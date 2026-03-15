package com.chainpay.core.blockchain.worker;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.ledger.domain.Asset;
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
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainWorker {

    private final PayoutRepository payoutRepository;
    private final BlockchainTransactionRepository blockchainTxRepository;
    private final PayoutStateMachine stateMachine;
    private final OutboxPublisherService outboxPublisher;
    private final Web3j web3j;

    @Value("${chainpay.web3.hot-wallet-private-key}")
    private String privateKey;

    private final AtomicLong nonceTracker = new AtomicLong(0);

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    @Transactional
    public void processProcessingPayouts() {
        List<Payout> pendingPayouts = payoutRepository.findByStatus(PayoutStatus.PENDING);
        for (Payout payout : pendingPayouts) {
            processSinglePayout(payout);
        }
    }

    @Transactional
    public void processSinglePayout(Payout payout) {
        try {
            log.info("Worker picking up PENDING payout ID {}", payout.getId());
            stateMachine.transition(payout, PayoutStatus.PROCESSING, "Picked up by BlockchainWorker", "BLOCKCHAIN_WORKER");

            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();
            long nonce = nonceTracker.getAndIncrement();

            // Mock or live Web3j tx submission
            String mockTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000";

            BlockchainTransaction tx = BlockchainTransaction.builder()
                    .payout(payout)
                    .txHash(mockTxHash)
                    .fromAddress(fromAddress)
                    .toAddress(payout.getDestinationAddress())
                    .nonce(nonce)
                    .gasPrice(BigInteger.valueOf(20000000000L)) // 20 Gwei
                    .gasLimit(BigInteger.valueOf(65000L))
                    .status("SUBMITTED")
                    .build();

            blockchainTxRepository.save(tx);

            stateMachine.transition(payout, PayoutStatus.SUBMITTED, "Broadcasted transaction: " + mockTxHash, "BLOCKCHAIN_WORKER");
            outboxPublisher.publishEvent("PAYOUT", payout.getId().toString(), "PAYOUT_SUBMITTED",
                    String.format("{\"payoutId\":\"%s\",\"txHash\":\"%s\"}", payout.getId(), mockTxHash));

            payoutRepository.save(payout);
        } catch (Exception ex) {
            log.error("Failed to process payout ID {}: {}", payout.getId(), ex.getMessage(), ex);
            payout.setErrorReason(ex.getMessage());
            stateMachine.transition(payout, PayoutStatus.FAILED, "RPC Exception: " + ex.getMessage(), "BLOCKCHAIN_WORKER");
            payoutRepository.save(payout);
        }
    }
}
