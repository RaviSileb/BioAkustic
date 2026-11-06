package com.bioakustic.app.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicClassifierTest {

    private fun tone(seconds: Int, freqHz: Double, amplitude: Float): FloatArray {
        val len = seconds * 48_000
        return FloatArray(len) { i ->
            (amplitude * kotlin.math.sin(2.0 * Math.PI * freqHz * i / 48_000)).toFloat()
        }
    }

    @Test
    fun topLabels_matchTopResultAndConfidenceRange() {
        val classifier = HeuristicClassifier()
        try {
            val result = classifier.classify(tone(3, 2000.0, 0.5f), 48_000)
            assertTrue("expected ranked candidates", result.topLabels.isNotEmpty())
            val best = result.topLabels.first()
            assertEquals(result.commonName, best.commonName)
            assertEquals(result.confidence, best.confidence, 1e-6f)
            result.topLabels.forEach {
                assertTrue("confidence ${it.confidence} in 0..1", it.confidence in 0f..1f)
            }
            val confidences = result.topLabels.map { it.confidence }
            assertEquals(confidences.sortedDescending(), confidences)
        } finally {
            classifier.close()
        }
    }

    @Test
    fun loudSignal_scoresHigherThanQuietSignal() {
        val classifier = HeuristicClassifier()
        try {
            val loud = classifier.classify(tone(3, 2000.0, 0.5f), 48_000)
            val quiet = classifier.classify(tone(3, 2000.0, 0.02f), 48_000)
            assertTrue(
                "loud ${loud.confidence} should beat quiet ${quiet.confidence}",
                loud.confidence >= quiet.confidence,
            )
        } finally {
            classifier.close()
        }
    }
}
