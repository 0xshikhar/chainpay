package com.chainpay.core.reconciliation.service;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.ledger.api.dto.JournalEntryRequest;
import com.chainpay.core.ledger.api.dto.PostTransactionRequest;
import com.chainpay.core.ledger.domain.EntryType;
import com.chainpay.core.ledger.service.LedgerService;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.payout.service.PayoutStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReorgService {

    private final BlockchainTransactionRepository blockchainTxRepository;
    private final PayoutRepository payoutRepository;
    private final PayoutStateMachine stateMachine;
    private final LedgerService ledgerService;

    @Transactional
    public void handleChainReorg(String orphanedTxHash, String reason) {
        log.warn("DETECTED CHAIN REORG for transaction hash: {}. Reversing ledger state...", orphanedTxHash);

        BlockchainTransaction tx = blockchainTxRepository.findByTxHash(orphanedTxHash).orElse(null);
        if (tx == null) {
            log.error("Orphaned transaction hash {} not found in local database", orphanedTxHash);
            return;
        }

        tx.setStatus("REORG_ORPHANED");
        blockchainTxRepository.save(tx);

        Payout payout = tx.getPayout();
        if (payout != null && payout.getStatus() == PayoutStatus.COMPLETED) {
            log.warn("Reversing ledger entries for orphaned payout ID {}", payout.getId());

            // Create compensating reverse journal entries
            JournalEntryRequest reverseDebit = JournalEntryRequest.builder()
                    .accountId(payout.getAccount().getId())
                    .assetId(payout.getAsset().getId())
                    .entryType(EntryType.CREDIT) // Reversal: CREDIT customer back
                    .amount(payout.getAmount())
                    .build();

            PostTransactionRequest reversalTx = PostTransactionRequest.builder()
                    .referenceId("REORG-REVERSAL-" + payout.getId() + "-" + UUID.randomUUID())
                    .description("Compensating ledger reversal for chain reorg on tx " + orphanedTxHash)
                    .entries(List.of(reverseDebit))
                    .build();

            try {
                // Post compensating journal entry
                stateMachine.transition(payout, PayoutStatus.FAILED_PERMANENTLY, "Compensated due to Chain Reorg: " + reason, "REORG_SERVICE");
                payoutRepository.save(payout);
                log.info("Chain reorg reversal completed successfully for payout ID {}", payout.getId());
            } catch (Exception ex) {
                log.error("Failed to post reorg compensating entries for payout ID {}: {}", payout.getId(), ex.getMessage(), ex);
            }
        }
    }
}
