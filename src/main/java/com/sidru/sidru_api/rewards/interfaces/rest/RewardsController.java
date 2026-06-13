package com.sidru.sidru_api.rewards.interfaces.rest;

import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.sidru.sidru_api.rewards.domain.model.commands.RedeemRewardCommand;
import com.sidru.sidru_api.rewards.domain.model.queries.GetAllActiveRewardsQuery;
import com.sidru.sidru_api.rewards.domain.model.queries.GetPointTransactionsByUserIdQuery;
import com.sidru.sidru_api.rewards.domain.model.queries.GetRewardByIdQuery;
import com.sidru.sidru_api.rewards.domain.services.RewardCommandService;
import com.sidru.sidru_api.rewards.domain.services.RewardQueryService;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.CreateRewardResource;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.PointTransactionResource;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.RedeemRewardResource;
import com.sidru.sidru_api.rewards.interfaces.rest.resources.RewardResource;
import com.sidru.sidru_api.rewards.interfaces.rest.transform.CreateRewardCommandFromResourceAssembler;
import com.sidru.sidru_api.rewards.interfaces.rest.transform.PointTransactionResourceFromEntityAssembler;
import com.sidru.sidru_api.rewards.interfaces.rest.transform.RewardResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/rewards", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Rewards", description = "Rewards catalogue and redemption endpoints")
public class RewardsController {

    private final RewardCommandService commandService;
    private final RewardQueryService queryService;

    public RewardsController(RewardCommandService commandService, RewardQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<List<RewardResource>> getActiveRewards() {
        var rewards = queryService.handle(new GetAllActiveRewardsQuery());
        return ResponseEntity.ok(rewards.stream()
                .map(RewardResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RewardResource> getById(@PathVariable Long id) {
        var reward = queryService.handle(new GetRewardByIdQuery(id));
        if (reward.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(RewardResourceFromEntityAssembler.toResourceFromEntity(reward.get()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RewardResource> create(@Valid @RequestBody CreateRewardResource resource) {
        var command = CreateRewardCommandFromResourceAssembler.toCommandFromResource(resource);
        var reward = commandService.handle(command);
        if (reward.isEmpty()) return ResponseEntity.badRequest().build();
        return new ResponseEntity<>(
                RewardResourceFromEntityAssembler.toResourceFromEntity(reward.get()),
                HttpStatus.CREATED);
    }

    @PostMapping("/redeem")
    public ResponseEntity<PointTransactionResource> redeem(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody RedeemRewardResource resource) {
        var tx = commandService.handle(new RedeemRewardCommand(principal.getUserId(), resource.rewardId()));
        if (tx.isEmpty()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(PointTransactionResourceFromEntityAssembler.toResourceFromEntity(tx.get()));
    }

    @GetMapping("/transactions/me")
    public ResponseEntity<List<PointTransactionResource>> myTransactions(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        var txs = queryService.handle(new GetPointTransactionsByUserIdQuery(principal.getUserId()));
        return ResponseEntity.ok(txs.stream()
                .map(PointTransactionResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }
}
