Como correr tudo localmente?

Ver `docker-compose.yml` na raiz. 

```bash
docker compose up --build
```
--Frontend | http://localhost:4200 |

Alternativa: Cada módulo individualmente
Precisa de 3 terminais separados, por esta ordem o payment-platform tem de estar de pé antes do ticketing-platform processar pagamentos

Terminal 1 — payment-platform

cd payment-platform
mvn spring-boot:run
→ http://localhost:8081

Terminal 2 — ticketing-platform
cd ticketing-platform
mvn spring-boot:run
→ http://localhost:8080

Terminal 3 — frontend
cd frontend
npm install
npm start
→ http://localhost:4200


## 1. Visão geral da arquitetura

A solução é composta por três módulos independentes, cada um com o seu próprio
repositório e ciclo de vida:

```
                 ┌──────────────────────┐
   Browser  ───▶ │  frontend (Angular)  │
                 └──────────┬───────────┘
                             │ REST / JSON (HTTPS em produção)
                             ▼
                 ┌──────────────────────┐        REST / JSON        ┌───────────────────────┐
                 │  ticketing-platform   │ ─────────────────────────▶│   payment-platform     │
                 │  (Spring Boot, :8080) │  client_id/client_secret  │   (Spring Boot, :8081) │
                 └──────────┬───────────┘                            └───────────┬───────────┘
                             │ JPA                                                │ JPA
                             ▼                                                    ▼
                 ┌──────────────────────┐                            ┌───────────────────────┐
                 │   ticketing-db (PG)  │                            │   payment-db (PG)      │
                 └──────────────────────┘                            └───────────────────────┘
```

**Porque três módulos separados e não um monólito?**
O enunciado (RF4) exige explicitamente que toda a lógica financeira passe por
uma Plataforma de Pagamentos própria do Moza Banco, com API dedicada. Isto tem
uma consequência de arquitetura relevante: o `payment-platform` não é uma
biblioteca interna do `ticketing-platform`, é um **serviço bancário
independente**, com a sua própria base de dados, autenticação e ciclo de vida —
tal como aconteceria com o core bancário/switch de pagamentos reais do banco,
que pode (e deve) ser reutilizado por outras aplicações do Moza Banco além do
BancoBilhetes. Separar os dois força uma fronteira de API bem definida e evita
o acoplamento que existiria se a lógica de débito/reembolso vivesse dentro do
mesmo processo/transação da reserva de bilhetes.

Cada serviço tem a sua própria base de dados (padrão *database-per-service*):
isto evita que uma alteração de esquema num módulo obrigue a coordenação com o
outro, e reflecte a fronteira de responsabilidade real (o `payment-platform`
não precisa de saber o que é um "evento" ou uma "reserva").

## 2. Tecnologias utilizadas

| Camada       | Tecnologia | Justificação |
|--------------|-----------|---------------|
| Backend      | Java 17 + Spring Boot 3.3 | Obrigatório pelo enunciado |
| Persistência | Spring Data JPA + Hibernate | Obrigatório pelo enunciado |
| Base de dados| PostgreSQL (perfil `docker`) / H2 em memória (perfil `dev`) | Postgres para paridade com produção; H2 para arranque instantâneo em desenvolvimento local, sem dependências externas |
| Frontend     | Angular 18 (standalone components, Signals) | Recomendado pelo enunciado; Signals evita a necessidade de uma dependência extra de gestão de estado (NgRx) para o âmbito desta aplicação |
| Autenticação | JWT (HMAC-SHA256), stateless | Adequado a uma API REST sem sessão, permite escalar horizontalmente qualquer um dos serviços sem *sticky sessions* |
| Serviço-a-serviço | `client_id`/`client_secret` em headers 
| Documentação de API | springdoc-openapi (Swagger UI) | Facilita a validação manual dos endpoints durante a apresentação |
| Testes       | JUnit 5 + Mockito + AssertJ | Testes unitários dos serviços de domínio (regras de negócio RF3/RF4/RF5), isolados de infraestrutura |
| Contentorização | Docker + Docker Compose | Entregável explícito do exercício ("scripts necessários para execução") |

## 3. Perfis de utilizador (RF1)

| Perfil        | Permissões |
|---------------|------------|
| `CLIENTE`     | Consultar eventos publicados, criar/alterar/pagar/cancelar as suas reservas, consultar o próprio histórico |
| `ORGANIZADOR` | Criar, publicar e cancelar os seus próprios eventos |
| `ADMIN`       | Acesso total (todos os eventos, todas as reservas, de qualquer organizador/cliente) |

## 4. Ciclo de vida de uma reserva (RF3)

