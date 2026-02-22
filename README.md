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

Copy `.env.example` and set your key:

```bash
cp .env.example .env
# then set GEMINI_API_KEY in .env
```

## Run backend + web-host (Docker, single container)

```bash
docker compose --env-file .env up --build backend
```

Backend API will be available on `http://localhost:18080`.
Web app will be available on `http://localhost:18081`.

## Run web app (local)

```bash
./gradlew :web-host:jsBrowserDevelopmentRun
```

Default web URL is usually `http://localhost:8081`.

## Backend API

- `GET /health`
- `POST /api/v1/generate`

Request example:

```json
{
  "prompt": "Write a short Kotlin tip",
  "model": "gemini-2.5-flash",
  "temperature": 0.7
}
```

## Notes

- Stateless single-turn prompting.
- Frontend shows validation and backend failures in an error popup.
- Current provider implementation is Gemini and can be replaced by adding another `LlmNetworkClient` + repository wiring.
