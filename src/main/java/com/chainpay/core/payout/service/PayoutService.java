package com.chainpay.core.payout.service;

import com.chainpay.core.common.exception.ResourceNotFoundException;
import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.Asset;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.repository.AssetRepository;
import com.chainpay.core.payout.api.dto.CreatePayoutRequest;
import com.chainpay.core.payout.api.dto.PayoutResponse;
import com.chainpay.core.payout.api.dto.PayoutStatusHistoryResponse;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final PayoutStateMachine stateMachine;

    @Transactional
    public PayoutResponse createPayout(String idempotencyKey, CreatePayoutRequest request) {
        // Idempotency check: Return existing payout if idempotency key already processed
        Optional<Payout> existingPayout = payoutRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayout.isPresent()) {
            log.info("Returning cached payout for idempotency key: {}", idempotencyKey);
            return mapToResponse(existingPayout.get());
        }

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));

        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with ID: " + request.getAssetId()));

        Payout payout = Payout.builder()
                .account(account)
                .asset(asset)
                .destinationAddress(request.getDestinationAddress())
                .amount(request.getAmount())
                .status(PayoutStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        payout.addStatusHistory(null, PayoutStatus.PENDING, "Payout created", "API_CLIENT");

        Payout savedPayout = payoutRepository.save(payout);
        log.info("Created payout ID {} in PENDING state", savedPayout.getId());

        return mapToResponse(savedPayout);
    }

    @Transactional(readOnly = true)
    public PayoutResponse getPayoutById(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found with ID: " + payoutId));
        return mapToResponse(payout);
    }

    @Transactional
    public PayoutResponse retryPayout(UUID payoutId, String actor) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found with ID: " + payoutId));

        if (payout.getRetryCount() >= payout.getMaxRetries()) {
            log.warn("Payout ID {} exceeded max retries ({}). Marking FAILED_PERMANENTLY", payoutId, payout.getMaxRetries());
            stateMachine.transition(payout, PayoutStatus.FAILED_PERMANENTLY, "Max retry limit reached (" + payout.getMaxRetries() + ")", actor);
        } else {
            payout.setRetryCount(payout.getRetryCount() + 1);
            stateMachine.transition(payout, PayoutStatus.RETRYING, "Manual retry attempt " + payout.getRetryCount(), actor);
        }

        return mapToResponse(payoutRepository.save(payout));
    }

    @Transactional
    public PayoutResponse markFailedPermanently(UUID payoutId, String reason, String actor) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found with ID: " + payoutId));

        payout.setErrorReason(reason);
        stateMachine.transition(payout, PayoutStatus.FAILED_PERMANENTLY, reason, actor);

        return mapToResponse(payoutRepository.save(payout));
    }

    public PayoutResponse mapToResponse(Payout payout) {
        List<PayoutStatusHistoryResponse> historyResponses = payout.getStatusHistory().stream()
                .map(h -> PayoutStatusHistoryResponse.builder()
                        .id(h.getId())
                        .fromStatus(h.getFromStatus())
                        .toStatus(h.getToStatus())
                        .reason(h.getReason())
                        .actor(h.getActor())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PayoutResponse.builder()
                .id(payout.getId())
                .accountId(payout.getAccount().getId())
                .accountNumber(payout.getAccount().getAccountNumber())
                .assetId(payout.getAsset().getId())
                .assetSymbol(payout.getAsset().getSymbol())
                .destinationAddress(payout.getDestinationAddress())
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .idempotencyKey(payout.getIdempotencyKey())
                .retryCount(payout.getRetryCount())
                .maxRetries(payout.getMaxRetries())
                .errorReason(payout.getErrorReason())
                .statusHistory(historyResponses)
                .createdAt(payout.getCreatedAt())
                .updatedAt(payout.getUpdatedAt())
                .build();
    }
}
