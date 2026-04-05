package com.chainpay.core.reconciliation.job;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.domain.BlockchainTxStatus;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.ledger.api.dto.BalanceResponse;
import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.AccountType;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.service.LedgerService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationJob {

    private final PayoutRepository payoutRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final ReconciliationReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final Web3j web3j;
    private final String hotWalletAddress;

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public ReconciliationReport runReconciliation() {
        log.info("Starting scheduled 3-way Financial & Live Blockchain Reconciliation Job...");

        // Save initial report record so partial execution or mid-run failure doesn't lose the run context (P1 fix)
        ReconciliationReport report = ReconciliationReport.builder()
                .status("RUNNING")
                .totalChecked(0)
                .discrepancyCount(0)
                .build();
        report = reportRepository.save(report);

        try {
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
                } else if (tx.getStatus() != BlockchainTxStatus.CONFIRMED) {
                    ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                            .discrepancyType("STATUS_MISMATCH")
                            .description("Payout ID " + payout.getId() + " is COMPLETED but on-chain status is " + tx.getStatus())
                            .severity("MEDIUM")
                            .build();
                    report.addDiscrepancy(discrepancy);
                }
            }

            // 2. Real On-Chain RPC Hot Wallet Balance & Ledger Audit (uses injected hotWalletAddress - P1 fix)
            try {
                EthGetBalance ethBalanceResp = web3j.ethGetBalance(hotWalletAddress, DefaultBlockParameterName.LATEST).send();

                if (!ethBalanceResp.hasError()) {
                    BigInteger onChainBalanceWei = ethBalanceResp.getBalance();
                    String ethStr = new BigDecimal(onChainBalanceWei)
                            .divide(new BigDecimal("1000000000000000000")).toPlainString();

                    log.info("[RECONCILIATION] Hot Wallet [{}] Real EVM Balance: {} Wei ({} ETH)",
                            hotWalletAddress, onChainBalanceWei, ethStr);

                    List<Account> hotWalletAccounts = accountRepository.findAll().stream()
                            .filter(a -> a.getAccountType() == AccountType.SYSTEM_HOT_WALLET)
                            .toList();

                    for (Account acc : hotWalletAccounts) {
                        BalanceResponse ledgerBal = ledgerService.getAccountBalance(acc.getId());
                        BigInteger ledgerBalanceBase = ledgerBal.getBalanceBaseUnits();

                        if (ledgerBalanceBase != null && ledgerBalanceBase.compareTo(onChainBalanceWei) > 0) {
                            ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                                    .discrepancyType("HOT_WALLET_BALANCE_MISMATCH")
                                    .description(String.format("System Hot Wallet [%s] ledger balance (%s) exceeds live EVM balance (%s Wei)",
                                            acc.getAccountNumber(), ledgerBalanceBase, onChainBalanceWei))
                                    .severity("HIGH")
                                    .build();
                            report.addDiscrepancy(discrepancy);
                            log.warn("[RECONCILIATION] DISCREPANCY: Ledger balance {} > On-Chain balance {}",
                                    ledgerBalanceBase, onChainBalanceWei);
                        }
                    }
                } else {
                    log.warn("[RECONCILIATION] Failed to fetch on-chain balance for hot wallet {}: {}",
                            hotWalletAddress, ethBalanceResp.getError().getMessage());
                }
            } catch (Exception ex) {
                log.error("[RECONCILIATION] Web3 RPC balance query failed: {}", ex.getMessage());
            }

            report.setStatus(report.getDiscrepancyCount() > 0 ? "DISCREPANCY_FOUND" : "PASSED");
        } catch (Exception ex) {
            log.error("[RECONCILIATION] Unexpected failure during reconciliation run: {}", ex.getMessage(), ex);
            report.setStatus("FAILED");
        }

        ReconciliationReport savedReport = reportRepository.save(report);
        log.info("3-Way Financial & Web3 Reconciliation complete. Status: {}, Total Checked: {}, Discrepancies: {}",
                savedReport.getStatus(), savedReport.getTotalChecked(), savedReport.getDiscrepancyCount());

        return savedReport;
    }
}
