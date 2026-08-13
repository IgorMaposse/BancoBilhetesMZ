package mz.mozabanco.ticketing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.mozabanco.ticketing.dto.event.CreateEventRequest;
import mz.mozabanco.ticketing.dto.event.EventResponse;
import mz.mozabanco.ticketing.security.AuthenticatedUser;
import mz.mozabanco.ticketing.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // RF3: consulta publica de eventos (nao requer autenticacao)
    @GetMapping
    public ResponseEntity<List<EventResponse>> list(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(eventService.listPublished(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    // RF2: organizadores disponibilizam eventos para venda
    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request, user));
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publish(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(eventService.publish(id, user));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<EventResponse> cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(eventService.cancel(id, user));
    }

    @GetMapping("/mine/list")
    public ResponseEntity<List<EventResponse>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(eventService.listMine(user));
    }
}
