package com.chainpay.core.payout.repository;

import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Optional<Payout> findByIdempotencyKey(String idempotencyKey);
    List<Payout> findByStatus(PayoutStatus status);
}
