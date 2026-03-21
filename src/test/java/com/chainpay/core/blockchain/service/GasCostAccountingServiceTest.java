package com.chainpay.core.blockchain.service;

import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GasCostAccountingServiceTest {

    @Mock
    private LedgerService ledgerService;

    @Mock
    private AccountRepository accountRepository;

    private GasCostAccountingService gasCostAccountingService;

    @BeforeEach
    void setUp() {
        gasCostAccountingService = new GasCostAccountingService(ledgerService, accountRepository);
    }

    @Test
    @DisplayName("Calculate gas cost in base units should scale gasPrice * gasLimit accurately")
    void testCalculateGasCostBaseUnits_ScalesAccurately() {
        BigInteger gasPrice = new BigInteger("20000000000"); // 20 Gwei
        BigInteger gasLimit = new BigInteger("65000");      // 65,000 gas units

        BigInteger feeBaseUnits = gasCostAccountingService.calculateGasCostBaseUnits(gasPrice, gasLimit);

        // (20,000,000,000 * 65,000) / 1,000,000,000 = 1,300,000 base units (1.3 USDC)
        assertEquals(new BigInteger("1300000"), feeBaseUnits);
    }
}
