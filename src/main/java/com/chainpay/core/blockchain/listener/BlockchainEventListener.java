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

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainEventListener {

    private final BlockchainTransactionRepository blockchainTxRepository;
    private final PayoutRepository payoutRepository;
    private final PayoutStateMachine stateMachine;
    private final OutboxPublisherService outboxPublisher;
    private final org.web3j.protocol.Web3j web3j;

    @Value("${chainpay.web3.confirmations-required:12}")
    private int confirmationsRequired;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void trackConfirmations() {
        List<BlockchainTransaction> pendingTxs = blockchainTxRepository.findByStatus("SUBMITTED");
        for (BlockchainTransaction tx : pendingTxs) {
            try {
                org.web3j.protocol.core.methods.response.EthGetTransactionReceipt receiptResp =
                        web3j.ethGetTransactionReceipt(tx.getTxHash()).send();

                if (receiptResp.getTransactionReceipt().isPresent()) {
                    org.web3j.protocol.core.methods.response.TransactionReceipt receipt =
                            receiptResp.getTransactionReceipt().get();

                    // Verify EVM execution status (0x1 = Success, 0x0 = Revert)
                    if (!receipt.isStatusOK()) {
                        log.error("EVM Transaction REVERTED on-chain! Tx Hash: {}", tx.getTxHash());
                        tx.setStatus("REVERTED");
                        Payout payout = tx.getPayout();
                        if (payout != null) {
                            stateMachine.transition(payout, PayoutStatus.FAILED_PERMANENTLY,
                                    "EVM Reverted on-chain", "EVENT_LISTENER");
                            payoutRepository.save(payout);
                        }
                        blockchainTxRepository.save(tx);
                        continue;
                    }

                    BigInteger currentBlock = web3j.ethBlockNumber().send().getBlockNumber();
                    BigInteger minedBlock = receipt.getBlockNumber();
                    int realConfirmations = currentBlock.subtract(minedBlock).intValue() + 1;

                    tx.setBlockNumber(minedBlock.longValue());
                    tx.setGasUsed(receipt.getGasUsed());
                    tx.setConfirmations(realConfirmations);

                    Payout payout = tx.getPayout();
                    if (payout != null) {
                        if (realConfirmations >= 1 && payout.getStatus() == PayoutStatus.SUBMITTED) {
                            stateMachine.transition(payout, PayoutStatus.CONFIRMING,
                                    "Mined in block #" + minedBlock, "EVENT_LISTENER");
                            payoutRepository.save(payout);
                        }

                        if (realConfirmations >= confirmationsRequired && payout.getStatus() == PayoutStatus.CONFIRMING) {
                            tx.setStatus("CONFIRMED");
                            stateMachine.transition(payout, PayoutStatus.COMPLETED,
                                    "Accrued " + realConfirmations + " real on-chain confirmations", "EVENT_LISTENER");
                            payoutRepository.save(payout);

                            outboxPublisher.publishEvent("PAYOUT", payout.getId().toString(), "PAYOUT_COMPLETED",
                                    String.format("{\"payoutId\":\"%s\",\"status\":\"COMPLETED\",\"txHash\":\"%s\",\"blockNumber\":%d}",
                                            payout.getId(), tx.getTxHash(), minedBlock.longValue()));

                            log.info("Payout ID {} completed with {} real EVM block confirmations! Mined in block #{}",
                                    payout.getId(), realConfirmations, minedBlock);
                        }
                    }
                    blockchainTxRepository.save(tx);
                }
            } catch (Exception ex) {
                log.warn("RPC query for transaction receipt {} failed: {}", tx.getTxHash(), ex.getMessage());
            }
        }
    }
}
