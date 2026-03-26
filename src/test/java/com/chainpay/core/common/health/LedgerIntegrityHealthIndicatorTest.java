package com.chainpay.core.common.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LedgerIntegrityHealthIndicatorTest {

    @Test
    @DisplayName("Should instantiate LedgerIntegrityHealthIndicator bean")
    void testIndicatorInstantiation() {
        LedgerIntegrityHealthIndicator indicator = new LedgerIntegrityHealthIndicator();
        assertNotNull(indicator);
    }
}
