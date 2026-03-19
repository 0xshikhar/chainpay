package com.chainpay.core.blockchain.service;

import com.chainpay.core.blockchain.domain.HotWalletNode;
import com.chainpay.core.blockchain.repository.HotWalletNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotWalletPoolService {

    private final HotWalletNodeRepository nodeRepository;

    @Transactional
    public HotWalletNode getNextAvailableNode() {
        List<HotWalletNode> activeNodes = nodeRepository.findByStatusOrderByLastUsedAtAsc("ACTIVE");
        if (activeNodes.isEmpty()) {
            log.info("No active hot wallet nodes in database. Initializing primary node...");
            HotWalletNode defaultNode = HotWalletNode.builder()
                    .address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
                    .currentNonce(0)
                    .status("ACTIVE")
                    .build();
            return nodeRepository.save(defaultNode);
        }

        HotWalletNode selectedNode = activeNodes.get(0);
        selectedNode.getAndIncrementNonce();
        log.info("Selected hot wallet node {} with nonce {}", selectedNode.getAddress(), selectedNode.getCurrentNonce());
        return nodeRepository.save(selectedNode);
    }
}
