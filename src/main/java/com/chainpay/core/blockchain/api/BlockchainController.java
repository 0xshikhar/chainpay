package com.chainpay.core.blockchain.api;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blockchain")
@RequiredArgsConstructor
public class BlockchainController {

    private final BlockchainTransactionRepository transactionRepository;

    @GetMapping("/transactions/{hash}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<BlockchainTransaction> getTransactionByHash(@PathVariable("hash") String hash) {
        BlockchainTransaction tx = transactionRepository.findByTxHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Blockchain transaction not found for hash: " + hash));
        return ResponseEntity.ok(tx);
    }
}
