package com.sidru.sidru_api.blockchain;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sidru.sidru_api.blockchain.application.internal.scheduling.ReserveMonitorService;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * US-MN-08, escenario "Alerta de reserva": si collateralizationBps() cae por debajo del
 * umbral configurado, el job de monitoreo registra una alerta WARN con el valor actual. Un
 * fallo del eth_call tampoco debe reventar el job (mismo criterio que la reconciliación).
 */
class ReserveMonitorServiceTest {

    private ChapaTuCriptoContract contract;
    private BlockchainProperties properties;
    private ReserveMonitorService service;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        contract = mock(ChapaTuCriptoContract.class);
        properties = new BlockchainProperties();
        properties.setReserveAlertBps(11000);
        service = new ReserveMonitorService(contract, properties);

        logger = (Logger) LoggerFactory.getLogger(ReserveMonitorService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    void collateralizationPorDebajoDelUmbralRegistraUnWarn() throws Exception {
        when(contract.collateralizationBps()).thenReturn(BigInteger.valueOf(10500)); // < 11000

        service.checkReserve();

        boolean warned = logAppender.list.stream().anyMatch(e ->
                e.getLevel() == Level.WARN && e.getFormattedMessage().contains("10500"));
        assertTrue(warned, "debe registrar un WARN con el valor actual de collateralizationBps");
    }

    @Test
    void collateralizationPorEncimaDelUmbralNoAlerta() throws Exception {
        when(contract.collateralizationBps()).thenReturn(BigInteger.valueOf(12000)); // >= 11000

        service.checkReserve();

        boolean warned = logAppender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN);
        assertFalse(warned, "por encima del umbral no debe alertar");
    }

    @Test
    void unFalloDelEthCallNoRevientaElJob() throws Exception {
        when(contract.collateralizationBps()).thenThrow(new java.io.IOException("rpc down"));

        service.checkReserve(); // no debe lanzar
    }
}
