package io.artemkopan.ai.webapp.ui

import io.artemkopan.ai.sharedcontract.AgentConfigDto
import io.artemkopan.ai.sharedcontract.AgentWsClientMessageDto
import io.artemkopan.ai.sharedcontract.AgentWsServerMessageDto
import io.artemkopan.ai.sharedcontract.ModelMetadataDto
import io.artemkopan.ai.sharedui.gateway.AgentGateway
import co.touchlab.kermit.Logger
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket
import kotlin.js.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class WsAgentGateway(
    private val backendBaseUrl: String,
) : AgentGateway {

    private val log = Logger.withTag("WsAgentGateway")
    private val _events = MutableSharedFlow<AgentWsServerMessageDto>(extraBufferCapacity = 64)
    private var socket: WebSocket? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    override val events: Flow<AgentWsServerMessageDto> = _events.asSharedFlow()
    private val userScope: String = resolveUserScope()

    override suspend fun getConfig(): Result<AgentConfigDto> {
        log.d { "Fetching agent config" }
        return runCatching {
            val response = window.fetch("$backendBaseUrl/api/v1/config").await()
            val bodyText = response.text().await()

            if (!response.ok) {
                throw RuntimeException("Failed to fetch config: ${response.status}")
            }

            json.decodeFromString(AgentConfigDto.serializer(), bodyText)
        }.onFailure { throwable ->
            log.e(throwable) { "Failed to fetch config" }
        }
    }

    override suspend fun getModelMetadata(model: String): Result<ModelMetadataDto> {
        log.d { "Fetching model metadata: model=$model" }
        return runCatching {
            val normalized = model.trim()
            val response = window.fetch(
                "$backendBaseUrl/api/v1/models/metadata?model=$normalized"
            ).await()
            val bodyText = response.text().await()

            if (!response.ok) {
                throw RuntimeException("Failed to fetch model metadata: ${response.status}")
            }

            json.decodeFromString(ModelMetadataDto.serializer(), bodyText)
        }.onFailure { throwable ->
            log.e(throwable) { "Failed to fetch model metadata: model=$model" }
        }
    }

    override suspend fun connect(): Result<Unit> = runCatching {
        if (socket?.readyState == OPEN_STATE || socket?.readyState == CONNECTING_STATE) return@runCatching
        val ws = suspendCancellableCoroutine { continuation ->
            val created = WebSocket(resolveWsUrl())
            var completed = false

            created.onopen = {
                if (!completed) {
                    completed = true
                    continuation.resume(created)
                }
            }
            created.onerror = {
                if (!completed) {
                    completed = true
                    continuation.resumeWithException(RuntimeException("WebSocket connection failed"))
                }
            }
            created.onclose = {
                if (!completed) {
                    completed = true
                    continuation.resumeWithException(RuntimeException("WebSocket closed while connecting"))
                }
            }

            continuation.invokeOnCancellation {
                runCatching { created.close() }
            }
        }

        socket = ws
        ws.onmessage = { event ->
            val text = event.data?.toString()
            if (text != null) {
                runCatching {
                    json.decodeFromString(AgentWsServerMessageDto.serializer(), text)
                }.onSuccess { message ->
                    _events.tryEmit(message)
                }.onFailure { throwable ->
                    log.e(throwable) { "Failed to parse WebSocket message" }
                }
            }
        }
        ws.onclose = {
            socket = null
            log.i { "WebSocket disconnected" }
        }
        ws.onerror = {
            log.w { "WebSocket reported an error event" }
        }
        log.i { "WebSocket connected: ${resolveWsUrl()}" }
    }

    override fun disconnect() {
        socket?.close()
        socket = null
    }

    override suspend fun send(message: AgentWsClientMessageDto): Result<Unit> = runCatching {
        val ws = socket ?: throw RuntimeException("WebSocket is not connected")
        if (ws.readyState != OPEN_STATE) {
            throw RuntimeException("WebSocket is not open")
        }
        ws.send(json.encodeToString(AgentWsClientMessageDto.serializer(), message))
    }

    private fun resolveWsUrl(): String {
        val base = backendBaseUrl.removeSuffix("/")
        return when {
            base.startsWith("https://") -> base.replaceFirst("https://", "wss://")
            base.startsWith("http://") -> base.replaceFirst("http://", "ws://")
            else -> "ws://$base"
        } + "/api/v1/agents/ws?userId=$userScope"
    }

    private fun resolveUserScope(): String {
        val storageKey = "ai-learning-user-scope"
        val existing = window.localStorage.getItem(storageKey)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing

        val generated = buildString {
            append("web-")
            append((Date.now().toLong() and 0xffff).toString(16))
            append("-")
            append(Random.nextInt().toUInt().toString(16))
        }
        window.localStorage.setItem(storageKey, generated)
        return generated
    }
}

private val OPEN_STATE: Short = 1
private val CONNECTING_STATE: Short = 0
