# Acessor de Investimentos - Serviço de Perfil

Aplica o questionário, guarda as respostas e calcula o perfil do investidor
(`CONSERVATIVE`, `MODERATE` ou `AGGRESSIVE`). Quando o perfil muda, publica o
evento `profile.updated` no RabbitMQ.

## Requisitos

- Java 25
- Maven
- Docker (PostgreSQL e RabbitMQ)

## Como rodar

Tudo com Docker Compose (serviço + PostgreSQL + RabbitMQ):

```bash
cp .env.example .env
docker compose up --build
```

Ou só a infraestrutura no Docker e a aplicação local:

```bash
docker compose up -d postgres rabbitmq
mvn spring-boot:run
```

O schema do banco é criado pelas migrations do Flyway em
`src/main/resources/db/migration`.

## Autenticação (Auth0)

O serviço é um OAuth2 Resource Server: toda requisição precisa do header
`Authorization: Bearer <access token do Auth0>`. O token é validado pela
assinatura (JWKS do Auth0), pelo `issuer` (`AUTH0_ISSUER`) e pela `audience`
(`AUTH0_AUDIENCE`).

- O usuário é sempre o `sub` do token. POST/PUT não recebem `userId`.
- Nos GETs, o `{userId}` da URL precisa ser igual ao `sub`; senão a resposta é `403`.
- As permissões são lidas dos claims `permissions` (RBAC do Auth0) e `scope`.
- O CORS libera apenas `FRONTEND_URL` (aceita várias origens separadas por vírgula).

| Variável | Padrão |
| --- | --- |
| `AUTH0_ISSUER` | `https://dev-s41b58hqkvvmdu13.us.auth0.com/` |
| `AUTH0_AUDIENCE` | `https://api.acessor-investimentos` |
| `FRONTEND_URL` | `http://localhost:5173` |

No deploy, os valores podem ser sobrescritos pelas *variables* do repositório
no GitHub com esses mesmos nomes.

## Endpoints

| Método | Rota | Permissão | Descrição |
| --- | --- | --- | --- |
| POST | `/profiles/questionnaire` | `write:profile` | Responde o questionário |
| PUT | `/profiles/questionnaire` | `write:profile` | Atualiza as respostas |
| GET | `/profiles/{userId}` | `read:profile` | Perfil do investidor (só o próprio) |
| GET | `/profiles/{userId}/questionnaire` | `read:profile` | Respostas do questionário (só o próprio) |

Sem token ou com token inválido: `401`. Sem a permissão ou acessando o perfil
de outro usuário: `403`.

## Testes

```bash
mvn verify
```

- `unit/`: testes unitários dos services, publisher, controller (incluindo 401/403/CORS) e da leitura das permissões do token
- `integration/`: fluxo completo com H2 (modo PostgreSQL), RabbitMQ mockado e JWT simulado

O relatório JaCoCo é gerado em `tests/`. O build exige pelo menos 80% de
cobertura de instruções no projeto e em cada classe do pacote `service`.
