package pl.michalmatu.aicallbridge.serviceintent

class ModelBackedUserIntentResolver(
    private val model: UserIntentClassificationModel,
    private val minimumConfidence: Double = 0.70,
) {
    init {
        require(minimumConfidence in 0.0..1.0) { "minimumConfidence must be between 0 and 1" }
    }

    fun resolve(request: UserIntentResolutionRequest, registry: ServiceRegistry): UserIntentResolution {
        require(request.servicePackId.isNotBlank()) { "servicePackId must not be blank" }
        if (request.userText.isBlank()) return rejected(request, UserIntentResolutionReason.UNKNOWN)

        val candidates = registry.entriesForPack(request.servicePackId).map {
            ServiceIntentCandidate(it.serviceId, it.routeStatus, it.risk)
        }
        if (candidates.isEmpty()) return rejected(request, UserIntentResolutionReason.UNKNOWN)

        val output = model.classify(
            UserIntentModelRequest(request.userText, request.servicePackId, request.generation, candidates),
        )
        if (output.generation != request.generation) {
            return rejected(request, UserIntentResolutionReason.STALE_RESULT, output.confidence)
        }
        if (containsAuthorityBearingMetadata(output.classificationMetadata)) {
            return rejected(request, UserIntentResolutionReason.UNSAFE_MODEL_OUTPUT, output.confidence)
        }
        if (!output.confidence.isFinite() || output.confidence !in 0.0..1.0 || output.confidence < minimumConfidence) {
            return rejected(request, UserIntentResolutionReason.LOW_CONFIDENCE, output.confidence)
        }

        val serviceId = output.serviceId?.trim().orEmpty()
        if (serviceId.isEmpty()) {
            return rejected(request, UserIntentResolutionReason.UNKNOWN, output.confidence)
        }
        val descriptor = registry.find(serviceId)
            ?: return rejected(request, UserIntentResolutionReason.UNKNOWN_SERVICE, output.confidence)
        if (descriptor.servicePackId != request.servicePackId) {
            return rejected(request, UserIntentResolutionReason.WRONG_SERVICE_PACK, output.confidence)
        }

        return UserIntentResolution(
            serviceId = descriptor.serviceId,
            confidence = output.confidence,
            servicePackId = request.servicePackId,
            generation = request.generation,
            reason = UserIntentResolutionReason.RESOLVED,
            classificationMetadata = output.classificationMetadata.toMap(),
        )
    }

    private fun rejected(
        request: UserIntentResolutionRequest,
        reason: UserIntentResolutionReason,
        confidence: Double = 0.0,
    ) = UserIntentResolution(null, confidence, request.servicePackId, request.generation, reason)

    private fun containsAuthorityBearingMetadata(metadata: Map<String, String>): Boolean {
        val forbidden = setOf("speech", "utterance", "tts", "action", "action_id", "actionid", "dial", "target")
        return metadata.keys.any { it.trim().lowercase() in forbidden }
    }
}
