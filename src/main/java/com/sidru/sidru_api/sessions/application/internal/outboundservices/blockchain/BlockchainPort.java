package com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain;

import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;

import java.util.Optional;

/**
 * Outbound port for recording a confirmed session on the blockchain.
 * Concrete implementation lives in the blockchain context (Web3jBlockchainService).
 */
public interface BlockchainPort {
    Optional<String> recordSession(RecyclingSession session);
}
