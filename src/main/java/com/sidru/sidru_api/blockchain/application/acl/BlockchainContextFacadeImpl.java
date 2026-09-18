package com.sidru.sidru_api.blockchain.application.acl;

import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.interfaces.acl.BlockchainContextFacade;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BlockchainContextFacadeImpl implements BlockchainContextFacade {

    private final WithdrawalRequestRepository withdrawalRepository;

    public BlockchainContextFacadeImpl(WithdrawalRequestRepository withdrawalRepository) {
        this.withdrawalRepository = withdrawalRepository;
    }

    @Override
    public long sumCompletedWithdrawalCtc(LocalDateTime from, LocalDateTime to) {
        return withdrawalRepository.sumPointsByStatusAndCreatedAtBetween(
                WithdrawalStatus.COMPLETADO, from, to);
    }
}
