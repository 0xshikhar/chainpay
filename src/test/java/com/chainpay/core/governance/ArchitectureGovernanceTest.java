package com.chainpay.core.governance;

import com.chainpay.core.ledger.domain.Asset;
import com.chainpay.core.ledger.domain.JournalEntry;
import com.chainpay.core.payout.domain.Payout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureGovernanceTest {

    @Test
    @DisplayName("Verify domain entity package naming conventions")
    void testDomainEntityPackageNaming() {
        assertEquals("com.chainpay.core.ledger.domain", Asset.class.getPackageName());
        assertEquals("com.chainpay.core.ledger.domain", JournalEntry.class.getPackageName());
        assertEquals("com.chainpay.core.payout.domain", Payout.class.getPackageName());
    }

    @Test
    @DisplayName("Verify domain entities are annotated with jakarta.persistence.Entity")
    void testDomainEntitiesAnnotated() {
        assertTrue(Asset.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertTrue(JournalEntry.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertTrue(Payout.class.isAnnotationPresent(jakarta.persistence.Entity.class));
    }
}
