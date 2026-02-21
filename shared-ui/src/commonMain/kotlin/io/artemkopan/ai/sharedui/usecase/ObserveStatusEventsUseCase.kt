package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedui.gateway.EventsClient
import io.artemkopan.ai.sharedui.gateway.WsEvent

class ObserveStatusEventsUseCase(private val eventsClient: EventsClient) {
    suspend operator fun invoke(onEvent: (WsEvent) -> Unit) = eventsClient.connect(onEvent)
}
