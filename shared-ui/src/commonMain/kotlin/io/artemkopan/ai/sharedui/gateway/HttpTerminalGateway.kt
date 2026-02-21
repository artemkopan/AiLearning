package io.artemkopan.ai.sharedui.gateway

import io.artemkopan.ai.sharedcontract.ChatInfo
import io.artemkopan.ai.sharedcontract.CreateChatRequest
import io.artemkopan.ai.sharedcontract.CreateChatResponse
import io.artemkopan.ai.sharedcontract.ProjectInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class HttpTerminalGateway(private val client: HttpClient) : TerminalGateway {

    override suspend fun getProjects(): Result<List<ProjectInfo>> = runCatching {
        client.get("/api/projects").body()
    }

    override suspend fun getChats(): Result<List<ChatInfo>> = runCatching {
        client.get("/api/chats").body()
    }

    override suspend fun createChat(projectPath: String): Result<String> = runCatching {
        client.post("/api/chats") {
            contentType(ContentType.Application.Json)
            setBody(CreateChatRequest(projectPath))
        }.body<CreateChatResponse>().chatId
    }

    override suspend fun closeChat(chatId: String): Result<Unit> = runCatching {
        client.delete("/api/chats/$chatId")
    }
}
