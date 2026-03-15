package com.chainpay.core.payout.service;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrashRecoveryService {

    private final PayoutRepository payoutRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final PayoutStateMachine stateMachine;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInFlightPayouts() {
        log.info("Running Crash Recovery Audit for stuck in-flight payouts...");

        List<Payout> processingPayouts = payoutRepository.findByStatus(PayoutStatus.PROCESSING);

        for (Payout payout : processingPayouts) {
            Optional<BlockchainTransaction> txOpt = blockchainTransactionRepository.findByPayoutId(payout.getId());

            if (txOpt.isPresent()) {
                BlockchainTransaction tx = txOpt.get();
                log.warn("Recovery: Payout ID {} died in PROCESSING but tx {} was recorded. Recovering to SUBMITTED", payout.getId(), tx.getTxHash());
                stateMachine.transition(payout, PayoutStatus.SUBMITTED, "Recovered on startup: tx found (" + tx.getTxHash() + ")", "CRASH_RECOVERY");
            } else {
                log.warn("Recovery: Payout ID {} died in PROCESSING before tx broadcast. Resetting to PENDING for re-attempt", payout.getId());
                stateMachine.transition(payout, PayoutStatus.PENDING, "Recovered on startup: reset to PENDING", "CRASH_RECOVERY");
            }

            payoutRepository.save(payout);
        }

        log.info("Crash Recovery Audit completed. Processed {} stuck payouts.", processingPayouts.size());
    }
}
