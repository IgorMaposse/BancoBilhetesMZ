package mz.mozabanco.ticketing.service;

import lombok.RequiredArgsConstructor;
import mz.mozabanco.ticketing.domain.User;
import mz.mozabanco.ticketing.domain.enums.Role;
import mz.mozabanco.ticketing.dto.auth.AuthResponse;
import mz.mozabanco.ticketing.dto.auth.LoginRequest;
import mz.mozabanco.ticketing.dto.auth.RegisterRequest;
import mz.mozabanco.ticketing.exception.BusinessException;
import mz.mozabanco.ticketing.repository.UserRepository;
import mz.mozabanco.ticketing.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Ja existe um utilizador com este email", HttpStatus.CONFLICT);
        }

        Role role = request.roleEnum();
        if (role == Role.ADMIN) {
            throw new BusinessException("Nao e possivel auto-registar como ADMIN", HttpStatus.FORBIDDEN);
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .active(true)
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, saved.getId(), saved.getName(), saved.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Credenciais invalidas", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Credenciais invalidas", HttpStatus.UNAUTHORIZED);
        }
        if (!user.isActive()) {
            throw new BusinessException("Utilizador inativo", HttpStatus.FORBIDDEN);
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getName(), user.getRole().name());
    }
}
