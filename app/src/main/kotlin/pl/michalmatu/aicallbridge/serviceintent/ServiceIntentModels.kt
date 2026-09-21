package pl.michalmatu.aicallbridge.serviceintent

enum class ServiceRouteStatus { DISCOVERED, VERIFIED }

data class ServiceDescriptor(
    val serviceId: String,
    val servicePackId: String,
    val routeStatus: ServiceRouteStatus,
    val risk: String,
) {
    init {
        require(serviceId.isNotBlank()) { "serviceId must not be blank" }
        require(servicePackId.isNotBlank()) { "servicePackId must not be blank" }
        require(risk.isNotBlank()) { "risk must not be blank" }
    }
}

class ServiceRegistry(entries: Collection<ServiceDescriptor>) {
    private val entriesById: Map<String, ServiceDescriptor>

    init {
        val copy = entries.toList()
        require(copy.map { it.serviceId }.toSet().size == copy.size) { "service ids must be unique" }
        entriesById = copy.associateBy { it.serviceId }
    }

    fun find(serviceId: String): ServiceDescriptor? = entriesById[serviceId]

    fun entriesForPack(servicePackId: String): List<ServiceDescriptor> =
        entriesById.values.filter { it.servicePackId == servicePackId }.sortedBy { it.serviceId }
}

data class ServiceIntentCandidate(
    val serviceId: String,
    val routeStatus: ServiceRouteStatus,
    val risk: String,
)

data class UserIntentModelRequest(
    val userText: String,
    val servicePackId: String,
    val generation: Long,
    val candidates: List<ServiceIntentCandidate>,
)

data class UserIntentModelOutput(
    val serviceId: String?,
    val confidence: Double,
    val generation: Long,
    val classificationMetadata: Map<String, String> = emptyMap(),
)

fun interface UserIntentClassificationModel {
    fun classify(request: UserIntentModelRequest): UserIntentModelOutput
}

data class UserIntentResolutionRequest(
    val userText: String,
    val servicePackId: String,
    val generation: Long,
)

enum class UserIntentResolutionReason {
    RESOLVED,
    UNKNOWN,
    LOW_CONFIDENCE,
    UNKNOWN_SERVICE,
    WRONG_SERVICE_PACK,
    UNSAFE_MODEL_OUTPUT,
    STALE_RESULT,
}

data class UserIntentResolution(
    val serviceId: String?,
    val confidence: Double,
    val servicePackId: String,
    val generation: Long,
    val reason: UserIntentResolutionReason,
    val classificationMetadata: Map<String, String> = emptyMap(),
)
