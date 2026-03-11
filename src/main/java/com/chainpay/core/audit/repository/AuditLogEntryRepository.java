package com.chainpay.core.audit.repository;

import com.chainpay.core.audit.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {
}
