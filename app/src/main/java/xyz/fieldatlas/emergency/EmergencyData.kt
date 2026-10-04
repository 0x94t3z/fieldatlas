package xyz.fieldatlas.emergency

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * First-aid and safety guides bundled in the APK (assets/emergency), so Research can answer an
 * emergency question from the first launch with no download, no model and no network. Guide text
 * is selected from official public-health and safety pages and shown exactly as published; see
 * tools/build_emergency_data.py for sources, pinned revisions and checksums.
 */
@Serializable
data class GuideSource(
    val publisher: String,
    val title: String,
    val url: String,
    val retrieved: String,
    val license: String,
    val revision: Long? = null,
    val sha256: String,
    val attribution: String? = null,
    @SerialName("license_url") val licenseUrl: String? = null,
)

@Serializable
data class GuideSection(
    val heading: String = "",
    val level: Int = 1,
    val numbered: Boolean = false,
    val paragraphs: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    @SerialName("step_levels") val stepLevels: List<Int> = emptyList(),
) {
    fun stepLevel(index: Int) = stepLevels.getOrElse(index) { 1 }.coerceIn(1, 4)
}

@Serializable
data class EmergencyGuide(
    val id: String,
    val title: String,
    val category: String,
    val keywords: List<String> = emptyList(),
    val urgent: Boolean = false,
    /** "US" when the source assumes US services or US species; the app says so beside it. */
    val region: String? = null,
    val sections: List<GuideSection>,
    val source: GuideSource,
) {
    /** All guide text in reading order, for search and for citation previews. */
    fun plainText(): String = sections.joinToString("\n\n") { section ->
        listOfNotNull(section.heading.takeIf(String::isNotBlank))
            .plus(section.paragraphs)
            .plus(section.steps.mapIndexed { i, step -> (if (section.numbered) "${i + 1}. " else "• ") + step })
            .joinToString("\n")
    }.trim()
}

@Serializable
data class GuideBook(val version: String, val generated: String? = null, val guides: List<EmergencyGuide>)

object EmergencyData {
    private val json = Json { ignoreUnknownKeys = true }
    fun guides(text: String): GuideBook = json.decodeFromString(GuideBook.serializer(), text)

    val categoryLabels = linkedMapOf(
        "medical" to "First aid",
        "environment" to "Heat, cold & weather",
        "survival" to "Outdoors & survival",
        "disaster" to "Disasters",
    )
}