```
                 ┌──────────────────┐
                 │  PENDING_PAYMENT │◀── criada com POST /reservations
                 └───┬───┬───┬──────┘      (bloqueia inventário)
                     │   │   │
     alterar itens ──┘   │   └── cancelar (sem custo, nada foi cobrado)
     (PUT /{id})         │            │
        (mesmo estado)   │            ▼
                          │   ┌─────────────────────┐
                          │   │  CANCELLED_NO_REFUND │
                          │   └─────────────────────┘
             pagar (POST /{id}/pay)
                          │
              ┌───────────┼────────────┐
              ▼           ▼            ▼
        ┌───────────┐ ┌────────────┐ ┌─────────┐
        │ CONFIRMED │ │PAYMENT_FAILED│ │ EXPIRED │  (job agendado, 15 min sem pagamento)
        └─────┬─────┘ └────────────┘ └─────────┘
              │
     cancelar (POST /{id}/cancel) — RF5
              │
              ▼
   ┌────────────────────────────────────────────┐
   │ CANCELLED_REFUNDED                          │
   │   > 30 dias do evento → reembolso de 80%    │
   │  ≤ 30 dias do evento → reembolso de 50%     │
   │  evento cancelado pelo organizador → 100%   │
   └────────────────────────────────────────────┘
```

**Alteração de reserva (RF3, "todo o resto"):** só é permitida enquanto a
reserva está `PENDING_PAYMENT`. Depois de `CONFIRMED`, o dinheiro já mudou de
mãos através do `payment-platform`, pelo que uma "alteração" deixa de fazer
sentido como operação atómica única — nesse caso o fluxo correto é cancelar
(aplicando a política de reembolso RF5) e criar uma nova reserva. Esta é uma
decisão de negócio explícita, documentada no código (`ReservationService`).

## 5. Consistência reserva ↔ pagamento (RF5)

- A reserva só é criada depois de o lote de bilhetes ser bloqueado
  atomicamente (`SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)` em
  `TicketType`), o que evita *overbooking* quando dois clientes tentam
  reservar os últimos lugares em simultâneo.
- A confirmação da reserva só acontece depois de o débito no
  `payment-platform` ter sucesso. Se o débito falhar (ou o serviço estiver
  indisponível), a reserva passa a `PAYMENT_FAILED` e os lugares são
  libertados **na mesma transação** — nunca ficam bilhetes reservados sem
  correspondência a um pagamento válido.
- **Cancelamento pelo cliente:** a política de reembolso 80%/50% é aplicada
  antes de o pedido de reembolso ser enviado ao `payment-platform`; se o
  reembolso falhar, a reserva mantém-se `CONFIRMED` (não avança para
  cancelada) e o cliente pode tentar novamente.
- **Cancelamento de evento pelo organizador:** propaga-se em cascata a todas
  as reservas `CONFIRMED`/`PENDING_PAYMENT` desse evento. Reservas confirmadas
  recebem reembolso de 100% (a falha é do organizador, não do cliente);
  reservas pendentes são simplesmente libertadas. Se o reembolso de uma
  reserva específica falhar, o evento e as restantes reservas não ficam
  bloqueados — o erro é registado em log para seguimento manual, evitando que
  uma falha isolada trave toda a operação de cancelamento do evento.
  
- **Premissa assumida:** dado o prazo do exercício, a chamada ao
  `payment-platform` é síncrona, dentro do próprio pedido HTTP. Em produção,
  recomendar-se-ia um padrão *outbox* + fila de mensagens (ex.: RabbitMQ)
  para tolerar indisponibilidade temporária do `payment-platform` sem
  bloquear o utilizador nem arriscar inconsistências em caso de falha a meio
  do pedido.

## 6. Segurança

- Autenticação stateless via JWT (HS256), token com expiração de 2h por
  omissão, validado em cada pedido por um `OncePerRequestFilter`.
- Autorização por perfil ao nível do endpoint (`hasAnyRole(...)`) e, dentro do
  serviço, verificação de posse do recurso (um cliente só vê/altera/cancela as
  suas próprias reservas; um organizador só gere os seus próprios eventos;
  `ADMIN` tem acesso total).
- Comunicação `ticketing-platform` → `payment-platform` autenticada por
  `client_id`/`client_secret` em headers — Em produção
  seria substituído por OAuth2 Client Credentials com rotação de segredo.
  As mesmas credenciais nunca circulam para o browser — o frontend só fala
  com o `ticketing-platform`, nunca diretamente com o `payment-platform`.
- Palavras-passe com hash BCrypt.
- CORS restrito nos headers/métodos usados pela SPA.

## 7. Testabilidade

- Testes unitários dos serviços de domínio (`ReservationServiceTest`,
  `EventServiceTest`, `PaymentServiceTest`) usando Mockito para isolar
  repositórios e o cliente HTTP do `payment-platform`, cobrindo:
  a política de reembolso 80%/50%, cancelamento sem reembolso de reservas
  ainda não pagas, cancelamento em cascata na sequência do cancelamento de um
  evento, regras de propriedade (um cliente não pode operar sobre a reserva
  de outro; um organizador não pode operar sobre o evento de outro), e
  transições de estado inválidas.
  
- A separação clara entre `Controller` (HTTP), `Service` (regras de negócio) e
  `Repository`/`Client` (infraestrutura) permite testar as regras de negócio
  sem precisar de subir um contexto Spring completo nem uma base de dados.

## 8. Trabalho futuro / fora do âmbito mínimo do exercício

- Padrão *outbox* + fila para o débito/reembolso, em vez de chamada síncrona.
- Refresh token / renovação de sessão no frontend.
- Emissão de bilhetes com QR code e validação à entrada do evento.
- Painel de administração dedicado (o `ADMIN` já tem permissões de backend
  para tal, falta a interface).
- Notificações (email/SMS) de confirmação e cancelamento.



