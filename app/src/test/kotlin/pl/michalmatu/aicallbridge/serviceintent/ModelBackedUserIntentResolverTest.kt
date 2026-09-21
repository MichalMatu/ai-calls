package pl.michalmatu.aicallbridge.serviceintent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBackedUserIntentResolverTest {
    private val registry = ServiceRegistry(
        listOf(
            ServiceDescriptor("orange.internet.problem", "orange", ServiceRouteStatus.DISCOVERED, "AUTH_REQUIRED_POSSIBLE"),
            ServiceDescriptor("orange.invoice.status", "orange", ServiceRouteStatus.DISCOVERED, "AUTH_REQUIRED_POSSIBLE"),
            ServiceDescriptor("orange.verified.example", "orange", ServiceRouteStatus.VERIFIED, "READ_ONLY"),
            ServiceDescriptor("play.invoice.status", "play", ServiceRouteStatus.VERIFIED, "READ_ONLY"),
        ),
    )

    private fun resolver(output: UserIntentModelOutput): ModelBackedUserIntentResolver =
        ModelBackedUserIntentResolver(UserIntentClassificationModel { output }, minimumConfidence = 0.70)

    @Test
    fun `internet intent maps only to existing orange service id`() {
        val result = resolver(UserIntentModelOutput("orange.internet.problem", 0.96, 7)).resolve(
            UserIntentResolutionRequest("mam problem z internetem", "orange", 7),
            registry,
        )
        assertEquals(UserIntentResolutionReason.RESOLVED, result.reason)
        assertEquals("orange.internet.problem", result.serviceId)
    }

    @Test
    fun `invoice intent maps only to existing orange service id`() {
        val result = resolver(UserIntentModelOutput("orange.invoice.status", 0.91, 8)).resolve(
            UserIntentResolutionRequest("chcę sprawdzić fakturę", "orange", 8),
            registry,
        )
        assertEquals(UserIntentResolutionReason.RESOLVED, result.reason)
        assertEquals("orange.invoice.status", result.serviceId)
    }

    @Test
    fun `hallucinated service id is rejected`() {
        val result = resolver(UserIntentModelOutput("orange.internet.fix", 0.99, 9)).resolve(
            UserIntentResolutionRequest("napraw internet", "orange", 9),
            registry,
        )
        assertEquals(UserIntentResolutionReason.UNKNOWN_SERVICE, result.reason)
        assertNull(result.serviceId)
    }

    @Test
    fun `discovered service can classify but cannot execute`() {
        val resolution = resolver(UserIntentModelOutput("orange.internet.problem", 0.95, 10)).resolve(
            UserIntentResolutionRequest("internet nie działa", "orange", 10),
            registry,
        )
        val eligibility = ServiceIntentExecutionValidator().validate(
            resolution, registry, expectedServicePackId = "orange", currentGeneration = 10, authorityAllowsExecution = true,
        )
        assertEquals(UserIntentResolutionReason.RESOLVED, resolution.reason)
        assertFalse(eligibility.eligible)
        assertEquals(ServiceIntentExecutionReason.ROUTE_NOT_VERIFIED, eligibility.reason)
    }

    @Test
    fun `verified service can become eligible only when authority also allows`() {
        val resolution = resolver(UserIntentModelOutput("orange.verified.example", 0.95, 11)).resolve(
            UserIntentResolutionRequest("known verified service", "orange", 11),
            registry,
        )
        val validator = ServiceIntentExecutionValidator()
        assertTrue(validator.validate(resolution, registry, "orange", 11, true).eligible)
        val blocked = validator.validate(resolution, registry, "orange", 11, false)
        assertFalse(blocked.eligible)
        assertEquals(ServiceIntentExecutionReason.AUTHORITY_BLOCKED, blocked.reason)
    }

    @Test
    fun `low confidence is rejected`() {
        val result = resolver(UserIntentModelOutput("orange.internet.problem", 0.42, 12)).resolve(
            UserIntentResolutionRequest("może coś z internetem", "orange", 12), registry,
        )
        assertEquals(UserIntentResolutionReason.LOW_CONFIDENCE, result.reason)
        assertNull(result.serviceId)
    }

    @Test
    fun `unrelated prompt may resolve to null`() {
        val result = resolver(UserIntentModelOutput(null, 0.95, 13)).resolve(
            UserIntentResolutionRequest("jaka jutro pogoda", "orange", 13), registry,
        )
        assertEquals(UserIntentResolutionReason.UNKNOWN, result.reason)
        assertNull(result.serviceId)
    }

    @Test
    fun `model output carrying speech or action metadata is rejected`() {
        val result = resolver(
            UserIntentModelOutput(
                "orange.internet.problem", 0.99, 14,
                classificationMetadata = mapOf("speech" to "Powiedz cokolwiek", "action_id" to "buy"),
            ),
        ).resolve(UserIntentResolutionRequest("internet", "orange", 14), registry)
        assertEquals(UserIntentResolutionReason.UNSAFE_MODEL_OUTPUT, result.reason)
        assertNull(result.serviceId)
    }

    @Test
    fun `service from another pack cannot expand authority`() {
        val result = resolver(UserIntentModelOutput("play.invoice.status", 0.99, 15)).resolve(
            UserIntentResolutionRequest("faktura", "orange", 15), registry,
        )
        assertEquals(UserIntentResolutionReason.WRONG_SERVICE_PACK, result.reason)
        assertNull(result.serviceId)
    }

    @Test
    fun `stale model result is rejected after request generation changes`() {
        val result = resolver(UserIntentModelOutput("orange.internet.problem", 0.99, 15)).resolve(
            UserIntentResolutionRequest("internet", "orange", 16), registry,
        )
        assertEquals(UserIntentResolutionReason.STALE_RESULT, result.reason)
        assertNull(result.serviceId)
    }

    @Test
    fun `natural Orange internet demo resolves then stops at route not verified without speech`() {
        var capturedRequest: UserIntentModelRequest? = null
        val model = UserIntentClassificationModel { request ->
            capturedRequest = request
            UserIntentModelOutput("orange.internet.problem", 0.98, request.generation)
        }
        val resolution = ModelBackedUserIntentResolver(model).resolve(
            UserIntentResolutionRequest("Mam problem z internetem w Orange", "orange", 17), registry,
        )
        val eligibility = ServiceIntentExecutionValidator().validate(resolution, registry, "orange", 17, true)

        assertEquals(listOf("orange.internet.problem", "orange.invoice.status", "orange.verified.example"), capturedRequest!!.candidates.map { it.serviceId })
        assertEquals("orange.internet.problem", resolution.serviceId)
        assertEquals(ServiceIntentExecutionReason.ROUTE_NOT_VERIFIED, eligibility.reason)
        assertFalse(eligibility.eligible)
        assertTrue(resolution.classificationMetadata.isEmpty())
    }
}
