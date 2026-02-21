package io.artemkopan.ai.sharedui.usecase

import io.artemkopan.ai.sharedcontract.StatusEvent
import io.artemkopan.ai.sharedui.gateway.EventsClient

class ObserveStatusEventsUseCase(private val eventsClient: EventsClient) {
    suspend operator fun invoke(onEvent: (StatusEvent) -> Unit) = eventsClient.connect(onEvent)
}
