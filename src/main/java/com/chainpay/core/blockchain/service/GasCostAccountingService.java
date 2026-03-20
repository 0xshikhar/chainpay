package com.chainpay.core.blockchain.service;

import com.chainpay.core.common.exception.ResourceNotFoundException;
import com.chainpay.core.ledger.api.dto.JournalEntryRequest;
import com.chainpay.core.ledger.api.dto.PostTransactionRequest;
import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.AccountType;
import com.chainpay.core.ledger.domain.EntryType;
import com.chainpay.core.ledger.domain.LedgerTransaction;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.service.LedgerService;
import com.chainpay.core.payout.domain.Payout;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GasCostAccountingService {

    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;

    public BigInteger calculateGasCostBaseUnits(BigInteger gasPrice, BigInteger gasLimit) {
        if (gasPrice == null || gasLimit == null) {
            return BigInteger.valueOf(1300000L); // Default fallback 1.3 USDC (6 decimals)
        }
        return gasPrice.multiply(gasLimit).divide(BigInteger.valueOf(1000000000L)); // Normalized gas scale
    }

    @Transactional
    public LedgerTransaction settleGasFeeForPayout(Payout payout, BigInteger gasPrice, BigInteger gasLimit) {
        BigInteger feeAmount = calculateGasCostBaseUnits(gasPrice, gasLimit);

        Account customerAccount = payout.getAccount();
        UUID assetId = payout.getAsset().getId();

        // Find or reference system fee revenue account
        Account feeRevenueAccount = accountRepository.findByAccountTypeAndAssetId(AccountType.SYSTEM_FEE_REVENUE, assetId)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_FEE_REVENUE account not found for asset ID " + assetId));

        JournalEntryRequest debitCustomerFee = JournalEntryRequest.builder()
                .accountId(customerAccount.getId())
                .assetId(assetId)
                .entryType(EntryType.DEBIT)
                .amount(feeAmount)
                .build();

        JournalEntryRequest creditFeeRevenue = JournalEntryRequest.builder()
                .accountId(feeRevenueAccount.getId())
                .assetId(assetId)
                .entryType(EntryType.CREDIT)
                .amount(feeAmount)
                .build();

        PostTransactionRequest feeTx = PostTransactionRequest.builder()
                .referenceId("GAS-FEE-" + payout.getId() + "-" + UUID.randomUUID())
                .description("Gas cost settlement for payout ID " + payout.getId())
                .entries(List.of(debitCustomerFee, creditFeeRevenue))
                .build();

        log.info("Posting gas fee settlement of {} base units for payout ID {}", feeAmount, payout.getId());
        return ledgerService.postTransaction(feeTx);
    }
}
