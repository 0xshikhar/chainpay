package com.chainpay.core.blockchain.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hot_wallet_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotWalletNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String address;

    @Column(name = "current_nonce", nullable = false)
    @Builder.Default
    private long currentNonce = 0;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "last_used_at", nullable = false)
    @Builder.Default
    private Instant lastUsedAt = Instant.now();

    public synchronized long getAndIncrementNonce() {
        long nonce = this.currentNonce;
        this.currentNonce++;
        this.lastUsedAt = Instant.now();
        return nonce;
    }
}
