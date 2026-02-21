package io.artemkopan.ai.backend.http

import io.artemkopan.ai.backend.config.AppConfig
import io.artemkopan.ai.backend.terminal.ChatManager
import io.artemkopan.ai.backend.terminal.EventBus
import io.artemkopan.ai.backend.terminal.PtyBridge
import io.artemkopan.ai.backend.terminal.StatusManager
import io.artemkopan.ai.sharedcontract.ChatCreatedEvent
import io.artemkopan.ai.sharedcontract.ChatInfo
import io.artemkopan.ai.sharedcontract.ChatStatus
import io.artemkopan.ai.sharedcontract.CreateChatRequest
import io.artemkopan.ai.sharedcontract.CreateChatResponse
import io.artemkopan.ai.sharedcontract.ErrorResponseDto
import io.artemkopan.ai.sharedcontract.ProjectInfo
import io.artemkopan.ai.sharedcontract.StatusUpdateRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRoutes() {
    val chatManager by inject<ChatManager>()
    val statusManager by inject<StatusManager>()
    val eventBus by inject<EventBus>()
    val ptyBridge by inject<PtyBridge>()
    val config by inject<AppConfig>()
    val logger = log
    val json = Json { explicitNulls = false }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api") {
            get("/projects") {
                val root = File(config.projectsRoot)
                val projects = if (root.isDirectory) {
                    root.listFiles()
                        ?.filter { it.isDirectory && !it.name.startsWith(".") }
                        ?.sortedBy { it.name.lowercase() }
                        ?.map { ProjectInfo(name = it.name, path = it.absolutePath) }
                        ?: emptyList()
                } else {
                    emptyList()
                }
                call.respond(projects)
            }

            post("/chats") {
                val request = call.receive<CreateChatRequest>()
                val chatId = chatManager.createChat(request.projectPath)
                val chatInfo = ChatInfo(
                    chatId = chatId,
                    projectPath = request.projectPath,
                    status = ChatStatus.idle,
                    since = java.time.Instant.now().toString(),
                )
                eventBus.broadcast(json.encodeToString(ChatCreatedEvent(chat = chatInfo)))
                call.respond(HttpStatusCode.Created, CreateChatResponse(chatId = chatId))
            }

            get("/chats") {
                val chats = chatManager.listChats()
                call.respond(chats)
            }

            post("/status") {
                val request = call.receive<StatusUpdateRequest>()
                val event = statusManager.applyUpdate(request)
                eventBus.broadcast(json.encodeToString(event))
                logger.info("Status updated for chat ${request.chatId}: ${request.status}")
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }
        }

        webSocket("/ws/{chatId}") {
            val chatId = call.parameters["chatId"]
            if (chatId == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing chatId"))
                return@webSocket
            }
            ptyBridge.handleSession(chatId, this)
        }

        webSocket("/events") {
            eventBus.register(this)
            try {
                for (frame in incoming) {
                    // Keep connection alive, ignore client messages
                }
            } finally {
                eventBus.unregister(this)
            }
        }
    }
}
