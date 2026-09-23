package pl.michalmatu.aicallbridge.textagent

/**
 * Reviewed network source metadata for candidate Gemma bytes.
 *
 * This is not model identity or activation authority. The immutable source points at one reviewed
 * upstream revision, while [Gemma4ModelInstaller] still verifies the pinned application-owned
 * model identity (size + SHA-256) before atomic activation.
 */
internal data class Gemma4ModelAcquisitionSource(
    val repositoryId: String,
    val revision: String,
    val fileName: String,
    val expectedSha256: String,
    val expectedBytes: Long?,
    val declaredLicense: String,
    val requiresAuthentication: Boolean,
) {
    init {
        require(REPOSITORY_REGEX.matches(repositoryId)) { "acquisition_repository_invalid" }
        require(REVISION_REGEX.matches(revision)) { "acquisition_revision_must_be_immutable_sha" }
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) {
            "acquisition_file_name_invalid"
        }
        require(SHA256_REGEX.matches(expectedSha256)) { "acquisition_sha256_invalid" }
        require(expectedBytes == null || expectedBytes > 0L) { "acquisition_expected_bytes_invalid" }
        require(declaredLicense.isNotBlank()) { "acquisition_license_missing" }
    }

    val downloadUrl: String
        get() = "https://huggingface.co/$repositoryId/resolve/$revision/$fileName?download=true"

    private companion object {
        val REPOSITORY_REGEX = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        val REVISION_REGEX = Regex("[0-9a-f]{40}")
        val SHA256_REGEX = Regex("[0-9a-f]{64}")
    }
}

internal object Gemma4ModelAcquisitionCatalog {
    private const val LITERT_COMMUNITY_REPOSITORY =
        "litert-community/gemma-4-E2B-it-litert-lm"
    private const val REVIEWED_REVISION =
        "6e5c4f1e395deb959c494953478fa5cec4b8008f"

    val GEMMA_4_E2B_IT: Gemma4ModelAcquisitionSource =
        Gemma4ModelCatalog.GEMMA_4_E2B_IT.let { model ->
            Gemma4ModelAcquisitionSource(
                repositoryId = LITERT_COMMUNITY_REPOSITORY,
                revision = REVIEWED_REVISION,
                fileName = model.fileName,
                expectedSha256 = model.sha256,
                expectedBytes = model.expectedBytes,
                declaredLicense = "apache-2.0",
                requiresAuthentication = false,
            )
        }
}
