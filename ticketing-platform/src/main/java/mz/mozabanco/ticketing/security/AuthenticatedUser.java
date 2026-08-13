package mz.mozabanco.ticketing.security;

import java.util.UUID;

/** Representa o utilizador autenticado extraido do token JWT (colocado no SecurityContext). */
public record AuthenticatedUser(UUID id, String email, String role) {}
