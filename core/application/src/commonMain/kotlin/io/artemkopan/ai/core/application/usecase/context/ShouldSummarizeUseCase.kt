package io.artemkopan.ai.core.application.usecase.context

class ShouldSummarizeUseCase {
    fun execute(messagesToCompressCount: Int, summarizeEveryK: Int): Boolean {
        if (messagesToCompressCount <= 0) return false
        if (summarizeEveryK <= 0) return false
        return messagesToCompressCount >= summarizeEveryK
    }
}
