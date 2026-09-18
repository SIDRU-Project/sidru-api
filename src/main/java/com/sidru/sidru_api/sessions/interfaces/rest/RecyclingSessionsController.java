package com.sidru.sidru_api.sessions.interfaces.rest;

import com.sidru.sidru_api.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.sidru.sidru_api.sessions.domain.model.commands.CancelRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.commands.ConfirmRecyclingSessionCommand;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionByIdQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionByQrTokenQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionsByUserIdPagedQuery;
import com.sidru.sidru_api.sessions.domain.model.queries.GetRecyclingSessionsByUserIdQuery;
import com.sidru.sidru_api.sessions.domain.services.RecyclingSessionCommandService;
import com.sidru.sidru_api.sessions.domain.services.RecyclingSessionQueryService;
import com.sidru.sidru_api.sessions.interfaces.rest.resources.CreateRecyclingSessionResource;
import com.sidru.sidru_api.sessions.interfaces.rest.resources.RecyclingSessionResource;
import com.sidru.sidru_api.sessions.interfaces.rest.transform.CreateRecyclingSessionCommandFromResourceAssembler;
import com.sidru.sidru_api.sessions.interfaces.rest.transform.RecyclingSessionResourceFromEntityAssembler;
import com.sidru.sidru_api.shared.interfaces.rest.resources.PagedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Recycling Sessions", description = "Recycling session lifecycle endpoints")
public class RecyclingSessionsController {

    private final RecyclingSessionCommandService commandService;
    private final RecyclingSessionQueryService queryService;

    public RecyclingSessionsController(RecyclingSessionCommandService commandService,
                                       RecyclingSessionQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** Called by Smart Bin via X-Device-Api-Key header. */
    @PostMapping
    public ResponseEntity<RecyclingSessionResource> create(
            @RequestHeader("X-Device-Api-Key") String deviceApiKey,
            @Valid @RequestBody CreateRecyclingSessionResource resource) {
        var command = CreateRecyclingSessionCommandFromResourceAssembler
                .toCommandFromResource(deviceApiKey, resource);
        var session = commandService.handle(command);
        if (session.isEmpty()) return ResponseEntity.badRequest().build();
        return new ResponseEntity<>(
                RecyclingSessionResourceFromEntityAssembler.toResourceFromEntity(session.get()),
                HttpStatus.CREATED);
    }

    @GetMapping("/qr/{qrToken}")
    public ResponseEntity<RecyclingSessionResource> getByQr(@PathVariable String qrToken) {
        var session = queryService.handle(new GetRecyclingSessionByQrTokenQuery(qrToken));
        if (session.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                RecyclingSessionResourceFromEntityAssembler.toResourceFromEntity(session.get()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecyclingSessionResource> getById(@PathVariable Long id) {
        var session = queryService.handle(new GetRecyclingSessionByIdQuery(id));
        if (session.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                RecyclingSessionResourceFromEntityAssembler.toResourceFromEntity(session.get()));
    }

    @PostMapping("/qr/{qrToken}/confirm")
    public ResponseEntity<RecyclingSessionResource> confirm(
            @PathVariable String qrToken,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        var session = commandService.handle(new ConfirmRecyclingSessionCommand(qrToken, principal.getUserId()));
        if (session.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                RecyclingSessionResourceFromEntityAssembler.toResourceFromEntity(session.get()));
    }

    /**
     * Historial del ciudadano autenticado, siempre ordenado por fecha de registro descendente.
     *
     * Sin parametros devuelve la lista completa (contrato historico que consume la app).
     * Con {@code page} y/o {@code size} devuelve el envoltorio paginado con
     * {@code content/page/size/totalElements/totalPages} (CP016 / US-22). Se mantienen las
     * dos formas para no romper los clientes ya desplegados.
     */
    @GetMapping("/me")
    @Operation(summary = "Historial del ciudadano: lista completa, o pagina si se envia page/size")
    public ResponseEntity<Object> myHistory(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (page == null && size == null) {
            var sessions = queryService.handle(new GetRecyclingSessionsByUserIdQuery(principal.getUserId()));
            return ResponseEntity.ok(sessions.stream()
                    .map(RecyclingSessionResourceFromEntityAssembler::toResourceFromEntity)
                    .toList());
        }

        var pageResult = queryService.handle(new GetRecyclingSessionsByUserIdPagedQuery(
                principal.getUserId(),
                page == null ? 0 : page,
                size == null ? GetRecyclingSessionsByUserIdPagedQuery.DEFAULT_SIZE : size));

        return ResponseEntity.ok(PagedResource.from(
                pageResult, RecyclingSessionResourceFromEntityAssembler::toResourceFromEntity));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<RecyclingSessionResource> cancel(@PathVariable Long id) {
        var session = commandService.handle(new CancelRecyclingSessionCommand(id));
        if (session.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                RecyclingSessionResourceFromEntityAssembler.toResourceFromEntity(session.get()));
    }
}
