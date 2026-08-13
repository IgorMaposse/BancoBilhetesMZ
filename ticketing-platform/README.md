# ticketing-platform (BancoBilhetes)

Plataforma de venda de bilhetes para eventos. Gere utilizadores, eventos, reservas
e bilhetes, e integra-se com o `payment-platform` (Plataforma de Pagamentos do
Moza Banco) para processar débitos e reembolsos.

## Requisitos

- Java 17+
- Maven 3.9+
- O módulo `payment-platform` a correr em `http://localhost:8081` (ver o README
  desse módulo). Sem ele, os endpoints de pagamento/cancelamento de reservas
  falham com `502 Bad Gateway` — o resto da API (registo, login, eventos)
  funciona à mesma.

## Correr localmente (perfil `dev`, com H2 em memória)

```bash
mvn spring-boot:run
```

A API fica disponível em `http://localhost:8080`.
Consola H2: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:ticketing`, user `sa`, sem password).
Swagger UI: `http://localhost:8080/swagger-ui.html`.

Por omissão, este módulo espera encontrar o `payment-platform` em
`http://localhost:8081` com `client_id=bancobilhetes-app` /
`client_secret=bancobilhetes-secret` (os mesmos valores por omissão do
`payment-platform`). Configurável via `PAYMENT_PLATFORM_URL`,
`PAYMENTS_CLIENT_ID`, `PAYMENTS_CLIENT_SECRET`.

## Perfis de utilizador (RF1)

| Perfil       | Permissões |
|--------------|------------|
| `CLIENTE`    | Consultar eventos publicados, criar reservas, pagar, cancelar e consultar o próprio histórico |
| `ORGANIZADOR`| Criar/publicar/cancelar os seus próprios eventos |
| `ADMIN`      | Acesso total (todos os eventos, todas as reservas) |

## Fluxo típico (passo a passo)

### 1. Registar um organizador
```
POST /api/v1/auth/register
{
  "name": "Produtora XPTO",
  "email": "organizador@xpto.mz",
  "password": "senha12345",
  "role": "ORGANIZADOR"
}
```
Resposta inclui um `token` JWT — usar em `Authorization: Bearer <token>` nos
pedidos seguintes.

### 2. Criar um evento (como organizador)
```
POST /api/v1/events
Authorization: Bearer <token-organizador>
{
  "name": "Festival de Marrabenta",
  "description": "Uma noite de música tradicional moçambicana",
  "category": "concerto",
  "venue": "Estádio da Machava",
  "address": "Machava, Maputo",
  "eventDate": "2027-03-15T19:00:00",
  "ticketTypes": [
    { "name": "Geral", "price": 500.00, "quantityTotal": 1000 },
    { "name": "VIP",   "price": 1500.00, "quantityTotal": 100 }
  ]
}
```

### 3. Publicar o evento
```
PUT /api/v1/events/{eventId}/publish
Authorization: Bearer <token-organizador>
```

### 4. Registar/login como cliente, consultar eventos, reservar
```
GET /api/v1/events                     (público, não precisa de token)

POST /api/v1/reservations
Authorization: Bearer <token-cliente>
{
  "eventId": "...",
  "items": [{ "ticketTypeId": "...", "quantity": 2 }]
}
```

### 5. Pagar a reserva (chama o payment-platform)
```
POST /api/v1/reservations/{reservationId}/pay
Authorization: Bearer <token-cliente>
```

### 6. Alterar uma reserva ainda não paga (RF3)
```
PUT /api/v1/reservations/{reservationId}
Authorization: Bearer <token-cliente>
{
  "items": [{ "ticketTypeId": "...", "quantity": 3 }]
}
```
Substitui integralmente as quantidades/tipos de bilhete escolhidos. Só é
possível enquanto o estado for `PENDING_PAYMENT` (antes de pagar). Depois de
`CONFIRMED`, o fluxo correto é cancelar (aplica RF5) e criar uma nova reserva.

### 7. Cancelar (aplica a política de reembolso RF5: 80% se >30 dias do evento, 50% caso contrário)
```
POST /api/v1/reservations/{reservationId}/cancel
Authorization: Bearer <token-cliente>
{ "reason": "opcional" }
```
Reservas ainda `PENDING_PAYMENT` também podem ser canceladas — como nada foi
cobrado, não há reembolso a processar (`CANCELLED_NO_REFUND`).

### 8. Histórico de compras
```
GET /api/v1/reservations/history
Authorization: Bearer <token-cliente>
```

## Testes

```bash
mvn test
```

Cobre a política de reembolso (80%/50%), cancelamento sem reembolso de
reservas ainda não pagas, cancelamento em cascata de reservas na sequência do
cancelamento de um evento pelo organizador, regras de propriedade da reserva
(um cliente não pode alterar/cancelar a reserva de outro), e transições de
estado inválidas (não é possível alterar uma reserva já confirmada, nem
cancelar uma reserva que já está cancelada/expirada).

## Correr com Docker

Ver `docker-compose.yml` e `ARCHITECTURE.md` na raiz do monorepo (`docker compose up --build`).

## Notas de arquitetura / premissas assumidas

- Reservas não pagas em 15 minutos expiram automaticamente (job agendado,
  `ReservationExpirationJob`) e libertam o inventário de bilhetes.
- Consistência reserva↔pagamento: o débito é síncrono dentro do próprio pedido
  HTTP; se falhar, a reserva passa a `PAYMENT_FAILED` e os lugares são
  libertados na mesma transação. Em produção, recomendar-se-ia um padrão
  outbox + fila (Kafka/RabbitMQ) para tolerar indisponibilidade temporária do
  `payment-platform`.
- Controlo de concorrência otimista/pessimista em `TicketType` evita
  overbooking quando vários clientes reservam o mesmo lote em simultâneo.
- Cancelar um evento (pelo organizador) propaga automaticamente o
  cancelamento/reembolso (100%) a todas as reservas confirmadas ou pendentes
  desse evento.
- Alterar uma reserva (`PUT /reservations/{id}`) só é permitido antes do
  pagamento; depois de confirmada, cancela-se (RF5) e cria-se uma nova.
