package pl.michalmatu.aicallbridge.serviceintent

enum class ServiceIntentExecutionReason {
    ELIGIBLE,
    RESOLUTION_REJECTED,
    STALE_RESOLUTION,
    UNKNOWN_SERVICE,
    WRONG_SERVICE_PACK,
    ROUTE_NOT_VERIFIED,
    AUTHORITY_BLOCKED,
}

data class ServiceIntentExecutionEligibility(
    val eligible: Boolean,
    val reason: ServiceIntentExecutionReason,
    val serviceId: String?,
)

/**
 * Validates classification against the authoritative registry only. This does not dial, speak,
 * or replace CallTask/CallWorkflow/CallPlan/commitment/output-approval authority owners.
 */
class ServiceIntentExecutionValidator {
    fun validate(
        resolution: UserIntentResolution,
        registry: ServiceRegistry,
        expectedServicePackId: String,
        currentGeneration: Long,
        authorityAllowsExecution: Boolean,
    ): ServiceIntentExecutionEligibility {
        if (resolution.reason != UserIntentResolutionReason.RESOLVED || resolution.serviceId == null) {
            return blocked(ServiceIntentExecutionReason.RESOLUTION_REJECTED)
        }
        if (resolution.generation != currentGeneration) {
            return blocked(ServiceIntentExecutionReason.STALE_RESOLUTION)
        }
        val descriptor = registry.find(resolution.serviceId)
            ?: return blocked(ServiceIntentExecutionReason.UNKNOWN_SERVICE)
        if (resolution.servicePackId != expectedServicePackId || descriptor.servicePackId != expectedServicePackId) {
            return blocked(ServiceIntentExecutionReason.WRONG_SERVICE_PACK)
        }
        if (descriptor.routeStatus != ServiceRouteStatus.VERIFIED) {
            return blocked(ServiceIntentExecutionReason.ROUTE_NOT_VERIFIED, descriptor.serviceId)
        }
        if (!authorityAllowsExecution) {
            return blocked(ServiceIntentExecutionReason.AUTHORITY_BLOCKED, descriptor.serviceId)
        }
        return ServiceIntentExecutionEligibility(true, ServiceIntentExecutionReason.ELIGIBLE, descriptor.serviceId)
    }

    private fun blocked(reason: ServiceIntentExecutionReason, serviceId: String? = null) =
        ServiceIntentExecutionEligibility(false, reason, serviceId)
}
