package com.chainpay.core.ledger.repository;

import com.chainpay.core.ledger.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findBySymbolAndChainId(String symbol, Long chainId);
    Optional<Asset> findByContractAddressAndChainId(String contractAddress, Long chainId);
}
