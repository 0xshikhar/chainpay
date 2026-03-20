package com.chainpay.core.payout.service;

import com.chainpay.core.payout.api.dto.BatchPayoutRequest;
import com.chainpay.core.payout.api.dto.BatchPayoutResponse;
import com.chainpay.core.payout.api.dto.CreatePayoutRequest;
import com.chainpay.core.payout.api.dto.PayoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchPayoutService {

    private final PayoutService payoutService;

    @Transactional
    public BatchPayoutResponse processBatchPayout(BatchPayoutRequest request) {
        log.info("Processing batch payout request with batch key '{}' ({} payouts)",
                request.getBatchIdempotencyKey(), request.getPayouts().size());

        List<PayoutResponse> responses = new ArrayList<>();

        int index = 0;
        for (CreatePayoutRequest item : request.getPayouts()) {
            String itemKey = request.getBatchIdempotencyKey() + "-item-" + index++;
            PayoutResponse payoutResp = payoutService.createPayout(itemKey, item);
            responses.add(payoutResp);
        }

        return BatchPayoutResponse.builder()
                .batchIdempotencyKey(request.getBatchIdempotencyKey())
                .totalSubmitted(responses.size())
                .payouts(responses)
                .build();
    }
}
