package io.artemkopan.ai.backend.agent.persistence.helper

import kotlin.math.sqrt

internal fun serializeEmbedding(values: List<Double>): String {
    return values.joinToString(separator = ",") { it.toString() }
}

internal fun parseEmbedding(raw: String): List<Double> {
    if (raw.isBlank()) return emptyList()
    return raw.split(',').mapNotNull { it.trim().toDoubleOrNull() }
}

internal fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
    if (a.size != b.size || a.isEmpty()) return 0.0

    var dot = 0.0
    var aNorm = 0.0
    var bNorm = 0.0
    for (index in a.indices) {
        val av = a[index]
        val bv = b[index]
        dot += av * bv
        aNorm += av * av
        bNorm += bv * bv
    }

    if (aNorm <= 0.0 || bNorm <= 0.0) return 0.0
    return dot / (sqrt(aNorm) * sqrt(bNorm))
}
