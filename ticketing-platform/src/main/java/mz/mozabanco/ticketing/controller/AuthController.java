package mz.mozabanco.ticketing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.mozabanco.ticketing.dto.auth.AuthResponse;
import mz.mozabanco.ticketing.dto.auth.LoginRequest;
import mz.mozabanco.ticketing.dto.auth.RegisterRequest;
import mz.mozabanco.ticketing.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
