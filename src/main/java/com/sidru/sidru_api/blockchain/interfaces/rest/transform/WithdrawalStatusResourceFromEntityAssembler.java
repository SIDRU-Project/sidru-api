package com.sidru.sidru_api.blockchain.interfaces.rest.transform;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WithdrawalStatusResource;
import org.springframework.stereotype.Component;

/**
 * Arma el recurso de estado de retiro. Es un componente y no una clase estatica porque
 * la URL del explorador depende de la red configurada: en mainnet apunta a polygonscan.com
 * y en testnet a amoy.polygonscan.com.
 */
@Component
public class WithdrawalStatusResourceFromEntityAssembler {

    private final BlockchainProperties properties;

    public WithdrawalStatusResourceFromEntityAssembler(BlockchainProperties properties) {
        this.properties = properties;
    }

    public WithdrawalStatusResource toResourceFromEntity(WithdrawalRequest entity) {
        return new WithdrawalStatusResource(
                entity.getId(),
                entity.getToAddress(),
                entity.getAmountWei(),
                entity.getStatus().name(),
                entity.getTxHash(),
                properties.explorerTxUrl(entity.getTxHash()));
    }
}
