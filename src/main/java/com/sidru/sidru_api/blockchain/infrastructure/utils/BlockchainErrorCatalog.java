package com.sidru.sidru_api.blockchain.infrastructure.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BlockchainErrorCatalog {
    INVALID_ADDRESS("ERR_BC_004", "La dirección de retiro no es válida (formato/checksum EIP-55)"),
    WITHDRAWAL_IN_PROGRESS("ERR_BC_007", "Ya hay un retiro en proceso para este usuario"),
    NO_BALANCE("ERR_BC_005", "Puntos insuficientes para retirar"),
    WALLET_NOT_AVAILABLE("ERR_BC_008", "No se pudo resolver la dirección de la wallet"),
    MIN_WITHDRAWAL("ERR_BC_009", "El retiro mínimo es de %d puntos"),
    WITHDRAWALS_DISABLED("ERR_BC_010", "Los retiros están temporalmente deshabilitados"),
    WITHDRAWAL_NOT_FOUND("ERR_BC_011", "El retiro no existe o no pertenece al usuario"),
    GENERIC_ERROR("ERR_BC_999", "Ocurrió un error inesperado en el módulo blockchain");

    private final String code;
    private final String message;
}
