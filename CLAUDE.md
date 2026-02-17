# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :backend:test
./gradlew :core:application:test
./gradlew :core:data:test

# Run backend locally (requires .env file)
./run-backend.sh
# or directly:
./gradlew :backend:run

# Run backend with Docker
docker compose --env-file .env up --build backend

# Run web app (development)
./gradlew :web-host:jsBrowserDevelopmentRun

# Build distribution
./gradlew :backend:installDist
```

## Environment Setup

Copy `.env.example` to `.env` and set `GEMINI_API_KEY`. Required variables:
- `GEMINI_API_KEY` - Gemini API key
- `GEMINI_MODEL` - Default model (defaults to `gemini-2.5-flash`)
- `CORS_ORIGIN` - CORS origin (defaults to `http://localhost:8081`)

## Architecture

This is a Clean Architecture Kotlin Multiplatform project with clear layer separation:

```
core/domain     → Entities, domain errors, repository interfaces (pure Kotlin, no dependencies)
core/application → Use cases (business logic), depends only on domain
core/data       → Repository implementations, network clients (Ktor HTTP client)
shared-contract → DTOs shared between backend and frontend (kotlinx.serialization)
shared-ui       → Compose Multiplatform UI components and state management
backend         → Ktor JVM server, HTTP routes, DI wiring
web-host        → Web app entry point (JS), wires shared-ui with HTTP gateway
```

**Dependency flow**: `backend` → `core/application` → `core/domain` ← `core/data`

### Key Conventions

- All public service/usecase/repository/client methods return `Result<T>`
- Use cases are single-responsibility classes with an `execute()` method
- Domain errors (`DomainError`) are mapped to application errors (`AppError`) in use cases
- Repository interfaces live in `core/domain`, implementations in `core/data`
- `LlmNetworkClient` interface allows swapping LLM providers (currently Gemini)

### Backend DI

Manual DI via `AppContainer` (no framework). Create new use cases there and wire dependencies.

### API Endpoints

- `GET /health` - Health check
- `POST /api/v1/generate` - Text generation (accepts `GenerateRequestDto`, returns `GenerateResponseDto`)
