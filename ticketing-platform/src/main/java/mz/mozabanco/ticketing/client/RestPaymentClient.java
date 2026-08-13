package mz.mozabanco.ticketing.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Integracao entre sistemas (payment-platform <-> ticketing-platform), autenticada
 * com client_id/client_secret, tal como os endpoints internos da Fidelidade
 * (fidelidademundial.com) que usam o mesmo padrao.
 */
@Component
public class RestPaymentClient implements PaymentClient {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public RestPaymentClient(
            @Value("${payment-platform.base-url}") String baseUrl,
            @Value("${payment-platform.client-id}") String clientId,
            @Value("${payment-platform.client-secret}") String clientSecret) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("client_id", clientId)
                .defaultHeader("client_secret", clientSecret)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public PaymentClientResponse debit(String idempotencyKey, BigDecimal amount, String clientReference, String description) {
        try {
            JsonNode body = restClient.post()
                    .uri("/api/v1/payments/debit")
                    .body(Map.of(
                            "idempotencyKey", idempotencyKey,
                            "amount", amount,
                            "currency", "MZN",
                            "clientReference", clientReference,
                            "description", description == null ? "" : description
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            return toResponse(body);
        } catch (RestClientResponseException ex) {
            throw new PaymentClientException("Falha ao debitar pagamento: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public PaymentClientResponse refund(UUID paymentId, BigDecimal amount, String reason) {
        try {
            JsonNode body = restClient.post()
                    .uri("/api/v1/payments/{id}/refund", paymentId)
                    .body(Map.of("amount", amount, "reason", reason == null ? "" : reason))
                    .retrieve()
                    .body(JsonNode.class);
            return toResponse(body);
        } catch (RestClientResponseException ex) {
            throw new PaymentClientException("Falha ao reembolsar pagamento: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public PaymentClientResponse getStatus(UUID paymentId) {
        try {
            JsonNode body = restClient.get()
                    .uri("/api/v1/payments/{id}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);
            return toResponse(body);
        } catch (RestClientResponseException ex) {
            throw new PaymentClientException("Falha ao consultar pagamento: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private PaymentClientResponse toResponse(JsonNode body) {
        return new PaymentClientResponse(
                UUID.fromString(body.get("id").asText()),
                body.get("status").asText(),
                new BigDecimal(body.get("amount").asText()),
                new BigDecimal(body.get("totalRefunded").asText())
        );
    }
}
