package pl.michalmatu.aicallbridge.realtime

import com.google.gson.JsonParser

/** Developer-defined function tool advertised to one Realtime session. */
data class RealtimeFunctionTool(
    val name: String,
    val description: String,
    val parametersJson: String,
) {
    init {
        require(NAME_PATTERN.matches(name)) { "function tool name must match ${NAME_PATTERN.pattern}" }
        require(description.isNotBlank()) { "function tool description must not be blank" }
        require(parametersJson.isNotBlank()) { "function tool parametersJson must not be blank" }
        val parsed = try {
            JsonParser.parseString(parametersJson)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("function tool parametersJson must be valid JSON", error)
        }
        require(parsed.isJsonObject) { "function tool parametersJson must be a JSON object schema" }
    }

    override fun toString(): String =
        "RealtimeFunctionTool(name=$name, description=REDACTED, parameters=REDACTED)"

    private companion object {
        val NAME_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
