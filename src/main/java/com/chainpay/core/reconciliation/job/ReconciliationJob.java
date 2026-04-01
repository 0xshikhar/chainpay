package com.chainpay.core.reconciliation.job;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.reconciliation.domain.ReconciliationDiscrepancy;
import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import com.chainpay.core.reconciliation.repository.ReconciliationReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationJob {

    private final PayoutRepository payoutRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final ReconciliationReportRepository reportRepository;
    private final com.chainpay.core.ledger.repository.AccountRepository accountRepository;
    private final com.chainpay.core.ledger.service.LedgerService ledgerService;
    private final org.web3j.protocol.Web3j web3j;

    @org.springframework.beans.factory.annotation.Value("${chainpay.web3.hot-wallet-private-key}")
    private String privateKey;

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public ReconciliationReport runReconciliation() {
        log.info("Starting scheduled 3-way Financial & Live Blockchain Reconciliation Job...");

        ReconciliationReport report = ReconciliationReport.builder()
                .status("PASSED")
                .totalChecked(0)
                .discrepancyCount(0)
                .build();

        List<Payout> completedPayouts = payoutRepository.findByStatus(PayoutStatus.COMPLETED);
        report.setTotalChecked(completedPayouts.size());

        // 1. Transaction Reconciliation
        for (Payout payout : completedPayouts) {
            BlockchainTransaction tx = blockchainTransactionRepository.findByPayoutId(payout.getId()).orElse(null);

            if (tx == null) {
                ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                        .discrepancyType("MISSING_ON_CHAIN")
                        .description("Payout ID " + payout.getId() + " is marked COMPLETED in ledger but has no blockchain transaction record!")
                        .severity("HIGH")
                        .build();
                report.addDiscrepancy(discrepancy);
            } else if (!"CONFIRMED".equalsIgnoreCase(tx.getStatus())) {
                ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                        .discrepancyType("STATUS_MISMATCH")
                        .description("Payout ID " + payout.getId() + " is COMPLETED but on-chain status is " + tx.getStatus())
                        .severity("MEDIUM")
                        .build();
                report.addDiscrepancy(discrepancy);
            }
        }

        // 2. Real On-Chain RPC Hot Wallet Balance & Ledger Audit
        try {
            org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(privateKey);
            String hotWalletAddress = credentials.getAddress();

            org.web3j.protocol.core.methods.response.EthGetBalance ethBalanceResp =
                    web3j.ethGetBalance(hotWalletAddress, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send();

            if (!ethBalanceResp.hasError()) {
                java.math.BigInteger onChainBalanceWei = ethBalanceResp.getBalance();
                String ethStr = new java.math.BigDecimal(onChainBalanceWei)
                        .divide(new java.math.BigDecimal("1000000000000000000")).toPlainString();

                log.info("LIVE ON-CHAIN RECONCILIATION: Hot Wallet [{}] Real EVM Node Balance: {} Wei ({} ETH)",
                        hotWalletAddress, onChainBalanceWei, ethStr);

                // Fetch ledger Hot Wallet account balance if exists
                List<com.chainpay.core.ledger.domain.Account> hotWalletAccounts = accountRepository.findAll().stream()
                        .filter(a -> a.getAccountType() == com.chainpay.core.ledger.domain.AccountType.SYSTEM_HOT_WALLET)
                        .toList();

                for (com.chainpay.core.ledger.domain.Account acc : hotWalletAccounts) {
                    com.chainpay.core.ledger.api.dto.BalanceResponse ledgerBal = ledgerService.getAccountBalance(acc.getId());
                    java.math.BigInteger ledgerBalanceBase = ledgerBal.getBalanceBaseUnits();

                    // If ledger balance exceeds live EVM balance, flag potential insolvency/drift
                    if (ledgerBalanceBase != null && ledgerBalanceBase.compareTo(onChainBalanceWei) > 0) {
                        ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                                .discrepancyType("HOT_WALLET_BALANCE_MISMATCH")
                                .description(String.format("System Hot Wallet [%s] ledger balance (%s) exceeds live EVM node balance (%s Wei)",
                                        acc.getAccountNumber(), ledgerBalanceBase, onChainBalanceWei))
                                .severity("HIGH")
                                .build();
                        report.addDiscrepancy(discrepancy);
                        log.warn("HOT WALLET DISCREPANCY DETECTED: Ledger balance {} > On-Chain balance {}", ledgerBalanceBase, onChainBalanceWei);
                    }
                }
            } else {
                log.warn("Failed to fetch on-chain ETH balance for hot wallet {}: {}",
                        hotWalletAddress, ethBalanceResp.getError().getMessage());
            }
        } catch (Exception ex) {
            log.error("Live Web3 RPC balance reconciliation failed: {}", ex.getMessage(), ex);
        }

        if (report.getDiscrepancyCount() > 0) {
            report.setStatus("DISCREPANCY_FOUND");
        }

        ReconciliationReport savedReport = reportRepository.save(report);
        log.info("3-Way Financial & Web3 Reconciliation complete. Status: {}, Total Checked: {}, Discrepancies: {}",
                savedReport.getStatus(), savedReport.getTotalChecked(), savedReport.getDiscrepancyCount());

        return savedReport;
    }
}
