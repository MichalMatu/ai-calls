package pl.michalmatu.aicallbridge.conversationcore

/**
 * A user-facing task template, never an executable phone authorization.
 * A prepared draft is only an instruction proposal: Telecom, identity disclosure
 * and external effects still require their separate app-owned policies.
 */
data class ScenarioFieldDefinition(
    val id: String,
    val titlePl: String,
    val titleEn: String,
    val placeholderPl: String = "",
    val placeholderEn: String = "",
    val required: Boolean = false,
    val defaultValue: String = "",
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) { "invalid_scenario_field_id" }
        require(titlePl.isNotBlank() && titleEn.isNotBlank()) { "scenario_field_label_missing" }
    }

    fun title(language: String): String = if (language == "en") titleEn else titlePl
    fun placeholder(language: String): String =
        if (language == "en") placeholderEn else placeholderPl
}

data class ScenarioDefinition(
    val id: String,
    val titlePl: String,
    val titleEn: String,
    val goalPl: String,
    val goalEn: String,
    val fields: List<ScenarioFieldDefinition>,
    /** Null means this template currently has no qualified telephone execution adapter. */
    val callAdapterId: String? = null,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) { "invalid_scenario_id" }
        require(titlePl.isNotBlank() && titleEn.isNotBlank()) { "scenario_title_missing" }
        require(goalPl.isNotBlank() && goalEn.isNotBlank()) { "scenario_goal_missing" }
        require(fields.map { it.id }.distinct().size == fields.size) { "duplicate_scenario_field" }
    }

    fun title(language: String): String = if (language == "en") titleEn else titlePl
    fun goal(language: String): String = if (language == "en") goalEn else goalPl
    fun draft(): ScenarioDraft = ScenarioDraft(id, fields.associate { it.id to it.defaultValue })
}

/** Immutable form values; neither the template nor the draft is a call/booking grant. */
data class ScenarioDraft(val scenarioId: String, val values: Map<String, String>) {
    fun withValue(fieldId: String, value: String, definition: ScenarioDefinition): ScenarioDraft {
        require(scenarioId == definition.id) { "scenario_draft_mismatch" }
        require(definition.fields.any { it.id == fieldId }) { "unknown_scenario_field" }
        return copy(values = values + (fieldId to value))
    }

    fun missingRequiredFields(definition: ScenarioDefinition): List<String> {
        require(scenarioId == definition.id) { "scenario_draft_mismatch" }
        return definition.fields.filter { it.required && values[it.id].isNullOrBlank() }.map { it.id }
    }

    /** User-editable summary for preview/lab, not authorization or proof of an external result. */
    fun summary(definition: ScenarioDefinition, language: String = "pl"): String {
        require(scenarioId == definition.id) { "scenario_draft_mismatch" }
        val filled = definition.fields.mapNotNull { field ->
            values[field.id]?.trim()?.takeIf(String::isNotBlank)?.let { field.title(language) + ": " + it }
        }
        return (listOf(definition.goal(language)) + filled).joinToString("\n")
    }
}

/**
 * Everyday templates. CLIR is the only existing app-owned outgoing adapter.
 * All other entries remain preview/lab-only until a separately qualified
 * task executor, exact target grant, and outcome evidence are implemented.
 */
