package com.chainpay.core.reconciliation.repository;

import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {
}
