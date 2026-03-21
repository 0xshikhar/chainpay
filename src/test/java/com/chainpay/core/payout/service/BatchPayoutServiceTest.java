package com.chainpay.core.payout.service;

import com.chainpay.core.payout.api.dto.BatchPayoutRequest;
import com.chainpay.core.payout.api.dto.BatchPayoutResponse;
import com.chainpay.core.payout.api.dto.CreatePayoutRequest;
import com.chainpay.core.payout.api.dto.PayoutResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchPayoutServiceTest {

    @Mock
    private PayoutService payoutService;

    @InjectMocks
    private BatchPayoutService batchPayoutService;

    private CreatePayoutRequest payoutItem;

    @BeforeEach
    void setUp() {
        payoutItem = CreatePayoutRequest.builder()
                .accountId(UUID.randomUUID())
                .assetId(UUID.randomUUID())
                .destinationAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                .amount(BigInteger.valueOf(10000000))
                .build();
    }

    @Test
    @DisplayName("Process batch payout should submit each payout with generated sub-key")
    void testProcessBatchPayout_SubmitsAllItems() {
        BatchPayoutRequest request = BatchPayoutRequest.builder()
                .batchIdempotencyKey("batch-key-100")
                .payouts(List.of(payoutItem, payoutItem))
                .build();

        PayoutResponse mockResponse = PayoutResponse.builder().id(UUID.randomUUID()).build();
        when(payoutService.createPayout(any(String.class), any(CreatePayoutRequest.class))).thenReturn(mockResponse);

        BatchPayoutResponse response = batchPayoutService.processBatchPayout(request);

        assertNotNull(response);
        assertEquals("batch-key-100", response.getBatchIdempotencyKey());
        assertEquals(2, response.getTotalSubmitted());
        verify(payoutService, times(2)).createPayout(any(String.class), any(CreatePayoutRequest.class));
    }
}
