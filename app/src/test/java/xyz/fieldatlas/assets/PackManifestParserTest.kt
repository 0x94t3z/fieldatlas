package xyz.fieldatlas.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackManifestParserTest {
    private fun manifest(
        artifact: String = """{"path":"model.gguf","bytes":3,"sha256":"${"a".repeat(64)}"}""",
        type: String = "MODEL",
        discovery: String? = null,
    ) = """
        {
          "schemaVersion": 1,
          "id": "qwen3-1.7b-q4",
          "version": "1.0.0",
          "type": "$type",
          "title": "Qwen compact",
          "license": "Apache-2.0",
          "sourceUrls": ["https://example.invalid/model"],
          ${discovery?.let { "\"discovery\": $it," }.orEmpty()}
          "artifacts": [$artifact]
        }
    """.trimIndent()

    @Test fun parsesStrictSchemaOneManifest() {
        val parsed = PackManifestParser.parse(manifest().encodeToByteArray())
        assertEquals("qwen3-1.7b-q4", parsed.id)
        assertEquals(PackType.MODEL, parsed.type)
        assertEquals(3L, parsed.artifacts.single().bytes)
    }

    @Test fun parsesAudioPackManifest() {
        val parsed = PackManifestParser.parse(
            manifest(artifact = """{"path":"audio-model/conf/model.conf","bytes":3,"sha256":"${"a".repeat(64)}"}""", type = "AUDIO")
                .encodeToByteArray(),
        )
        assertEquals(PackType.AUDIO, parsed.type)
    }

    @Test fun rejectsDiscoveryOnAudioPack() =
        rejects(manifest(type = "AUDIO", discovery = """{"table":"t","columns":["a"]}"""))

    @Test fun rejectsUnknownRootField() = rejects(manifest().replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"extra\": true,"))
    @Test fun rejectsUnknownArtifactField() = rejects(manifest().replace("\"bytes\":3", "\"bytes\":3,\"extra\":true"))
    @Test fun rejectsUnsupportedSchema() = rejects(manifest().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
    @Test fun rejectsStringSchemaNumber() = rejects(manifest().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""))
    @Test fun rejectsUppercaseHash() = rejects(manifest().replace("a".repeat(64), "A".repeat(64)))
    @Test fun rejectsShortHash() = rejects(manifest().replace("a".repeat(64), "a".repeat(63)))
    @Test fun rejectsZeroBytes() = rejects(manifest().replace("\"bytes\":3", "\"bytes\":0"))
    @Test fun rejectsStringByteCount() = rejects(manifest().replace("\"bytes\":3", "\"bytes\":\"3\""))
    @Test fun rejectsNegativeBytes() = rejects(manifest().replace("\"bytes\":3", "\"bytes\":-1"))
    @Test fun rejectsAbsolutePath() = rejects(manifest().replace("model.gguf", "/model.gguf"))
    @Test fun rejectsTraversalPath() = rejects(manifest().replace("model.gguf", "models/../model.gguf"))
    @Test fun rejectsBackslashPath() = rejects(manifest().replace("model.gguf", "models\\\\model.gguf"))
    @Test fun rejectsBlankPathSegment() = rejects(manifest().replace("model.gguf", "models//model.gguf"))
    @Test fun rejectsBlankMetadata() = rejects(manifest().replace("Qwen compact", " "))

    private val embeddingJson =
        """{"model":"BAAI/bge-small-en-v1.5","dim":384,"quant":"int8-symmetric-per-vector",
           "normalized":true,"table":"chunk_vectors","count":708813,"rejectBelow":0.68,
           "queryPrefix":"Represent this sentence for searching relevant passages: ",
           "encoderPath":"encoder.gguf","encoderSha256":"${"c".repeat(64)}",
           "encoderSourceUrl":"https://huggingface.co/QuantFactory/bge-small-en-v1.5-GGUF"}"""

    private fun knowledgeManifest(embedding: String?) = manifest(
        artifact = """{"path":"content.sqlite","bytes":9,"sha256":"${"a".repeat(64)}"}""",
        type = "KNOWLEDGE",
        discovery = """{"coverageSummary":"s","exampleQuestions":["q"],"coverageLevel":"broad"}""",
    ).let { text ->
        if (embedding == null) text else text.replace(
            "\"artifacts\":", "\"embedding\": $embedding, \"artifacts\":",
        )
    }

    @Test fun parsesKnowledgePackWithEmbedding() {
        val parsed = PackManifestParser.parse(knowledgeManifest(embeddingJson).encodeToByteArray())
        val embedding = parsed.embedding!!
        assertEquals("BAAI/bge-small-en-v1.5", embedding.model)
        assertEquals(384, embedding.dim)
        assertEquals("chunk_vectors", embedding.table)
        assertEquals(708813L, embedding.count)
        assertEquals(0.68, embedding.rejectBelow, 1e-9)
        assertEquals("encoder.gguf", embedding.encoderPath)
    }

    @Test fun rejectsEmbeddingOnModelPack() =
        rejects(manifest().replace("\"artifacts\":", "\"embedding\": $embeddingJson, \"artifacts\":"))

    @Test fun rejectsUnknownEmbeddingField() =
        rejects(knowledgeManifest(embeddingJson.replace("\"dim\":384", "\"dim\":384,\"extra\":1")))

    @Test fun rejectsRejectBelowOutOfRange() =
        rejects(knowledgeManifest(embeddingJson.replace("\"rejectBelow\":0.68", "\"rejectBelow\":1.5")))

    @Test fun rejectsBadTableName() =
        rejects(knowledgeManifest(embeddingJson.replace("\"table\":\"chunk_vectors\"", "\"table\":\"vectors; DROP\"")))

    @Test fun rejectsDuplicateArtifactPaths() {
        val item = """{"path":"same.bin","bytes":1,"sha256":"${"b".repeat(64)}"}"""
        rejects(manifest("$item,$item"))
    }

    @Test fun acceptsBoundedKnowledgeDiscoveryAndRejectsItForModels() {
        val discovery =
            """{"coverageSummary":"Four demo science notes","exampleQuestions":["Why do seasons change?"],"coverageLevel":"demo"}"""
        val parsed = PackManifestParser.parse(
            manifest(type = "KNOWLEDGE", discovery = discovery).encodeToByteArray(),
        )
        assertEquals(CoverageLevel.DEMO, parsed.discovery?.coverageLevel)
        assertThrows(InvalidPackManifestException::class.java) {
            PackManifestParser.parse(manifest(type = "MODEL", discovery = discovery).encodeToByteArray())
        }
    }

    @Test fun rejectsDuplicateTooManyAndOversizedSuggestions() {
        rejectsDiscovery(List(7) { "Question $it" }, "Summary")
        rejectsDiscovery(listOf("Same question", "Same question"), "Summary")
        rejectsDiscovery(listOf("Question"), "x".repeat(161))
    }

    @Test fun rejectsUnknownDiscoveryFieldsAndInvalidCoverageLevel() {
        val unknown =
            """{"coverageSummary":"Summary","exampleQuestions":[],"coverageLevel":"demo","extra":true}"""
        rejects(manifest(type = "KNOWLEDGE", discovery = unknown))
        val invalid = """{"coverageSummary":"Summary","exampleQuestions":[],"coverageLevel":"unlimited"}"""
        rejects(manifest(type = "KNOWLEDGE", discovery = invalid))
    }

    private fun rejectsDiscovery(questions: List<String>, summary: String) {
        val encodedQuestions = questions.joinToString(",") { "\"$it\"" }
        rejects(
            manifest(
                type = "KNOWLEDGE",
                discovery =
                    """{"coverageSummary":"$summary","exampleQuestions":[$encodedQuestions],"coverageLevel":"demo"}""",
            ),
        )
    }

    private fun rejects(json: String) {
        assertThrows(InvalidPackManifestException::class.java) {
            PackManifestParser.parse(json.encodeToByteArray())
        }
    }
}
