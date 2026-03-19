package com.chainpay.core.ops.repository;

import com.chainpay.core.ops.domain.OperationalIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperationalIncidentRepository extends JpaRepository<OperationalIncident, UUID> {
    List<OperationalIncident> findByStatus(String status);
    List<OperationalIncident> findByCategory(String category);
}
