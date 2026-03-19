package com.chainpay.core.blockchain.repository;

import com.chainpay.core.blockchain.domain.HotWalletNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotWalletNodeRepository extends JpaRepository<HotWalletNode, UUID> {
    List<HotWalletNode> findByStatusOrderByLastUsedAtAsc(String status);
    Optional<HotWalletNode> findByAddress(String address);
}
