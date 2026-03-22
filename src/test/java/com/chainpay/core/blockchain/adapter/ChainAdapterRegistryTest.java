package com.chainpay.core.blockchain.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainAdapterRegistryTest {

    private ChainAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ChainAdapterRegistry();
    }

    @Test
    @DisplayName("Should resolve ChainAdapter for registered EVM Chain IDs")
    void testGetAdapter_ResolvesCorrectAdapter() {
        ChainAdapter anvilAdapter = registry.getAdapter(31337L);
        assertNotNull(anvilAdapter);
        assertEquals("Anvil Local Devnet", anvilAdapter.getChainName());
        assertEquals(1, anvilAdapter.getRequiredConfirmations());

        ChainAdapter sepoliaAdapter = registry.getAdapter(11155111L);
        assertNotNull(sepoliaAdapter);
        assertEquals("Ethereum Sepolia Testnet", sepoliaAdapter.getChainName());
        assertEquals(12, sepoliaAdapter.getRequiredConfirmations());
    }
}
