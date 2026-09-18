package com.sidru.sidru_api.blockchain.application.internal.commandservices;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Notificaciones push de retiro (api-contract.md), centralizadas para que
 * {@link WithdrawalCommandService} y {@code WithdrawalReconciliationService} avisen igual
 * sin importar qué servicio cerró el retiro.
 */
@Component
public class WithdrawalNotifier {

    private static final BigDecimal USDC_UNIT = BigDecimal.TEN.pow(6);

    private final NotificationContextFacade notificationContextFacade;

    public WithdrawalNotifier(NotificationContextFacade notificationContextFacade) {
        this.notificationContextFacade = notificationContextFacade;
    }

    public void notifyCompleted(WithdrawalRequest request) {
        String body;
        if (request.getMode() == WithdrawalMode.CTC) {
            body = "Recibiste " + request.getPoints() + " CTC en tu wallet";
        } else if (request.getReserveOut() != null) {
            body = "Recibiste " + formatUsdc(request.getReserveOut()) + " USDC en tu wallet";
        } else {
            // Confirmado por lectura on-chain (withdrawalProcessed), sin recibo que decodificar:
            // no hay monto que mostrar. Nunca "0.00" — sería un dato falso, no una estimación.
            body = "Recibiste USDC en tu wallet";
        }
        notificationContextFacade.notifyUser(request.getUserId(), "Retiro completado", body);
    }

    public void notifyFailed(WithdrawalRequest request) {
        notificationContextFacade.notifyUser(request.getUserId(), "Retiro no completado",
                "No pudimos completar tu retiro. Tus " + request.getPoints()
                        + " puntos volvieron a tu cuenta.");
    }

    private static String formatUsdc(String reserveOutRaw) {
        BigDecimal usdc = new BigDecimal(reserveOutRaw).divide(USDC_UNIT);
        return usdc.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
