package com.chainpay.core.blockchain.listener;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.payout.service.PayoutStateMachine;
import com.chainpay.core.webhook.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainEventListener {

    private final BlockchainTransactionRepository blockchainTxRepository;
    private final PayoutRepository payoutRepository;
    private final PayoutStateMachine stateMachine;
    private final OutboxPublisherService outboxPublisher;

    @Value("${chainpay.web3.confirmations-required:12}")
    private int confirmationsRequired;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void trackConfirmations() {
        List<BlockchainTransaction> pendingTxs = blockchainTxRepository.findByStatus("SUBMITTED");
        for (BlockchainTransaction tx : pendingTxs) {
            int currentConfirmations = tx.getConfirmations() + 1;
            tx.setConfirmations(currentConfirmations);

            Payout payout = tx.getPayout();
            if (payout != null) {
                if (currentConfirmations == 1 && payout.getStatus() == PayoutStatus.SUBMITTED) {
                    stateMachine.transition(payout, PayoutStatus.CONFIRMING, "Transaction mined in block", "EVENT_LISTENER");
                    payoutRepository.save(payout);
                }

                if (currentConfirmations >= confirmationsRequired && payout.getStatus() == PayoutStatus.CONFIRMING) {
                    tx.setStatus("CONFIRMED");
                    stateMachine.transition(payout, PayoutStatus.COMPLETED, "Accrued " + currentConfirmations + " confirmations", "EVENT_LISTENER");
                    payoutRepository.save(payout);

                    outboxPublisher.publishEvent("PAYOUT", payout.getId().toString(), "PAYOUT_COMPLETED",
                            String.format("{\"payoutId\":\"%s\",\"status\":\"COMPLETED\",\"txHash\":\"%s\"}", payout.getId(), tx.getTxHash()));

                    log.info("Payout ID {} completed with {} confirmations!", payout.getId(), currentConfirmations);
                }
            }
            blockchainTxRepository.save(tx);
        }
    }
}
