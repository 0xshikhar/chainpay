package com.chainpay.core.payout.domain;

import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.Asset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "destination_address", nullable = false, length = 64)
    private String destinationAddress;

    @Column(nullable = false, precision = 38, scale = 0)
    private BigInteger amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "error_reason", columnDefinition = "TEXT")
    private String errorReason;

    @OneToMany(mappedBy = "payout", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PayoutStatusHistory> statusHistory = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public void addStatusHistory(PayoutStatus fromStatus, PayoutStatus toStatus, String reason, String actor) {
        PayoutStatusHistory history = PayoutStatusHistory.builder()
                .payout(this)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .actor(actor != null ? actor : "SYSTEM")
                .build();
        this.statusHistory.add(history);
        this.status = toStatus;
        this.updatedAt = Instant.now();
    }
}
