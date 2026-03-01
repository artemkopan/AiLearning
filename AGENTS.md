# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :backend:test
./gradlew :core:application:jvmTest

# Run backend locally (requires .env file)
./run-backend.sh
# or directly:
./gradlew :backend:run

# Run backend with Docker
docker compose --env-file .env up --build backend

# Run web app (development)
./gradlew :web-host:jsBrowserDevelopmentRun

# Compile shared-ui (JVM + JS)
./gradlew :shared-ui:compileKotlinJvm :shared-ui:compileKotlinJs

# Build distribution
./gradlew :backend:installDist
```

## Environment Setup

Copy `.env.example` to `.env` and set `DEEPSEEK_API_KEY`. Required variables:
- `DEEPSEEK_API_KEY` - DeepSeek API key
- `DEEPSEEK_MODEL` - Default model (defaults to `deepseek-chat`)
- `DEEPSEEK_BASE_URL` - Provider base URL (defaults to `https://api.deepseek.com`)
- `CORS_ORIGIN` - CORS origin (defaults to `http://localhost:8081`)

## Architecture

This is a Clean Architecture Kotlin Multiplatform project with clear layer separation:

```
core/domain     → Entities, value objects, domain errors, repository interfaces (pure Kotlin, no dependencies)
core/application → Use cases (business logic), commands, depends only on domain
core/data       → Repository implementations, network clients (Ktor HTTP client, DeepSeek API)
shared-contract → DTOs shared between backend and frontend (kotlinx.serialization)
shared-ui       → Compose Multiplatform UI components, state management, cyberpunk theme
backend         → Ktor JVM server, HTTP routes, DI wiring (Koin)
web-host        → Web app entry point (JS), wires shared-ui with HTTP gateway
```

**Dependency flow**: `backend` → `core/application` → `core/domain` ← `core/data`

### Key Conventions

- All public service/usecase/repository/client methods return `Result<T>`
- Use cases are single-responsibility classes with an `execute()` method
- Domain errors (`DomainError`) are mapped to application errors (`AppError`) in use cases
- Repository interfaces live in `core/domain`, implementations in `core/data`
- `LlmNetworkClient` interface allows swapping LLM providers (currently DeepSeek)
- Value objects wrap primitives in domain layer (`Prompt`, `ModelId`, `Temperature`, `MaxOutputTokens`, `StopSequences`)
- New generation parameters flow: `DTO → Command → UseCase → GenerationOptions → LlmGenerationInput → NetworkRequest → Provider Request Payload`
- Do not use fully-qualified class names in code bodies (for example `io.artemkopan...AgentMessageRole`); add imports and use short names

### Backend DI

Koin-based DI via `appModules()`. Create new use cases there and wire dependencies.

### Backend Persistence (Agent Repository) Conventions

- `PostgresAgentRepository` must remain a thin facade only:
  - implement `AgentRepository`
  - delegate each `override` method to a dedicated operation class
  - do not keep SQL/business logic directly in facade methods
- Split persistence logic by file and package:
  - operations in `backend/.../agent/persistence/operation/` (one file per public repository method)
  - shared helpers in `backend/.../agent/persistence/helper/` (schema, mapping, state/meta helpers, runtime helpers)
- Use Kotlin `Lazy<T>` consistently for repository flow:
  - operation dependencies are `Lazy<...>` constructor params
  - repository method inputs are wrapped as `Lazy<T>` / `Lazy<T?>` before calling operations
  - operation `execute(...)` methods accept `Lazy` arguments and resolve via `.value` only when needed
- Use DI for repository dependencies:
  - provide runtime/helpers/operations from Koin
  - inject them into `PostgresAgentRepository` constructor (no in-class construction of runtime/helpers/operations)
  - avoid public constructor signatures that expose `internal` types; use `internal constructor` if needed
- Concurrency rule for DB runtime initialization:
  - use coroutine `Mutex` (`withLock`) in suspend initialization paths
  - do not use JVM `synchronized`/monitor locks for coroutine-based init flow
- Preserve behavioral parity during refactors:
  - keep SQL/table names and schema stable
  - keep `require(...)` messages, ordering, and version/timestamp update semantics unchanged

### Backend WS Dispatch Conventions

- WebSocket message handling must use Koin-injected dispatch map:
  - `Map<KClass<out AgentWsClientMessageDto>, AgentWsMessageUseCase<out AgentWsClientMessageDto>>`
- `AgentWsMessageHandler` must stay thin:
  - parse incoming payload (`AgentWsClientMessageDto`)
  - resolve handler by DTO `KClass`
  - dispatch to use case
  - do not reintroduce monolithic `when` with business logic in handler
- Each WS client DTO subtype must have its own backend use case class in:
  - `backend/src/main/kotlin/io/artemkopan/ai/backend/agent/ws/usecase/`
- Required WS use case contract:
  - `AgentWsMessageUseCase<T : AgentWsClientMessageDto>`
  - `val messageType: KClass<T>`
  - `suspend fun execute(context: AgentWsMessageContext, message: T): Result<Unit>`
- Keep shared runtime concerns in dedicated services:
  - `AgentWsOutboundService` for snapshot/error responses and broadcasting
  - `AgentWsProcessingRegistry` for processing job lifecycle (busy/stop/register/clear)
- Fail fast on startup if handler bindings are incomplete:
  - all sealed subclasses of `AgentWsClientMessageDto` must be present in dispatcher map
  - duplicate handler registrations for the same DTO type must fail
