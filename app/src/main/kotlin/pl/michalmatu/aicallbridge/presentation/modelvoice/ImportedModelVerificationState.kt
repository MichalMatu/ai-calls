package pl.michalmatu.aicallbridge.presentation.modelvoice

/** One in-flight imported model check; previous per-model errors remain visible. */
internal class ImportedModelVerificationState {
    var checkingId: String? = null
        private set
    private val failed = mutableMapOf<String, String>()
    val failures: Map<String, String> get() = failed.toMap()

    fun begin(modelId: String): Boolean {
        if (checkingId != null) return false
        checkingId = modelId
        failed.remove(modelId)
        return true
    }

    /** A stale completion cannot affect the next check. */
    fun complete(modelId: String, failure: String?): Boolean {
        if (checkingId != modelId) return false
        checkingId = null
        if (failure == null) failed.remove(modelId) else failed[modelId] = failure
        return true
    }

    fun cancel() { checkingId = null }
}
