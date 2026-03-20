package com.chainpay.core.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChainPayMetrics {

    private final Counter completedPayoutsCounter;
    private final Counter failedPermanentlyPayoutsCounter;
    private final Counter ledgerInvariantCheckCounter;

    public ChainPayMetrics(MeterRegistry registry) {
        this.completedPayoutsCounter = Counter.builder("chainpay.payouts.total")
                .tag("status", "COMPLETED")
                .description("Total number of payouts successfully completed on-chain")
                .register(registry);

        this.failedPermanentlyPayoutsCounter = Counter.builder("chainpay.payouts.total")
                .tag("status", "FAILED_PERMANENTLY")
                .description("Total number of payouts routed to terminal failure")
                .register(registry);

        this.ledgerInvariantCheckCounter = Counter.builder("chainpay.ledger.invariant.checks")
                .description("Total number of double-entry ledger zero-sum integrity checks performed")
                .register(registry);
    }

    public void recordPayoutCompleted() {
        completedPayoutsCounter.increment();
    }

    public void recordPayoutFailedPermanently() {
        failedPermanentlyPayoutsCounter.increment();
    }

    public void recordLedgerInvariantCheck() {
        ledgerInvariantCheckCounter.increment();
    }
}
