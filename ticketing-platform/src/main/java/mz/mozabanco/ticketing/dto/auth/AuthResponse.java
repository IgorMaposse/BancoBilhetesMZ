package mz.mozabanco.ticketing.dto.auth;

import java.util.UUID;

public record AuthResponse(String token, UUID userId, String name, String role) {}
