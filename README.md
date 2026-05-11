# Hify

Hify is a lightweight internal AI Agent platform inspired by the core ideas of Dify. It is designed for local deployment, internal adoption, and secondary development by small teams of roughly 20-50 users.

The project uses a modular monolith architecture. The backend is built with Spring Boot and MyBatis-Plus, the frontend uses Vue 3, TypeScript, and Vite, and the storage layer includes MySQL, Redis, and PostgreSQL with pgvector.

Chinese documentation: [README_CN.md](README_CN.md)

## Features

| Module | Description |
|---|---|
| Account System | User login, session tokens, ADMIN / EDITOR / VIEWER roles, and API authorization |
| Model Management | Manage OpenAI, Anthropic, DeepSeek, Ollama, OpenAI-compatible providers, and model configs |
| Agent Configuration | Configure agents, system prompts, model bindings, knowledge bases, and MCP tools |
| Conversation Engine | Multi-turn chat, message history, SSE streaming responses, and function calling |
| Knowledge Base RAG | Knowledge bases, documents, chunks, vector retrieval, and context injection |
| MCP Tool Integration | Manage MCP servers and tools for agent conversations and workflows |
| Lightweight Workflow | Visual orchestration with START, LLM, CONDITION, KNOWLEDGE, API_CALL, HUMAN_REVIEW, CODE_TASK, and END nodes |
| Template Library | Create workflows from templates, save workflows as templates, version snapshots, and export templates |

## Tech Stack

Backend:

- Java 17
- Spring Boot 3.2
- MyBatis-Plus
- MySQL 8
- Redis 7
- PostgreSQL 16 + pgvector
- Flyway
- OkHttp

Frontend:

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia

Deployment:

- Docker / Docker Compose
- Kubernetes YAML templates

## Repository Structure

```text
hify
├── hify-app              # Spring Boot bootstrap module, config, migrations, integration tests
├── hify-common           # Shared config, exceptions, Result, logging, thread pools, MyBatis config
├── hify-auth             # Accounts, session tokens, and role-based authorization
├── hify-model            # Provider / ModelConfig / LLM adapters
├── hify-agent            # Agent configuration
├── hify-conversation     # Conversation engine, SSE, function calling
├── hify-knowledge        # Knowledge base, documents, pgvector repositories
├── hify-mcp              # MCP server and tool integration
├── hify-workflow         # Lightweight workflows and template library
├── hify-web              # Vue frontend
├── docker                # MySQL / PostgreSQL initialization scripts
├── k8s                   # Kubernetes deployment templates
└── docs                  # Design docs and implementation plans
```

## Requirements

Local development:

- JDK 17+
- Maven 3.9+
- Node.js 18+
- npm

Container deployment:

- Docker
- Docker Compose

## Local Development

The backend requires MySQL, Redis, and PostgreSQL with pgvector. You can start dependencies with Docker Compose, or run the full Docker Compose deployment directly.

One-command development script:

```bash
./start.sh
```

The script will:

- Build the backend Maven multi-module project
- Start the backend at `http://localhost:8080`
- Start the frontend dev server at `http://localhost:5173`
- Write logs to `logs/`

Stop services:

```bash
./stop.sh
```

## Initial Admin User

The account system is enabled by default. On first startup, if no `ADMIN` user exists, the backend creates an initial admin from environment variables:

```bash
export HIFY_INIT_ADMIN_USERNAME=admin
export HIFY_INIT_ADMIN_PASSWORD='change-me'
```

If `HIFY_INIT_ADMIN_PASSWORD` is not set, Hify will not create a weak default admin password. Test environments can disable authentication with `hify.auth.enabled=false`.

## Docker Compose Deployment

Copy and edit the environment file:

```bash
cp .env.example .env
```

The repository does not commit `.env`; it only commits `.env.example` without real secrets. Account-system variables:

```env
HIFY_INIT_ADMIN_USERNAME=admin
HIFY_INIT_ADMIN_PASSWORD=change-me
```

Other common variables:

```env
MYSQL_ROOT_PASSWORD=change-me
MYSQL_DATABASE=hify
MYSQL_USER=hify
MYSQL_PASSWORD=change-me
MYSQL_PORT=3306

REDIS_PASSWORD=
REDIS_PORT=6379

POSTGRES_USER=hify
POSTGRES_PASSWORD=change-me
POSTGRES_DB=hify_vector
POSTGRES_PORT=5433

FRONTEND_PORT=80
BACKEND_PORT=8080
SPRING_PROFILES_ACTIVE=default

OPENAI_API_KEY=
ANTHROPIC_API_KEY=
GEMINI_API_KEY=
DASHSCOPE_API_KEY=
DEEPSEEK_API_KEY=
```

Start:

```bash
docker compose up -d --build
```

Access:

- Frontend: `http://localhost`
- Backend health check: `http://localhost:8080/api/v1/health`

View logs:

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

Stop:

```bash
docker compose down
```

Remove data volumes:

```bash
docker compose down -v
```

## Kubernetes Deployment

The `k8s/` directory provides baseline deployment templates:

```text
k8s/
├── hify-backend.yaml
├── hify-frontend.yaml
├── hify-configmap-template.yaml
├── hify-secret-template.yaml
└── prometheus.yaml
```

Before deployment:

1. Build and push `hify-backend` and `hify-frontend` images to your registry.
2. Update image references in the YAML files.
3. Adjust ConfigMap and Secret values for your environment.
4. Prepare MySQL, Redis, and PostgreSQL with pgvector, or replace them with same-name services inside the cluster.

Example:

```bash
kubectl apply -f k8s/hify-secret-template.yaml
kubectl apply -f k8s/hify-configmap-template.yaml
kubectl apply -f k8s/hify-backend.yaml
kubectl apply -f k8s/hify-frontend.yaml
```

Production recommendations:

- Do not commit real secrets.
- Configure a persistent volume for uploads.
- Disable proxy buffering and configure a long read timeout for SSE endpoints.
- Use managed services or dedicated StatefulSets for MySQL, Redis, and PostgreSQL.

## Database

The primary database is MySQL. Migration scripts are located at:

```text
hify-app/src/main/resources/db/migration
```

The vector database is PostgreSQL with pgvector. Migration scripts are located at:

```text
hify-knowledge/src/main/resources/db/pgvector
```

Flyway runs migrations when the backend starts.

## Testing

Run all backend tests:

```bash
mvn test
```

Run app integration tests:

```bash
mvn -pl hify-app -am -Dtest=ProviderControllerIntegrationTest,ChatControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Integration tests use:

- The `mock` profile
- H2 in-memory database
- MockMvc
- Independent `@Sql` data
- Mocked LLM / MCP / pgvector external dependencies

Frontend build:

```bash
cd hify-web
npm run build
```

## Development Conventions

Core conventions are documented in:

- `AGENTS.md`
- `CLAUDE.md`

Key rules:

- Cross-module calls must go through the target module's `api/` interfaces.
- `web/` handles HTTP, validation, and response conversion only.
- `domain/` owns business logic and transaction boundaries.
- `infra/` owns mappers, external API clients, and infrastructure implementations.
- External LLM/MCP calls must have timeouts, circuit breakers, fallbacks, or controlled mocks.

## Security Notes

- Do not commit `.env`, real API keys, database passwords, or Kubernetes secrets.
- `application-local.yml`, logs, uploads, and build outputs are excluded by `.gitignore`.
- Docker Compose example variables are for local development only; use strong production passwords.
- High-risk operations such as Provider API keys, MCP server endpoints, and user management are restricted to `ADMIN`.
