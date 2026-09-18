package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * explorerTxUrl() nunca debe construir una URL con un txHash sintetico como "recorded:<id>"
 * (el marcador que deja completeFromChain cuando el retiro se confirmo por lectura on-chain
 * y no por recibo de transaccion): solo un hash real ("0x...") produce una URL.
 */
class BlockchainPropertiesTest {

    private BlockchainProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BlockchainProperties();
        properties.setExplorerBaseUrl("https://polygonscan.com");
    }

    @Test
    void unTxHashRecordedNoProduceUrl() {
        assertNull(properties.explorerTxUrl("recorded:123"));
    }

    @Test
    void unTxHashRealProduceLaUrlDelExplorador() {
        assertEquals(
                "https://polygonscan.com/tx/0xabc123",
                properties.explorerTxUrl("0xabc123"));
    }

    @Test
    void unTxHashNuloNoProduceUrl() {
        assertNull(properties.explorerTxUrl(null));
    }
}
