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

Copy `.env.example` to `.env` and set `GEMINI_API_KEY`. Required variables:
- `GEMINI_API_KEY` - Gemini API key
- `GEMINI_MODEL` - Default model (defaults to `gemini-2.5-flash`)
- `CORS_ORIGIN` - CORS origin (defaults to `http://localhost:8081`)

## Architecture

This is a Clean Architecture Kotlin Multiplatform project with clear layer separation:

```
core/domain     → Entities, value objects, domain errors, repository interfaces (pure Kotlin, no dependencies)
core/application → Use cases (business logic), commands, depends only on domain
core/data       → Repository implementations, network clients (Ktor HTTP client, Gemini API)
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
- `LlmNetworkClient` interface allows swapping LLM providers (currently Gemini)
- Value objects wrap primitives in domain layer (`Prompt`, `ModelId`, `Temperature`, `MaxOutputTokens`, `StopSequences`)
- New generation parameters flow: `DTO → Command → UseCase → GenerationOptions → LlmGenerationInput → NetworkRequest → GeminiGenerationConfig`
- Do not use fully-qualified class names in code bodies (for example `io.artemkopan...AgentMessageRole`); add imports and use short names

### Backend DI

Koin-based DI via `appModules()`. Create new use cases there and wire dependencies.

### API Endpoints

- `GET /health` - Health check
- `GET /api/v1/config` - Agent config
- `POST /api/v1/generate` - Text generation (accepts `GenerateRequestDto`, returns `GenerateResponseDto`)
- `WS /api/v1/agents/ws` - Agent state sync and commands

### shared-ui Component Structure

The UI uses Compose Multiplatform with a Cyberpunk 2077-inspired theme. Code is organized into separate folders by concern:

```
shared-ui/src/commonMain/kotlin/io/artemkopan/ai/sharedui/
├── state/
│   └── AppViewModel.kt            → ViewModel, UiState, UiAction, GenerationResult, UsageResult
├── gateway/
│   └── AgentGateway.kt            → WS + HTTP gateway interface for backend communication
├── ui/
│   ├── theme/                     → Visual identity
│   │   ├── CyberpunkColors.kt     → Color palette object (Yellow, Cyan, NeonGreen, Red, backgrounds, text)
│   │   └── CyberpunkTheme.kt      → Dark color scheme, typography, CyberpunkTheme() composable
│   ├── component/                 → Reusable UI building blocks (no ViewModel references)
│   │   ├── CyberpunkPanel.kt      → Bordered card with colored header bar ([ TITLE ] format)
│   │   ├── CyberpunkTextField.kt  → Themed OutlinedTextField wrapper + cyberpunkTextFieldColors()
│   │   ├── ConfigPanel.kt         → Generation config (max tokens, stop sequences)
│   │   ├── SubmitButton.kt        → Execute/processing button with loading state
│   │   ├── StatusPanel.kt         → Loading status indicator
│   │   ├── OutputPanel.kt         → Response text, provider info, token usage
│   │   └── ErrorDialog.kt         → Error alert dialog
│   └── screen/                    → Screen-level orchestrators
│       └── AiAssistantScreen.kt   → Composes all components, connects to ViewModel
└── di/
    └── SharedUiModule.kt          → Koin module for shared-ui
```

**UI conventions:**
- `ui/theme/` — colors and theme definition; all components reference `CyberpunkColors` for consistency
- `ui/component/` — each component in its own file, accepts data/callbacks via parameters only
- `ui/screen/` — thin orchestrators that compose components and connect to ViewModel
- `CyberpunkPanel` is the standard container with accent-colored header bar
- `CyberpunkTextField` wraps `OutlinedTextField` with theme-consistent styling
- All text uses uppercase for labels and headers (cyberpunk aesthetic)
- Color roles: Yellow = primary/config, Cyan = secondary/status, NeonGreen = output/success, Red = actions/errors
- External import path: `io.artemkopan.ai.sharedui.ui.screen.AiAssistantScreen`
