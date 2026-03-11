package com.chainpay.core.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String status; // PASSED, DISCREPANCY_FOUND

    @Column(name = "total_checked", nullable = false)
    @Builder.Default
    private int totalChecked = 0;

    @Column(name = "discrepancy_count", nullable = false)
    @Builder.Default
    private int discrepancyCount = 0;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public void addDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        discrepancies.add(discrepancy);
        discrepancy.setReport(this);
        this.discrepancyCount = discrepancies.size();
        this.status = "DISCREPANCY_FOUND";
    }
}
