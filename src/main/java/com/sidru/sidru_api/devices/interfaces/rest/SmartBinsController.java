package com.sidru.sidru_api.devices.interfaces.rest;

import com.sidru.sidru_api.devices.domain.model.queries.GetAllSmartBinsQuery;
import com.sidru.sidru_api.devices.domain.model.queries.GetSmartBinByIdQuery;
import com.sidru.sidru_api.devices.domain.services.SmartBinCommandService;
import com.sidru.sidru_api.devices.domain.services.SmartBinQueryService;
import com.sidru.sidru_api.devices.interfaces.rest.resources.RegisterSmartBinResource;
import com.sidru.sidru_api.devices.interfaces.rest.resources.SmartBinResource;
import com.sidru.sidru_api.devices.interfaces.rest.resources.UpdateSmartBinResource;
import com.sidru.sidru_api.devices.interfaces.rest.transform.RegisterSmartBinCommandFromResourceAssembler;
import com.sidru.sidru_api.devices.interfaces.rest.transform.SmartBinResourceFromEntityAssembler;
import com.sidru.sidru_api.devices.interfaces.rest.transform.UpdateSmartBinCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/smart-bins", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Smart Bins", description = "Smart Bin management endpoints")
public class SmartBinsController {

    private final SmartBinCommandService smartBinCommandService;
    private final SmartBinQueryService smartBinQueryService;

    public SmartBinsController(SmartBinCommandService smartBinCommandService,
                               SmartBinQueryService smartBinQueryService) {
        this.smartBinCommandService = smartBinCommandService;
        this.smartBinQueryService = smartBinQueryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SmartBinResource> register(@Valid @RequestBody RegisterSmartBinResource resource) {
        var command = RegisterSmartBinCommandFromResourceAssembler.toCommandFromResource(resource);
        var bin = smartBinCommandService.handle(command);
        if (bin.isEmpty()) return ResponseEntity.badRequest().build();
        return new ResponseEntity<>(
                SmartBinResourceFromEntityAssembler.toResourceFromEntity(bin.get()),
                HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<SmartBinResource>> getAll() {
        var bins = smartBinQueryService.handle(new GetAllSmartBinsQuery());
        return ResponseEntity.ok(bins.stream()
                .map(SmartBinResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SmartBinResource> getById(@PathVariable Long id) {
        var bin = smartBinQueryService.handle(new GetSmartBinByIdQuery(id));
        if (bin.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(SmartBinResourceFromEntityAssembler.toResourceFromEntity(bin.get()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SmartBinResource> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateSmartBinResource resource) {
        var command = UpdateSmartBinCommandFromResourceAssembler.toCommandFromResource(id, resource);
        var bin = smartBinCommandService.handle(command);
        if (bin.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(SmartBinResourceFromEntityAssembler.toResourceFromEntity(bin.get()));
    }
}
