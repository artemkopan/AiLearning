# AiAssistant

Clean Architecture Kotlin Multiplatform project with:
- `backend` (Ktor JVM)
- `web-host` (Compose Multiplatform JS host)
- `shared-ui` (shared Compose UI for web/mobile reuse)
- shared `core` modules (`domain`, `application`, `data`)
- shared API contracts in `shared-contract`

## Architecture

- `core/domain`: entities, domain errors, repository contract.
- `core/application`: single-responsibility use cases (business logic).
- `core/data`: repository implementation + network client adapter.
- `backend`: HTTP adapter and DI wiring.
- `shared-ui`: reusable app state and Compose UI.
- `web-host`: JS host entrypoint + web network gateway implementation.

All public service/usecase/repository/client methods return `Result<T>`.

## Environment

Copy `.env.example` and set values:

```bash
cp .env.example .env
# set GEMINI_API_KEY and DB_* values in .env
```

## Run backend (Docker)

```bash
docker compose --env-file .env up --build backend
```

Backend will be available on `http://localhost:8080` and PostgreSQL on `localhost:5432`.

## Run web app (local)

```bash
./gradlew :web-host:jsBrowserDevelopmentRun
```

Default web URL is usually `http://localhost:8081`.

## Backend API

- `GET /health`
- `GET /api/v1/config`
- `POST /api/v1/generate`
- `WS /api/v1/agents/ws` (agent list/state sync + statuses)

Request example:

```json
{
  "prompt": "Write a short Kotlin tip",
  "model": "gemini-2.5-flash",
  "temperature": 0.7
}
```

## Notes

- Agents are persisted in PostgreSQL and synchronized to frontend via WebSocket snapshots.
- Generation endpoint remains available over HTTP for compatibility.
- Frontend shows validation and backend failures in an error popup.
- Current provider implementation is Gemini and can be replaced by adding another `LlmNetworkClient` + repository wiring.
