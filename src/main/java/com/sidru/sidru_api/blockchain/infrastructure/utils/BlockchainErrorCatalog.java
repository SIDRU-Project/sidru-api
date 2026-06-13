package com.sidru.sidru_api.blockchain.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BlockchainErrorCatalog {
    INVALID_ADDRESS("ERR_BC_004", "La dirección de retiro no es válida (formato/checksum EIP-55)"),
    WITHDRAWAL_IN_PROGRESS("ERR_BC_007", "Ya hay un retiro en proceso para este usuario"),
    NO_BALANCE("ERR_BC_005", "Sin saldo para retirar"),
    WALLET_NOT_AVAILABLE("ERR_BC_008", "No se pudo resolver la dirección de la wallet"),
    GENERIC_ERROR("ERR_BC_999", "Ocurrió un error inesperado en el módulo blockchain");

    private final String code;
    private final String message;
}