- When adding a new WS DTO command, update all of:
  - new `*WsUseCase` implementation
  - Koin registration in `wsModule`
  - dispatcher map coverage (startup validation must pass)
  - tests for dispatch/binding and command behavior

### Backend HTTP Routing Conventions

- HTTP/WS routes must be implemented via router handler classes, not inline in `Application.module`.
- Route handlers live in:
  - `backend/src/main/kotlin/io/artemkopan/ai/backend/http/router/`
- Required router contract:
  - `RouterHandler`
  - `fun Routing.invoke()`
- Keep one endpoint per handler class (for example health, config, metadata, stats, ws, generate).
- `Application.module` must remain thin:
  - install plugins (Koin, logging, serialization, CORS, websockets, status pages)
  - inject and compose router handlers
  - avoid endpoint business logic in module body
- Router handlers must be injected via Koin:
  - register concrete handlers in `httpModule`
  - bind each as `RouterHandler` (use `bind RouterHandler::class`)
  - expose `List<RouterHandler>` via `getAll<RouterHandler>()`
- Keep shared call-level helpers (for example request id and user scope resolution) in dedicated routing support files, not duplicated across handlers.
- For heavy dependencies that should not be eagerly created during route registration (for example WS handler), prefer `Lazy<T>` injection in router handlers.
- When adding a new endpoint, update all of:
  - new `*RouterHandler` class
  - Koin registration in `httpModule`
  - endpoint list in this document
  - tests that validate route availability/behavior

### API Endpoints

- `GET /health` - Health check
- `GET /api/v1/config` - Agent config
- `GET /api/v1/models/metadata` - Model metadata by query param `model`
- `GET /api/v1/agents/stats` - Agent stats for resolved user scope
- `POST /api/v1/generate` - Text generation (accepts `GenerateRequestDto`, returns `GenerateResponseDto`)
- `WS /api/v1/agents/ws` - Agent state sync and commands

### shared-ui Component Structure

The UI uses Compose Multiplatform with a Cyberpunk 2077-inspired theme. Code is organized into separate folders by concern:

```
shared-ui/src/commonMain/kotlin/io/artemkopan/ai/sharedui/
├── gateway/
│   └── AgentGateway.kt            → WS + HTTP gateway interface for backend communication
├── feature/
│   ├── root/view/                 → App-level screen composition (`AiAssistantScreen`)
│   ├── conversationcolumn/view/   → Conversation UI components for messaging flow
│   ├── settingscolumn/view/       → Settings/context UI components
│   ├── configpanel/view/          → Config feature UI (`ConfigPanel`, `ConfigPanelFeature`)
│   ├── agentssidepanel/view/      → Agents list side panel feature UI
│   └── errordialog/view/          → Error dialog feature UI (`ErrorDialog`, `ErrorDialogFeature`)
├── ui/
│   ├── theme/                     → Visual identity
│   │   ├── CyberpunkColors.kt     → Color palette object (Yellow, Cyan, NeonGreen, Red, backgrounds, text)
│   │   └── CyberpunkTheme.kt      → Dark color scheme, typography, CyberpunkTheme() composable
│   └── component/                 → Common reusable UI components (shared across features)
│       ├── CyberpunkPanel.kt      → Bordered card with colored header bar ([ TITLE ] format)
│       ├── CyberpunkTextField.kt  → Themed OutlinedTextField wrapper + cyberpunkTextFieldColors()
│       ├── SubmitButton.kt        → Execute/processing button with loading state
│       └── StatusPanel.kt         → Loading status indicator
├── core/session/                  → Session state models and store
├── factory/                       → ViewModel factory interfaces/implementations
├── usecase/                       → UI state mapping/transformation use cases
└── di/
    └── SharedUiModule.kt          → Koin module for shared-ui
```

**UI conventions:**
- `ui/theme/` — colors and theme definition; all components reference `CyberpunkColors` for consistency
- `feature/*/view/` — feature-owned composables and feature composition entry points
- `ui/component/` — only common reusable primitives/components shared across features
- `CyberpunkPanel` is the standard container with accent-colored header bar
- `CyberpunkTextField` wraps `OutlinedTextField` with theme-consistent styling
- All text uses uppercase for labels and headers (cyberpunk aesthetic)
- Color roles: Yellow = primary/config, Cyan = secondary/status, NeonGreen = output/success, Red = actions/errors
- External import path: `io.artemkopan.ai.sharedui.feature.root.view.AiAssistantScreen`

**Session/ViewModel conventions:**
- `AgentSessionStore` must remain thin:
  - owns `SessionState` flow
  - handles gateway runtime lifecycle (connect/disconnect, snapshot/config/model-metadata observation)
  - provides minimal state mutation primitives
  - must not contain feature/business action logic
- ViewModels must invoke dedicated shared-ui action use cases for all user-triggered mutations/commands (create/select/close agent, submit/stop, config/context updates, branch actions, etc.).
- Queue orchestration (enqueue/drain/stop/retry/error handling) must live in use-case layer, not in `AgentSessionStore`.
- ViewModels may read state directly from `AgentSessionStore` flows, but write paths must go through use cases.
- Keep `SharedUiModule` wiring explicit:
  - register feature action use cases in DI
  - inject use cases into ViewModels
  - do not inject gateway directly into feature ViewModels
