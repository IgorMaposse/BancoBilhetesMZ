package mz.mozabanco.ticketing.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mz.mozabanco.ticketing.domain.enums.Role;

public record RegisterRequest(
        @NotBlank String name,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, message = "password deve ter no minimo 8 caracteres") String password,
        @NotBlank String role // CLIENTE ou ORGANIZADOR (ADMIN nao se auto-regista)
) {
    public Role roleEnum() {
        return Role.valueOf(role.toUpperCase());
    }
}
