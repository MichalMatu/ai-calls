package pl.michalmatu.aicallbridge.conversation

/** English-only view of BUILT-IN guidance. User-edited instructions remain verbatim.
 * Never changes persisted skill revisions, capability tags or tool permissions.
 */
internal object EnglishSkillGuidance {
    private val defaultLibrary by lazy { ConversationSkillLibrary.defaults() }
    private val standard = mapOf(
        ("ConversationStyle" to 1) to Pair("Speaking style",
            "Speak naturally, warmly and directly. Do not recite a script or greet the caller again."),
        ("ConversationStyle" to 2) to Pair("Speaking style",
            "Use a formal, courteous and precise tone. Avoid unnecessary formalities."),
        ("MessageCapture" to 1) to Pair("Taking messages",
            "Understand the caller's purpose and important facts they volunteer. Do not invent facts or promise notifications that do not exist."),
        ("Clarification" to 1) to Pair("Clarification",
            "Ask only for genuinely unclear or missing information. Apply corrections and confirmations; do not repeat answered questions."),
        ("NaturalClosure" to 1) to Pair("Closing the call",
            "Judge the end from the whole conversation. After a clear farewell, propose end_call with a goodbye; for a quotation, correction or new issue, continue. A skill never disconnects the call by itself."),
    )
    private val canonical by lazy {
        defaultLibrary.skills.flatMap { skill ->
            skill.revisions.map { revision ->
                (skill.id to revision.version) to Pair(skill.name, revision.instructions)
            }
        }.toMap()
    }

    fun skill(skill: ConversationSkill, direction: ConversationDirection): String = when (skill) {
        ConversationSkill.EXPLAIN_GOAL -> {
            val instruction = if (direction == ConversationDirection.INCOMING)
                "The app already introduced the assistant. Ask why the caller is calling without introducing yourself again."
            else "State the caller's specific purpose in your first reply. Do not act like a customer service agent."
            "- Clear call purpose: " + instruction
        }
        ConversationSkill.HANDLE_IVR ->
            "- IVR navigation: Answer menu questions briefly and specifically without acting as a customer service agent."
        ConversationSkill.ASK_PROCEDURE ->
            "- Procedure questions: If instructions are missing, ask only for the next step required for the authorized task."
        ConversationSkill.CLARIFY ->
            "- Clarification: If the other person does not understand, rephrase the purpose more simply. Do not repeat the same sentence indefinitely."
    }

    fun snapshot(snapshot: ConversationSkillSnapshot): String =
        snapshot.entries.joinToString("\n") { entry ->
            val key = entry.id to entry.version
            val defaults = canonical[key]
            val english = standard[key]
            val isFactoryText = defaults != null && english != null &&
                entry.instructions == defaults.second
            val title = when {
                entry.id == ConversationSkillLibrary.PERSONAL_INSTRUCTIONS_ID -> "Personal instructions"
                isFactoryText && entry.name == defaults?.first -> english!!.first
                else -> entry.name
            }
            val instructions = if (isFactoryText) english!!.second else entry.instructions
            "- $title (${entry.id} v${entry.version}): $instructions"
        }
}
