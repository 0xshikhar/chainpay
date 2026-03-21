package com.chainpay.core.blockchain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GasManagementServiceTest {

    @Mock
    private BlockchainTransactionRepository transactionRepository;

    private GasManagementService gasManagementService;

    @BeforeEach
    void setUp() {
        gasManagementService = new GasManagementService(transactionRepository);
    }

    @Test
    @DisplayName("Calculate bumped gas price should elevate current gas price by 15%")
    void testCalculateBumpedGasPrice_IncreasesBy15Percent() {
        BigInteger currentGas = new BigInteger("20000000000"); // 20 Gwei
        BigInteger bumpedGas = gasManagementService.calculateBumpedGasPrice(currentGas);

        // 20 Gwei * 1.15 = 23 Gwei (23,000,000,000)
        assertEquals(new BigInteger("23000000000"), bumpedGas);
    }
}
