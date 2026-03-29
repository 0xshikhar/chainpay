package com.chainpay.core.blockchain.domain;

import com.chainpay.core.payout.domain.Payout;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blockchain_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockchainTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    private Payout payout;

    @Column(name = "tx_hash", nullable = false, unique = true, length = 66)
    private String txHash;

    @Column(name = "from_address", nullable = false, length = 64)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 64)
    private String toAddress;

    @Column(nullable = false)
    private Long nonce;

    @Column(name = "gas_price", precision = 38, scale = 0)
    private BigInteger gasPrice;

    @Column(name = "gas_limit", precision = 38, scale = 0)
    private BigInteger gasLimit;

    @Column(name = "gas_used", precision = 38, scale = 0)
    private BigInteger gasUsed;

    @Column(name = "tx_cost_eth", length = 64)
    private String txCostEth;

    @Column(name = "on_chain_memo", length = 255)
    private String onChainMemo;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(nullable = false)
    @Builder.Default
    private Integer confirmations = 0;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
