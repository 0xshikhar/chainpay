package com.chainpay.core.ops.service;

import com.chainpay.core.ops.domain.OperationalIncident;
import com.chainpay.core.ops.repository.OperationalIncidentRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final PayoutRepository payoutRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OperationalIncidentRepository incidentRepository;
    private final Web3j web3j;

    @Value("${chainpay.web3.hot-wallet-private-key:0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80}")
    private String privateKey;

    private static final BigInteger MIN_HOT_WALLET_GAS_THRESHOLD = new BigInteger("50000000000000000"); // 0.05 ETH

    @Scheduled(fixedDelay = 60000) // Every 1 minute
    @Transactional
    public void scanSystemAnomalies() {
        log.info("Running Autonomous Anomaly Detection scan...");

        // 1. Check for spike in FAILED_PERMANENTLY payouts
        List<Payout> failedPermanently = payoutRepository.findByStatus(PayoutStatus.FAILED_PERMANENTLY);
        if (failedPermanently.size() >= 3) {
            log.warn("ANOMALY DETECTED: High count of FAILED_PERMANENTLY payouts ({})", failedPermanently.size());
            boolean existingIncident = incidentRepository.findByCategory("HIGH_FAILURE_RATE")
                    .stream().anyMatch(i -> "OPEN".equals(i.getStatus()));

            if (!existingIncident) {
                OperationalIncident incident = OperationalIncident.builder()
                        .title("High Payout Permanent Failure Spike")
                        .category("HIGH_FAILURE_RATE")
                        .severity("HIGH")
                        .description("System detected " + failedPermanently.size() + " payouts in FAILED_PERMANENTLY state.")
                        .status("OPEN")
                        .build();

                incidentRepository.save(incident);
            }
        }

        // 2. Check for outbox backlog
        long pendingOutboxCount = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING").size();
        if (pendingOutboxCount >= 50) {
            log.warn("ANOMALY DETECTED: Transactional Outbox backlog exceeds 50 events!");
            OperationalIncident incident = OperationalIncident.builder()
                    .title("Transactional Outbox Delivery Backlog")
                    .category("OUTBOX_BACKLOG")
                    .severity("MEDIUM")
                    .description("Pending outbox events saturated max batch size (" + pendingOutboxCount + ").")
                    .status("OPEN")
                    .build();

            incidentRepository.save(incident);
        }

        // 3. Proactive Hot Wallet Native Gas Balance Threshold Scan
        try {
            String hotWalletAddress = Credentials.create(privateKey).getAddress();
            BigInteger balance = web3j.ethGetBalance(hotWalletAddress, DefaultBlockParameterName.LATEST)
                    .send().getBalance();

            if (balance.compareTo(MIN_HOT_WALLET_GAS_THRESHOLD) < 0) {
                log.warn("PROACTIVE ANOMALY DETECTED: Hot Wallet ({}) gas balance below threshold: {} wei",
                        hotWalletAddress, balance);

                boolean existingIncident = incidentRepository.findByCategory("LOW_GAS_RESERVE")
                        .stream().anyMatch(i -> "OPEN".equals(i.getStatus()));

                if (!existingIncident) {
                    OperationalIncident incident = OperationalIncident.builder()
                            .title("Critical Hot Wallet Gas Depletion Warning")
                            .category("LOW_GAS_RESERVE")
                            .severity("CRITICAL")
                            .description("Hot Wallet (" + hotWalletAddress + ") balance (" + balance + " wei) is below 0.05 ETH threshold.")
                            .status("OPEN")
                            .build();

                    incidentRepository.save(incident);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to execute proactive hot wallet gas scan: {}", ex.getMessage());
        }
    }
}

