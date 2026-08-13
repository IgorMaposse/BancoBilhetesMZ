package mz.mozabanco.payments.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autenticacao servico-a-servico simples baseada em Client-Id / Client-Secret,
 * espelhando o padrao usado nas APIs internas da Fidelidade (client_id/client_secret
 * em headers). So o ticketing-platform (e sistemas autorizados) pode chamar esta API.
 *
 * Em producao isto seria substituido por OAuth2 Client Credentials (ex.: Keycloak),
 * mas para o exercicio um filtro de headers e suficiente e demonstra o conceito.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${payments.security.client-id}")
    private String expectedClientId;

    @Value("${payments.security.client-secret}")
    private String expectedClientSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/actuator")
                || request.getRequestURI().startsWith("/swagger-ui")
                || request.getRequestURI().startsWith("/v3/api-docs")
                || request.getRequestURI().startsWith("/h2-console")) {
            chain.doFilter(request, response);
            return;
        }

        String clientId = request.getHeader("client_id");
        String clientSecret = request.getHeader("client_secret");

        if (expectedClientId.equals(clientId) && expectedClientSecret.equals(clientSecret)) {
            var auth = new UsernamePasswordAuthenticationToken(clientId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"UNAUTHORIZED\",\"message\":\"client_id/client_secret invalidos ou em falta\"}");
        }
    }
}
