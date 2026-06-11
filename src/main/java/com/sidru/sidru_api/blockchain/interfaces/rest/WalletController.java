package com.sidru.sidru_api.blockchain.interfaces.rest;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.queryservices.WalletQueryService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WalletResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WalletTransactionResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WithdrawRequestResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WithdrawalStatusResource;
import com.sidru.sidru_api.blockchain.interfaces.rest.transform.WalletResourceFromViewAssembler;
import com.sidru.sidru_api.blockchain.interfaces.rest.transform.WalletTransactionResourceFromViewAssembler;
import com.sidru.sidru_api.blockchain.interfaces.rest.transform.WithdrawalStatusResourceFromEntityAssembler;
import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Citizen wallet endpoints (custodial CTC). Context-path /api/v1 is global.
 * userId always comes from the JWT principal, never from the request body.
 */
@RestController
@RequestMapping(value = "/wallet", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Wallet", description = "Custodial CTC wallet: balance, transactions and withdrawals")
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final WithdrawalCommandService withdrawalCommandService;

    public WalletController(WalletQueryService walletQueryService,
                            WithdrawalCommandService withdrawalCommandService) {
        this.walletQueryService = walletQueryService;
        this.withdrawalCommandService = withdrawalCommandService;
    }

    @GetMapping("/me")
    public ResponseEntity<WalletResource> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        var view = walletQueryService.getWallet(principal.getUserId());
        return ResponseEntity.ok(WalletResourceFromViewAssembler.toResourceFromView(view));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<List<WalletTransactionResource>> transactions(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        var views = walletQueryService.getTransactions(principal.getUserId());
        return ResponseEntity.ok(views.stream()
                .map(WalletTransactionResourceFromViewAssembler::toResourceFromView)
                .toList());
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalStatusResource> withdraw(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody WithdrawRequestResource resource) {
        WithdrawalRequest request =
                withdrawalCommandService.withdraw(principal.getUserId(), resource.toAddress());
        return ResponseEntity.ok(
                WithdrawalStatusResourceFromEntityAssembler.toResourceFromEntity(request));
    }

    @GetMapping("/withdraw/status")
    public ResponseEntity<WithdrawalStatusResource> withdrawStatus(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        WithdrawalRequest request = withdrawalCommandService.lastStatus(principal.getUserId());
        if (request == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(
                WithdrawalStatusResourceFromEntityAssembler.toResourceFromEntity(request));
    }
}
