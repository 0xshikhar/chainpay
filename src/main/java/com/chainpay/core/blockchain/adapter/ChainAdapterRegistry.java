package com.chainpay.core.blockchain.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChainAdapterRegistry {

    private final Map<Long, ChainAdapter> registry = new ConcurrentHashMap<>();

    public ChainAdapterRegistry() {
        // Register standard EVM chain adapters
        registerAdapter(new EvmChainAdapter(31337L, "Anvil Local Devnet", BigInteger.valueOf(20000000000L), 1));
        registerAdapter(new EvmChainAdapter(11155111L, "Ethereum Sepolia Testnet", BigInteger.valueOf(25000000000L), 12));
        registerAdapter(new EvmChainAdapter(137L, "Polygon Mainnet", BigInteger.valueOf(50000000000L), 64));
        registerAdapter(new EvmChainAdapter(42161L, "Arbitrum One", BigInteger.valueOf(100000000L), 12));
        registerAdapter(new EvmChainAdapter(8453L, "Base Mainnet", BigInteger.valueOf(100000000L), 12));
    }

    public void registerAdapter(ChainAdapter adapter) {
        log.info("Registered ChainAdapter for chain ID {} ({})", adapter.getChainId(), adapter.getChainName());
        registry.put(adapter.getChainId(), adapter);
    }

    public ChainAdapter getAdapter(Long chainId) {
        ChainAdapter adapter = registry.get(chainId);
        if (adapter == null) {
            log.warn("No adapter found for chain ID {}. Falling back to default Anvil adapter.", chainId);
            return registry.get(31337L);
        }
        return adapter;
    }
}
