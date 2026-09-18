package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn08 — US-MN-04, escenario "Dos peticiones concurrentes del mismo ciudadano". Dos pruebas,
 * cada una pincha una rama distinta del rechazo:
 *
 * <p>{@link #dosRetirosConcurrentesSoloUnoProsperaYElDebitoEsUnico()} cubre la carrera libre
 * (CyclicBarrier, ambas peticiones salen a la vez, sin control de timing sobre el mock): con
 * saldo exacto para una sola, la perdedora responde 409 (ya EN_PROCESO) o 422 (puntos ya
 * debitados) según si su guard corre antes o después de que la ganadora complete on-chain.
 * Ambos códigos son correctos — ninguno permite doble débito —, lo que valida es que el débito
 * ocurra exactamente una vez.</p>
 *
 * <p>{@link #laPerdedoraChocaConElGuardCuandoHaySaldoSuficienteParaAmbas()} pincha
 * específicamente la rama del guard: con saldo de sobra para las dos peticiones (2000 puntos,
 * 800 cada una), la única forma de que la perdedora choque con el guard en vez de pasar por
 * "puntos insuficientes" es que la ganadora siga EN_PROCESO cuando la perdedora consulta. Como
 * eso no se puede garantizar con un simple arranque simultáneo (el mock resuelve al instante y
 * la ganadora podría completar antes de que la perdedora llegue), el mock de
 * {@code mintWithdrawal} bloquea a la ganadora con un {@link CountDownLatch} justo después de
 * que su transacción de débito ya comprometió (recordar: {@code submit()} corre fuera de esa
 * transacción), y el test solo libera el latch después de leer la respuesta de la perdedora.</p>
 *
 * <p>Las dos validan la corrección A de la Tarea 3b (el lock del perfil se toma ANTES del
 * guard de EN_PROCESO; si el orden fuera al revés, ambas peticiones pasarían el guard y
 * debitarían).</p>
 */
@DisplayName("CpMn08 - US-MN-04: dos retiros concurrentes del mismo ciudadano")
class CpMn08RetiroConcurrenciaTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Test
    @DisplayName("Dos POST /wallet/withdraw simultaneos: solo uno prospera, el otro recibe 409, y el debito ocurre una sola vez")
    void dosRetirosConcurrentesSoloUnoProsperaYElDebitoEsUnico() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 800);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xconcurrencia");
        when(contract.mintWithdrawal(any(), any(), any())).thenReturn(receipt);

        var barrier = new CyclicBarrier(2);
        Callable<Integer> intento = () -> {
            barrier.await(10, TimeUnit.SECONDS); // ambas peticiones salen a la vez
            return mockMvc.perform(apiPost("/wallet/withdraw")
                            .header("Authorization", citizen.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                    .andReturn().getResponse().getStatus();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var futures = pool.invokeAll(List.of(intento, intento));
            int primero = futures.get(0).get(20, TimeUnit.SECONDS);
            int segundo = futures.get(1).get(20, TimeUnit.SECONDS);

            long exitos = List.of(primero, segundo).stream().filter(s -> s == 200 || s == 202).count();
            long rechazos = List.of(primero, segundo).stream().filter(s -> s == 409 || s == 422).count();
            assertEquals(1, exitos,
                    "exactamente una peticion debe prosperar (200/202), fueron: " + primero + " y " + segundo);
            assertEquals(1, rechazos,
                    "la otra debe rechazarse (409 ya en proceso, o 422 si ya se debitaron los puntos), fueron: "
                            + primero + " y " + segundo);
        } finally {
            pool.shutdownNow();
        }

        // El debito ocurrio una sola vez: el saldo queda en 0, no en -800 (doble debito) ni en
        // 800 (ningun debito). Este es exactamente el bug que corrige el orden lock -> guard.
        var profile = userProfileRepository.findByUserId(citizen.id()).orElseThrow();
        assertEquals(0, profile.getTotalPoints(), "los 800 puntos deben debitarse una sola vez");

        List<?> retiros = withdrawalRequestRepository.findAllByUserIdOrderByIdDesc(citizen.id());
        assertEquals(1, retiros.size(), "solo debe haberse creado un WithdrawalRequest, no dos");
    }

    @Test
    @DisplayName("Con saldo de sobra para ambas, la perdedora choca exactamente con el guard EN_PROCESO (409)")
    void laPerdedoraChocaConElGuardCuandoHaySaldoSuficienteParaAmbas() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 2000);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xganadora");

        // La ganadora se queda bloqueada DENTRO de mintWithdrawal (fuera de la transaccion de
        // debito, que ya comprometio para cuando submit() la llama) hasta que el test libere el
        // latch. Asi garantizamos que su WithdrawalRequest sigue EN_PROCESO cuando la perdedora
        // consulta el guard, en vez de dejarlo a la suerte del scheduler.
        CountDownLatch winnerInsideMint = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        when(contract.mintWithdrawal(any(), any(), any())).thenAnswer(invocation -> {
            winnerInsideMint.countDown();
            if (!releaseWinner.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("releaseWinner nunca se libero");
            }
            return receipt;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> ganadoraFuture = pool.submit(() ->
                    mockMvc.perform(apiPost("/wallet/withdraw")
                                    .header("Authorization", citizen.bearer())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                            .andReturn().getResponse().getStatus());

            assertTrue(winnerInsideMint.await(10, TimeUnit.SECONDS),
                    "la ganadora debio comprometer su debito y llegar a mintWithdrawal a tiempo");

            // La perdedora corre AHORA, con la ganadora ya EN_PROCESO (debito commiteado, lock
            // liberado) pero bloqueada antes de completar: debe chocar con el guard, no con el
            // saldo (hay de sobra: 2000 - 800 = 1200 >= 800).
            mockMvc.perform(apiPost("/wallet/withdraw")
                            .header("Authorization", citizen.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ERR_BC_007"));

            releaseWinner.countDown();
            int ganadora = ganadoraFuture.get(15, TimeUnit.SECONDS);
            assertTrue(ganadora == 200 || ganadora == 202, "la ganadora debe prosperar, fue: " + ganadora);
        } finally {
            pool.shutdownNow();
        }

        // El intento de la perdedora se revirtio por completo: solo se debito una vez.
        var profile = userProfileRepository.findByUserId(citizen.id()).orElseThrow();
        assertEquals(1200, profile.getTotalPoints(),
                "el debito de la perdedora debe revertirse; solo queda el de la ganadora");

        List<?> retiros = withdrawalRequestRepository.findAllByUserIdOrderByIdDesc(citizen.id());
        assertEquals(1, retiros.size(), "solo debe haberse creado un WithdrawalRequest");
    }
}