object ScenarioCatalog {
    val templates: List<ScenarioDefinition> = listOf(
        ScenarioDefinition(
            id = "clir",
            titlePl = "Zastrzeżenie numeru", titleEn = "Caller ID restriction",
            goalPl = "Zapytaj o aktywację stałego zastrzegania numeru CLIR.",
            goalEn = "Ask to enable permanent caller ID restriction (CLIR).",
            callAdapterId = "clir-enable",
            fields = listOf(
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe instrukcje",
                    "Additional instructions"),
            ),
        ),
        ScenarioDefinition(
            id = "mechanic",
            titlePl = "Mechanik samochodowy", titleEn = "Car mechanic",
            goalPl = "Umów wizytę w serwisie samochodowym i zapytaj o dostępność oraz cenę.",
            goalEn = "Arrange a car service visit and ask about availability and price.",
            fields = listOf(
                ScenarioFieldDefinition("visit_reason", "Cel wizyty", "Reason for visit",
                    required = true, defaultValue = "Diagnostyka samochodu"),
                ScenarioFieldDefinition("preferred_time", "Preferowany termin", "Preferred time",
                    required = true, defaultValue = "Przyszły tydzień, najlepiej rano"),
                ScenarioFieldDefinition("budget", "Maksymalny budżet", "Maximum budget",
                    defaultValue = "300 zł"),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe instrukcje",
                    "Additional instructions",
                    defaultValue = "Zapytaj o dostępność i cenę. Nie zatwierdzaj dodatkowych napraw."),
            ),
        ),
        ScenarioDefinition(
            id = "medical_clinic",
            titlePl = "Rejestracja w przychodni", titleEn = "Medical appointment",
            goalPl = "Zapytaj o dostępne terminy wizyty u lekarza.",
            goalEn = "Ask about available doctor's appointment times.",
            fields = listOf(
                ScenarioFieldDefinition("specialty", "Lekarz / specjalizacja",
                    "Doctor / specialty", required = true),
                ScenarioFieldDefinition("preferred_time", "Preferowany termin",
                    "Preferred time", required = true),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe instrukcje",
                    "Additional instructions"),
            ),
        ),
        ScenarioDefinition(
            id = "dentist", titlePl = "Stomatolog", titleEn = "Dentist",
            goalPl = "Zapytaj o wizytę stomatologiczną i dostępne terminy.",
            goalEn = "Ask for a dental appointment and available slots.",
            fields = listOf(
                ScenarioFieldDefinition("service", "Rodzaj wizyty", "Appointment type", required = true),
                ScenarioFieldDefinition("preferred_time", "Preferowany termin", "Preferred time"),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe instrukcje", "Additional instructions"),
            ),
        ),
        ScenarioDefinition(
            id = "hairdresser", titlePl = "Fryzjer", titleEn = "Hairdresser",
            goalPl = "Zapytaj o dostępny termin usługi fryzjerskiej.",
            goalEn = "Ask about an available hairdressing appointment.",
            fields = listOf(
                ScenarioFieldDefinition("service", "Usługa", "Service", required = true),
                ScenarioFieldDefinition("preferred_time", "Preferowany termin", "Preferred time"),
                ScenarioFieldDefinition("budget", "Budżet", "Budget"),
            ),
        ),
        ScenarioDefinition(
            id = "restaurant", titlePl = "Restauracja", titleEn = "Restaurant",
            goalPl = "Zapytaj o dostępność stolika na wybrany termin.",
            goalEn = "Ask about table availability on the requested date.",
            fields = listOf(
                ScenarioFieldDefinition("party_size", "Liczba osób", "Party size", required = true),
                ScenarioFieldDefinition("preferred_time", "Termin i godzina", "Date and time", required = true),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe wymagania", "Special requests"),
            ),
        ),
        ScenarioDefinition(
            id = "public_office", titlePl = "Urząd", titleEn = "Public office",
            goalPl = "Zapytaj o procedurę, wymagane dokumenty i dostępny termin.",
            goalEn = "Ask about procedure, required documents and available appointment times.",
            fields = listOf(
                ScenarioFieldDefinition("matter", "Rodzaj sprawy", "Matter", required = true),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe pytania", "Additional questions"),
            ),
        ),
        ScenarioDefinition(
            id = "complaint", titlePl = "Serwis i reklamacja", titleEn = "Service and complaint",
            goalPl = "Zgłoś problem i zapytaj o dalsze kroki rozpatrzenia reklamacji.",
            goalEn = "Report a problem and ask about the complaint process.",
            fields = listOf(
                ScenarioFieldDefinition("issue", "Opis problemu", "Problem description", required = true),
                ScenarioFieldDefinition("reference", "Numer sprawy (opcjonalnie)", "Reference (optional)"),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe instrukcje", "Additional instructions"),
            ),
        ),
        ScenarioDefinition(
            id = "provider", titlePl = "Dostawca usług", titleEn = "Service provider",
            goalPl = "Zapytaj o ofertę, rachunek lub szczegóły obsługi usługi.",
            goalEn = "Ask about an offer, bill or service details.",
            fields = listOf(
                ScenarioFieldDefinition("matter", "Cel kontaktu", "Reason for contact", required = true),
                ScenarioFieldDefinition("budget", "Maksymalny budżet", "Maximum budget"),
                ScenarioFieldDefinition("additional_instructions", "Dodatkowe instrukcje", "Additional instructions"),
            ),
        ),
        ScenarioDefinition(
            id = "quote", titlePl = "Zapytanie o wycenę", titleEn = "Price quote",
            goalPl = "Zapytaj o cenę, dostępność i warunki wykonania usługi.",
            goalEn = "Ask about price, availability and service terms.",
            fields = listOf(
                ScenarioFieldDefinition("service", "Przedmiot wyceny", "Requested service", required = true),
                ScenarioFieldDefinition("preferred_time", "Preferowany termin", "Preferred time"),
                ScenarioFieldDefinition("budget", "Budżet", "Budget"),
            ),
        ),
    ).also { items ->
        require(items.map { it.id }.distinct().size == items.size) { "duplicate_scenario_template" }
    }

    fun byId(id: String): ScenarioDefinition? = templates.firstOrNull { it.id == id }
}
