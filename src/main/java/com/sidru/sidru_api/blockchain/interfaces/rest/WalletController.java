package com.sidru.sidru_api.blockchain.interfaces.rest;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.queryservices.WalletQueryService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WalletResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WithdrawRequestResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WithdrawalStatusResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.transform.WalletResourceFromViewAssembler;
import com.sidru.sidru_api.blockchain.interfaces.rest.transform.WithdrawalStatusResourceFromEntityAssembler;
import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Citizen wallet endpoints (spec sidru-mainnet: retiro desde puntos). Context-path /api/v1
 * es global. userId siempre viene del principal del JWT, nunca del cuerpo de la petición.
 */
@RestController
@RequestMapping(value = "/wallet", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Wallet", description = "Wallet: saldo en puntos y retiros a CTC/USDC")
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final WithdrawalCommandService withdrawalCommandService;
    private final WithdrawalStatusResourceFromEntityAssembler withdrawalStatusAssembler;

    public WalletController(WalletQueryService walletQueryService,
                            WithdrawalCommandService withdrawalCommandService,
                            WithdrawalStatusResourceFromEntityAssembler withdrawalStatusAssembler) {
        this.walletQueryService = walletQueryService;
        this.withdrawalCommandService = withdrawalCommandService;
        this.withdrawalStatusAssembler = withdrawalStatusAssembler;
    }

    @GetMapping("/me")
    public ResponseEntity<WalletResource> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        var view = walletQueryService.getWallet(principal.getUserId());
        return ResponseEntity.ok(WalletResourceFromViewAssembler.toResourceFromView(view));
    }

    @GetMapping("/me/withdrawals")
    public ResponseEntity<List<WithdrawalStatusResource>> withdrawals(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        var requests = walletQueryService.getWithdrawals(principal.getUserId());
        return ResponseEntity.ok(requests.stream()
                .map(withdrawalStatusAssembler::toResourceFromEntity)
                .toList());
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalStatusResource> withdraw(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody WithdrawRequestResource resource) {
        WithdrawalRequest request = withdrawalCommandService.withdraw(
                principal.getUserId(), resource.toAddress(), resource.points(), resource.mode());
        // 200 si el resultado ya es final (COMPLETADO o FALLIDO: el cliente lee status); 202
        // solo si sigue EN_PROCESO.
        HttpStatus status = request.getStatus() == WithdrawalStatus.EN_PROCESO
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(withdrawalStatusAssembler.toResourceFromEntity(request));
    }

    @GetMapping("/withdraw/{id}")
    public ResponseEntity<WithdrawalStatusResource> withdrawById(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long id) {
        WithdrawalRequest request = withdrawalCommandService.getById(id, principal.getUserId());
        return ResponseEntity.ok(withdrawalStatusAssembler.toResourceFromEntity(request));
    }

    @GetMapping("/withdraw/status")
    public ResponseEntity<WithdrawalStatusResource> withdrawStatus(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        WithdrawalRequest request = withdrawalCommandService.lastStatus(principal.getUserId());
        if (request == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(withdrawalStatusAssembler.toResourceFromEntity(request));
    }
}
