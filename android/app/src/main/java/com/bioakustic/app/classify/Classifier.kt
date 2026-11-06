package com.bioakustic.app.classify

/**
 * Species (+ optional behavior) classifier over a mono audio clip.
 * Implementations must be thread-safe for a single background thread.
 */
interface Classifier {
    val name: String
    val isReady: Boolean

    /** One ranked candidate label with confidence 0..1. */
    data class ScoredLabel(
        val commonName: String?,
        val scientificName: String?,
        val confidence: Float,
    )

    data class Result(
        val commonName: String?,
        val scientificName: String?,
        /** 0..1 */
        val confidence: Float,
        val behavior: String?,
        val behaviorConfidence: Float?,
        /** One of DetectionSource. */
        val source: String,
        /** Ranked candidates, best first (single pass with [classify]). */
        val topLabels: List<ScoredLabel> = emptyList(),
    )

    fun classify(samples: FloatArray, sampleRate: Int): Result
    fun close()
}
