package com.chainpay.core.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_assets_chain_address", columnNames = {"chain_id", "contract_address"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "contract_address", nullable = false, length = 64)
    private String contractAddress;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(nullable = false)
    private Integer decimals;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
