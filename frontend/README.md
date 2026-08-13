# frontend (BancoBilhetes)

SPA Angular 18 (standalone components, signals) que consome a API do
`ticketing-platform`.

## Requisitos

- Node.js 18+ e npm
- `ticketing-platform` a correr em `http://localhost:8080` (ver o README desse
  módulo — e por sua vez ele precisa do `payment-platform` em `:8081`)

## Correr localmente

```bash
npm install
npm start
```

Abre em `http://localhost:4200`. O `environment.ts` já aponta para
`http://localhost:8080` — muda `src/environments/environment.ts` se o backend
correr noutra porta/host.

## Build de produção

```bash
npm run build
```
Gera os ficheiros estáticos em `dist/frontend/browser`, prontos a servir por
um Nginx (ver `Dockerfile` / `docker-compose.yml` na raiz do repositório).

## Estrutura

```
src/app/
  core/
    models/         DTOs espelhados do backend (User, Event, Reservation)
    services/        AuthService (signals), EventService, ReservationService
    interceptors/     authInterceptor - injeta o Bearer token em cada pedido
    guards/           authGuard, roleGuard (por perfil: CLIENTE/ORGANIZADOR/ADMIN)
  features/
    auth/             login, registo
    events/           listagem pública, detalhe + reserva, criação (organizador)
    reservations/      histórico de reservas do cliente + cancelamento
  shared/components/
    navbar/            navegação principal, condicional por perfil
```

## Fluxo de utilização

1. **Registar/Login** — como `CLIENTE` ou `ORGANIZADOR`.
2. **Organizador**: "Criar evento" -> preenche dados + tipos de bilhete ->
   "Criar e publicar" (ou guarda como rascunho).
3. **Cliente**: navega os eventos publicados -> abre um evento -> escolhe
   quantidades por tipo de bilhete -> "Reservar e pagar" (cria a reserva e
   processa o pagamento numa unica acao, chamando o payment-platform atraves
   do ticketing-platform).
4. **Minhas reservas**: historico com o codigo de cada bilhete emitido.
   - Reservas confirmadas: botao de cancelamento (aplica a politica de
     reembolso 80%/50% definida no backend, RF5).
   - Reservas ainda pendentes de pagamento: botoes para pagar agora, alterar
     as quantidades/tipos de bilhete escolhidos, ou cancelar sem custo (nada
     foi cobrado ainda).

## Correr com Docker

Ver `docker-compose.yml` e `ARCHITECTURE.md` na raiz do monorepo
(`docker compose up --build`) — sobe o frontend (Nginx), os dois backends e
as respetivas bases de dados Postgres de uma só vez.

## Notas de arquitetura / premissas assumidas

- Autenticacao: token JWT guardado em localStorage, injetado automaticamente
  em todos os pedidos HTTP via authInterceptor. Sem refresh token - quando o
  token expira (2h por omissao no backend), o pedido falha com 401/403 e o
  utilizador tem de voltar a entrar (aceitavel para o ambito do exercicio;
  em producao seria adicionado um refresh flow).
- Estado da aplicacao gerido com Angular Signals em vez de um state
  management dedicado (NgRx, etc.) - proporcional a dimensao da aplicacao.
- Design: identidade visual propria inspirada no bilhete fisico (contorno
  perfurado, canhoto destacavel, tipografia em tres papeis distintos -
  display condensada para titulos de evento, corpo em Inter, codigos/valores
  em monospace), evitando os padroes visuais genericos mais comuns em
  interfaces geradas por IA.
