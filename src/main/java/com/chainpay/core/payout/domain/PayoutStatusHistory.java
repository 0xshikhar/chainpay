package com.chainpay.core.payout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private PayoutStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private PayoutStatus toStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String actor = "SYSTEM";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
