package com.jawtrack.corelogic.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Gate2RejectionPolicyTest {

    @Test
    fun `no classes above threshold passes`() {
        val classifications = listOf(Gate2Classification("Speech", 0.1f), Gate2Classification("Silence", 0.9f))

        assertEquals(Gate2Verdict.PASS, Gate2RejectionPolicy.evaluate(classifications))
    }

    @Test
    fun `an unrelated confident class passes`() {
        val classifications = listOf(Gate2Classification("Wind noise", 0.9f))

        assertEquals(Gate2Verdict.PASS, Gate2RejectionPolicy.evaluate(classifications))
    }

    @Test
    fun `speech above threshold forces MUST_DESTROY regardless of other classes`() {
        val classifications = listOf(Gate2Classification("Speech", 0.6f), Gate2Classification("Snoring", 0.9f))

        assertEquals(Gate2Verdict.MUST_DESTROY, Gate2RejectionPolicy.evaluate(classifications))
    }

    @Test
    fun `each documented reject class triggers REJECT when speech is absent`() {
        val rejectClasses = listOf("Snoring", "Breathing", "Cough", "Music", "Television", "Dog", "Vehicle")

        rejectClasses.forEach { label ->
            val verdict = Gate2RejectionPolicy.evaluate(listOf(Gate2Classification(label, 0.9f)))
            assertEquals(Gate2Verdict.REJECT, verdict, "expected REJECT for $label")
        }
    }

    @Test
    fun `a reject class below threshold does not trigger rejection`() {
        val classifications = listOf(Gate2Classification("Snoring", 0.2f))

        assertEquals(Gate2Verdict.PASS, Gate2RejectionPolicy.evaluate(classifications, threshold = 0.5f))
    }

    @Test
    fun `custom threshold is respected`() {
        val classifications = listOf(Gate2Classification("Cough", 0.4f))

        assertEquals(Gate2Verdict.REJECT, Gate2RejectionPolicy.evaluate(classifications, threshold = 0.3f))
        assertEquals(Gate2Verdict.PASS, Gate2RejectionPolicy.evaluate(classifications, threshold = 0.5f))
    }
}
