package com.sidru.sidru_api.blockchain.application.internal.scheduling;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * Vigila el respaldo de la reserva cada 10 minutos (design.md §9/§10): WARN si
 * collateralizationBps cae por debajo de sidru.blockchain.reserve-alert-bps.
 */
@Service
@ConditionalOnProperty(value = "sidru.blockchain.enabled", havingValue = "true")
public class ReserveMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReserveMonitorService.class);
    private static final long TEN_MINUTES_MS = 10 * 60 * 1000L;

    private final ChapaTuCriptoContract contract;
    private final BlockchainProperties properties;

    public ReserveMonitorService(ChapaTuCriptoContract contract, BlockchainProperties properties) {
        this.contract = contract;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = TEN_MINUTES_MS)
    public void checkReserve() {
        try {
            BigInteger bps = contract.collateralizationBps();
            if (bps.compareTo(BigInteger.valueOf(properties.getReserveAlertBps())) < 0) {
                LOGGER.warn("Reserva por debajo del umbral de alerta: collateralizationBps={} (umbral={})",
                        bps, properties.getReserveAlertBps());
            }
        } catch (Exception ex) {
            LOGGER.warn("No se pudo consultar collateralizationBps(): {}", ex.getMessage());
        }
    }
}
