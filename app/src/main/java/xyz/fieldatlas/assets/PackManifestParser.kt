package xyz.fieldatlas.assets

import java.text.Normalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InvalidPackManifestException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object PackManifestParser {
    private val requiredRootFields = setOf(
        "schemaVersion", "id", "version", "type", "title", "license", "sourceUrls", "artifacts",
    )
    private val allowedRootFields = requiredRootFields + "discovery"
    private val artifactFields = setOf("path", "bytes", "sha256")
    private val discoveryFields = setOf("coverageSummary", "exampleQuestions", "coverageLevel")
    private val hashPattern = Regex("[0-9a-f]{64}")
    private val identifierPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun parse(bytes: ByteArray): PackManifest = try {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        requireFields(root, requiredRootFields, allowedRootFields, "manifest")
        val schemaVersion = root.requiredInt("schemaVersion")
        require(schemaVersion == 1) { "Unsupported schemaVersion: $schemaVersion" }
        val id = root.requiredText("id")
        val version = root.requiredText("version")
        require(identifierPattern.matches(id) && id != "." && id != "..") { "Unsafe id" }
        require(identifierPattern.matches(version) && version != "." && version != "..") { "Unsafe version" }
        val type = try {
            PackType.valueOf(root.requiredText("type"))
        } catch (error: IllegalArgumentException) {
            throw InvalidPackManifestException("Unknown pack type", error)
        }
        val title = root.requiredText("title")
        val license = root.requiredText("license")
        val sourceUrls = root.requiredArray("sourceUrls").mapIndexed { index, element ->
            element.text("sourceUrls[$index]")
        }
        require(sourceUrls.isNotEmpty()) { "sourceUrls must not be empty" }
        val artifacts = root.requiredArray("artifacts").mapIndexed { index, element ->
            parseArtifact(element, index)
        }
        require(artifacts.isNotEmpty()) { "artifacts must not be empty" }
        require(artifacts.map { it.path }.toSet().size == artifacts.size) { "Duplicate artifact path" }
        val discovery = root["discovery"]?.let(::parseDiscovery)
        require(type == PackType.KNOWLEDGE || discovery == null) { "Model packs must not declare discovery" }
        PackManifest(schemaVersion, id, version, type, title, license, sourceUrls, artifacts, discovery)
    } catch (error: InvalidPackManifestException) {
        throw error
    } catch (error: Exception) {
        throw InvalidPackManifestException(error.message ?: "Invalid manifest", error)
    }

    private fun parseArtifact(element: JsonElement, index: Int): PackArtifact {
        val value = element.jsonObject
        requireFields(value, artifactFields, artifactFields, "artifacts[$index]")
        val path = value.requiredText("path")
        require(isSafeRelativePath(path)) { "Unsafe artifact path" }
        val size = value.requiredLong("bytes")
        require(size > 0) { "Artifact bytes must be positive" }
        val hash = value.requiredText("sha256")
        require(hashPattern.matches(hash)) { "sha256 must be lowercase hexadecimal" }
        return PackArtifact(path, size, hash)
    }

    private fun parseDiscovery(element: JsonElement): PackDiscovery {
        val value = element.jsonObject
        requireFields(value, discoveryFields, discoveryFields, "discovery")
        val summary = value.requiredNormalizedText("coverageSummary", MAX_COVERAGE_CHARS)
        val questions = value.requiredArray("exampleQuestions").mapIndexed { index, question ->
            question.normalizedText("exampleQuestions[$index]", MAX_QUESTION_CHARS)
        }
        require(questions.size <= MAX_EXAMPLE_QUESTIONS) { "exampleQuestions must contain at most 6 items" }
        require(questions.distinct().size == questions.size) { "exampleQuestions must be unique" }
        val level = when (value.requiredText("coverageLevel")) {
            "demo" -> CoverageLevel.DEMO
            "focused" -> CoverageLevel.FOCUSED
            "broad" -> CoverageLevel.BROAD
            else -> error("Unknown coverageLevel")
        }
        return PackDiscovery(summary, questions, level)
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
        return path.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    }

    private fun requireFields(value: JsonObject, required: Set<String>, allowed: Set<String>, label: String) {
        val missing = required - value.keys
        val unknown = value.keys - allowed
        require(missing.isEmpty()) { "$label missing fields: $missing" }
        require(unknown.isEmpty()) { "$label has unknown fields: $unknown" }
    }

    private fun JsonObject.requiredText(name: String): String = getValue(name).text(name)

    private fun JsonObject.requiredNormalizedText(name: String, maximum: Int): String =
        getValue(name).normalizedText(name, maximum)

    private fun JsonElement.text(label: String): String {
        val primitive = this as? JsonPrimitive ?: error("$label must be a string")
        require(primitive.isString) { "$label must be a string" }
        return primitive.content.also { require(it.isNotBlank()) { "$label must not be blank" } }
    }

    private fun JsonElement.normalizedText(label: String, maximum: Int): String {
        val normalized = Normalizer.normalize(text(label), Normalizer.Form.NFKC).trim()
        val visibleLength = normalized.codePointCount(0, normalized.length)
        require(visibleLength in 1..maximum) { "$label must contain 1 to $maximum characters" }
        return normalized
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val primitive = getValue(name).jsonPrimitive
        require(!primitive.isString) { "$name must be a number" }
        return primitive.content.toIntOrNull() ?: error("$name must be an integer")
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val primitive = getValue(name).jsonPrimitive
        require(!primitive.isString) { "$name must be a number" }
        return primitive.content.toLongOrNull() ?: error("$name must be an integer")
    }

    private fun JsonObject.requiredArray(name: String): JsonArray = getValue(name).jsonArray

    private const val MAX_COVERAGE_CHARS = 160
    private const val MAX_QUESTION_CHARS = 140
    private const val MAX_EXAMPLE_QUESTIONS = 6
}
