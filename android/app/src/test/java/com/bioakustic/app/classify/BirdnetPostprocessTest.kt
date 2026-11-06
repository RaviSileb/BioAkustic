package com.bioakustic.app.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class BirdnetPostprocessTest {

    @Test
    fun sigmoid_mapsLogitsToProbabilities() {
        assertEquals(0.5f, BirdnetTfliteClassifier.sigmoid(0f), 1e-6f)
        assertEquals(1.0f, BirdnetTfliteClassifier.sigmoid(95.7f), 1e-6f)
        assertEquals(0.0f, BirdnetTfliteClassifier.sigmoid(-310.8f), 1e-6f)
        // Monotonic: ranking by logits equals ranking by probabilities.
        val logits = listOf(-475.4f, -310.8f, 2.0f, 95.7f)
        val probs = logits.map { BirdnetTfliteClassifier.sigmoid(it) }
        assertEquals(probs.sorted(), probs)
    }

    @Test
    fun parseLabels_splitsScientificAndCommon() {
        val parsed = BirdnetTfliteClassifier.parseLabels(
            listOf(
                "Abroscopus albogularis_Rufous-faced Warbler",
                "Columba livia_Rock Pigeon",
                "Human vocal",
                "",
            )
        )
        assertEquals(3, parsed.size)
        assertEquals("Rufous-faced Warbler" to "Abroscopus albogularis", parsed[0])
        assertEquals("Rock Pigeon" to "Columba livia", parsed[1])
        assertEquals("Human vocal" to "", parsed[2])
    }
}
