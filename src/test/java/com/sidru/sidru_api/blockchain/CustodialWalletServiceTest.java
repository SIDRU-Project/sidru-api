package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.UserWalletAddress;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.UserWalletAddressRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Determinismo de la derivación custodial HD (RN-BC-04): mismo userId -> misma
 * dirección SIEMPRE; userIds distintos -> direcciones distintas. Además, si ya
 * existe una dirección persistida para el usuario, se reutiliza sin volver a
 * derivar. Sin DB ni red (repositorio mockeado).
 */
class CustodialWalletServiceTest {

    // Mnemónico BIP-39 PÚBLICO de test (vector estándar más conocido). NO es un seed
    // real ni custodia fondos: solo verifica que la derivación es determinística.
    private static final String TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon "
                    + "abandon abandon abandon about";

    private UserWalletAddressRepository repository;
    private CustodialWalletService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserWalletAddressRepository.class);
        // Sin dirección previa -> deriva siempre (probamos la pureza de la derivación).
        when(repository.findByUserId(any())).thenReturn(Optional.empty());
        when(repository.save(any(UserWalletAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        BlockchainProperties props = new BlockchainProperties();
        props.setWalletMasterSeed(TEST_MNEMONIC);
        service = new CustodialWalletService(repository, props);
    }

    @Test
    void mismoUserIdDerivaSiempreLaMismaDireccion() {
        String first = service.addressFor(42L);
        String second = service.addressFor(42L);
        String third = service.addressFor(42L);

        assertEquals(first, second);
        assertEquals(second, third);
        // Dirección EVM bien formada (40 hex con prefijo 0x).
        assertTrue(first.matches("^0x[0-9a-fA-F]{40}$"), "Dirección EVM mal formada: " + first);
    }

    @Test
    void userIdsDistintosDerivanDireccionesDistintas() {
        String userA = service.addressFor(1L);
        String userB = service.addressFor(2L);
        String userC = service.addressFor(3L);

        assertNotEquals(userA, userB);
        assertNotEquals(userB, userC);
        assertNotEquals(userA, userC);
    }

    @Test
    void siYaExisteDireccionPersistidaLaReutilizaSinDerivar() {
        UserWalletAddress stored =
                new UserWalletAddress(7L, "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", 7L);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(stored));

        String address = service.addressFor(7L);

        assertEquals("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed", address);
        // No persiste nada nuevo: reutiliza la dirección existente.
        verify(repository, never()).save(any());
    }
}
