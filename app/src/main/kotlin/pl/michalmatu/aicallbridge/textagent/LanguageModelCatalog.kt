package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/** Immutable model identities. Selection is an AudioEngineProfile preference, never authority. */
internal data class LanguageModelEntry(
    val id: DialogueModelId,
    val title: String,
    val detail: String,
    val spec: Gemma4ModelSpec,
    val source: Gemma4ModelAcquisitionSource,
)

internal object LanguageModelCatalog {
    const val RUNTIME_VERSION = "0.19.0"
    private fun entry(
        id: DialogueModelId, title: String, detail: String,
        spec: Gemma4ModelSpec, repository: String, revision: String,
    ) = LanguageModelEntry(id, title, detail, spec, Gemma4ModelAcquisitionSource(
        repositoryId = repository, revision = revision, fileName = spec.fileName,
        expectedSha256 = spec.sha256, expectedBytes = spec.expectedBytes,
        declaredLicense = "apache-2.0", requiresAuthentication = false,
    ))

    val entries = listOf(
        entry(DialogueModelId.GEMMA_4_E2B, "Gemma 4 E2B", "Dotychczasowy model · polski dialog",
            Gemma4ModelCatalog.GEMMA_4_E2B_IT,
            "litert-community/gemma-4-E2B-it-litert-lm", "6e5c4f1e395deb959c494953478fa5cec4b8008f"),
        entry(DialogueModelId.GEMMA_4_E4B, "Gemma 4 E4B", "Większy model · większe zużycie pamięci",
            Gemma4ModelSpec("Gemma 4 E4B IT", "gemma-4-E4B-it.litertlm",
                "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", 3659530240L),
            "litert-community/gemma-4-E4B-it-litert-lm", "e6a3892a8c22cf87ff99583996149cf3cb32452b"),
        entry(DialogueModelId.QWEN_3_1_7B, "Qwen3 1.7B", "Lekki model tekstowy · INT4",
            Gemma4ModelSpec("Qwen3 1.7B", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8", 977184032L),
            "litert-community/Qwen3-1.7B", "e3496c7708baf1efe5482c6069a1f94695469ebc"),
        entry(DialogueModelId.QWEN_3_0_6B, "Qwen3 0.6B", "Najmniejszy model · eksperymentalny · INT4",
            Gemma4ModelSpec("Qwen3 0.6B", "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
                "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0", 343490560L),
            "litert-community/Qwen3-0.6B", "f3f204cecab53892b096ebd8b3b931f8612c6bd3"),
        // G1: laboratory CPU-only candidate; not selectable for real Calls before G3.
        // Immutable Hugging Face repository revision and exact LFS blob verified by Mac Host Ops.
        entry(DialogueModelId.QWEN_3_5_2B, "Qwen3.5 2B (Lab)", "CPU · kandydat eksperymentalny",
            Gemma4ModelSpec("Qwen3.5 2B", "Qwen3.5-2B_int8.litertlm",
                "c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49", 2119843840L),
            "litert-community/Qwen3.5-2B", "f24013ba6844bd39be2d1f68e8d2421a77eed1f2"),
    )
    operator fun get(id: DialogueModelId): LanguageModelEntry = entries.single { it.id == id }
}
