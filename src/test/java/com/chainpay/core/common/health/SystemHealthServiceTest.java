package com.chainpay.core.common.health;

import com.chainpay.core.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemHealthServiceTest {

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private Web3j web3j;

    @InjectMocks
    private SystemHealthService healthService;

    @Test
    @DisplayName("Check system health should return UP status and transaction count")
    void testCheckSystemHealth_ReturnsUp() {
        when(transactionRepository.count()).thenReturn(15L);

        SystemHealthService.SystemHealthStatus status = healthService.checkSystemHealth();

        assertNotNull(status);
        assertEquals("UP", status.getStatus());
        assertEquals("ZERO_SUM_INVARIANT_VALIDATED", status.getDoubleEntryLedgerStatus());
        assertEquals(15L, status.getTotalLedgerTransactions());
    }
}
