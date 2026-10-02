package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CP-WS-11 — Dos primeros accesos simultaneos de un usuario nuevo (US-WS-06). */
@DisplayName("CP-WS-11 - Concurrencia en el primer acceso social")
class CpWs11ConcurrenciaTest extends CpWsBase {

    @Test
    @DisplayName("CP-WS-11: accesos simultaneos con la misma identidad dejan un solo usuario y una sola identidad")
    void accesosSimultaneosUnSoloUsuario() throws Exception {
        int threads = 6;
        String sub = uniqueSub();
        String email = uniqueEmail("carrera");
        String token = googleToken(sub, email, true, "Carrera Doble");

        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<int[]>> futures = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    var result = google(token);
                    var response = result.andReturn().getResponse();
                    long id = response.getStatus() == 200 ? idOf(bodyOf(result)) : -1;
                    return new int[]{response.getStatus(), (int) id};
                }));
            }
            start.countDown();
            for (var future : futures) {
                int[] outcome = future.get();
                assertEquals(200, outcome[0], "todas las peticiones simultaneas deben terminar bien");
                ids.add((long) outcome[1]);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, ids.stream().distinct().count(), "todas devuelven el mismo usuario");
        assertEquals(1, userRepository.findAll().stream().filter(u -> email.equalsIgnoreCase(u.getEmail())).count());
        assertEquals(1, socialIdentityRepository.findAll().stream().filter(i -> sub.equals(i.getSubject())).count());
        assertTrue(userProfileRepository.existsByUserId(ids.get(0)));
    }
}
