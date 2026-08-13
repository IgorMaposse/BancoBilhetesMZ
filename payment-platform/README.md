# payment-platform

Plataforma de Pagamentos do Moza Banco (RF4) — API própria de débito, reembolso e
consulta do estado de transação, a ser consumida pelo `ticketing-platform`
(BancoBilhetes) e por qualquer outro sistema autorizado do banco.

## Requisitos

- Java 17+
- Maven 3.9+

## Correr localmente (perfil `dev`, com H2 em memória)

```bash
mvn spring-boot:run
```

A API fica disponível em `http://localhost:8081`.
Consola H2: `http://localhost:8081/h2-console` (JDBC URL: `jdbc:h2:mem:payments`, user `sa`, sem password).
Swagger UI: `http://localhost:8081/swagger-ui.html`.

## Autenticação

Todos os endpoints (exceto `/actuator/**` e `/swagger-ui/**`) exigem os headers:

```
client_id: bancobilhetes-app
client_secret: bancobilhetes-secret
```

(valores por omissão definidos em `application.yml` / variáveis de ambiente
`PAYMENTS_CLIENT_ID` e `PAYMENTS_CLIENT_SECRET`).

## Endpoints

### Débito
```
POST /api/v1/payments/debit
Content-Type: application/json
client_id: bancobilhetes-app
client_secret: bancobilhetes-secret

{
  "idempotencyKey": "reserva-123",
  "amount": 500.00,
  "currency": "MZN",
  "clientReference": "reserva-123",
  "description": "Bilhetes concerto"
}
```
Resposta `201 Created` com o pagamento criado. Se o mesmo `idempotencyKey` for
reenviado, devolve o pagamento já existente em vez de debitar novamente.

### Reembolso (total ou parcial)
```
POST /api/v1/payments/{paymentId}/refund
Content-Type: application/json
client_id: bancobilhetes-app
client_secret: bancobilhetes-secret

{
  "amount": 400.00,
  "reason": "Cancelamento com mais de 30 dias de antecedência"
}
```

### Consulta de estado
```
GET /api/v1/payments/{paymentId}
client_id: bancobilhetes-app
client_secret: bancobilhetes-secret
```

## Testes

```bash
mvn test
```

Cobre: débito com sucesso, idempotência (pedido duplicado não gera novo débito),
reembolso parcial, reembolso total (estado passa a `REFUNDED`), rejeição de
reembolso acima do valor disponível, e consulta de pagamento inexistente.

## Notas de arquitetura / premissas assumidas

- Não existe integração real com processador de cartões — o débito é simulado
  de forma determinística e síncrona (sempre `COMPLETED`). Em produção, este
  serviço chamaria o core bancário / switch de pagamentos.
- Autenticação serviço-a-serviço simplificada via `client_id`/`client_secret`
  em headers (o mesmo padrão usado nas APIs internas reais da Fidelidade).
  Em produção seria substituído por OAuth2 Client Credentials.
- Perfil `docker` liga a Postgres (ver `docker-compose.yml` e `ARCHITECTURE.md`
  na raiz do monorepo); perfil `dev` usa H2 em memória para arranque imediato.
