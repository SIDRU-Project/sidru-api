package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalNotifier;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Textos exactos de las notificaciones push de retiro (api-contract.md, sección
 * "Notificación push"), incluida la corrección de la Tarea 3b: USDC sin reserveOut
 * decodificado no debe inventar un monto ("0.00").
 */
class WithdrawalNotifierTest {

    private static final Long USER_ID = 5L;
    private static final String ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    private NotificationContextFacade notificationContextFacade;
    private WithdrawalNotifier notifier;

    @BeforeEach
    void setUp() {
        notificationContextFacade = mock(NotificationContextFacade.class);
        notifier = new WithdrawalNotifier(notificationContextFacade);
    }

    private WithdrawalRequest request(WithdrawalMode mode, int points) {
        return new WithdrawalRequest(USER_ID, ADDR, points, "0", mode);
    }

    @Test
    void completadoEnCtcAvisaLosCtcRecibidos() {
        WithdrawalRequest request = request(WithdrawalMode.CTC, 800);

        notifier.notifyCompleted(request);

        verify(notificationContextFacade).notifyUser(eq(USER_ID), eq("Retiro completado"),
                eq("Recibiste 800 CTC en tu wallet"));
    }

    @Test
    void completadoEnUsdcConMontoAvisaElMontoEnDosDecimales() {
        WithdrawalRequest request = request(WithdrawalMode.USDC, 1000);
        request.setReserveOut("2777777"); // 2.777777 USDC, 6 decimales -> 2.78 redondeado

        notifier.notifyCompleted(request);

        verify(notificationContextFacade).notifyUser(eq(USER_ID), eq("Retiro completado"),
                eq("Recibiste 2.78 USDC en tu wallet"));
    }

    @Test
    void completadoEnUsdcSinReserveOutNoInventaUnMonto() {
        // Confirmado por lectura on-chain (withdrawalProcessed), sin recibo que decodificar.
        WithdrawalRequest request = request(WithdrawalMode.USDC, 1000);

        notifier.notifyCompleted(request);

        verify(notificationContextFacade).notifyUser(eq(USER_ID), eq("Retiro completado"),
                eq("Recibiste USDC en tu wallet"));
    }

    @Test
    void falladoAvisaQueLosPuntosVolvieron() {
        WithdrawalRequest request = request(WithdrawalMode.CTC, 800);

        notifier.notifyFailed(request);

        verify(notificationContextFacade).notifyUser(eq(USER_ID), eq("Retiro no completado"),
                eq("No pudimos completar tu retiro. Tus 800 puntos volvieron a tu cuenta."));
    }
}
