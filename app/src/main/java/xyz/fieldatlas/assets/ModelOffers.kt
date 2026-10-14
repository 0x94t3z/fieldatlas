package xyz.fieldatlas.assets

/** An answer model the app can download, pinned to one verified file. */
data class ModelOffer(
    val id: String,
    val version: String,
    /** Short name for setup and Library. */
    val name: String,
    /** Manifest title, as in the model's registry file under models/. */
    val title: String,
    val license: String,
    val bytes: Long,
    val sha256: String,
    val url: String,
    val description: String,
    /** Phones reporting less total memory are not offered this model and do not load it. */
    val minimumMemoryBytes: Long = 0,
    /** The memory a buyer would see on the box, for messages: "6 GB". */
    val minimumMemoryLabel: String = "",
) {
    fun fits(deviceMemoryBytes: Long): Boolean = deviceMemoryBytes >= minimumMemoryBytes
}

object ModelOffers {
    /** The default. The same pinned artifact as models/compact-qwen3.5-2b.example.json. */
    val recommended = ModelOffer(
        id = "qwen3.5-2b-q4-k-m",
        version = "1.0.0",
        name = "Qwen3.5 2B",
        title = "Qwen3.5 2B — Q4_K_M Compact",
        license = "Apache-2.0",
        bytes = 1_396_198_496L,
        sha256 = "57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8",
        url = "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/7d26695454df6de5fbcce2e58681e62dae06ce43/Qwen_Qwen3.5-2B-Q4_K_M.gguf",
        description = "The default model. It answers sooner and runs on phones with 4 GB of memory.",
    )

    /**
     * The other choice, from models/compact-gemma-4-e2b.example.json: Google's quantization-aware 4-bit
     * Gemma 4 E2B. On 180 graded development questions it made about half as many factual errors as
     * Qwen3.5 2B and did better on questions with numbers. On a Redmi 13C (6 GB) it read 14 and wrote 4.1
     * tokens a second, against about 16 and 5.3 for Qwen3.5 2B, and peaked at 2.4 GB resident. It is
     * offered from 6 GB phones (a 6 GB phone reports about 6,000,000,000 bytes) until a 4 GB phone has run it.
     */
    val gemma = ModelOffer(
        id = "gemma-4-e2b-q4-0",
        version = "1.0.0",
        name = "Gemma 4 E2B",
        title = "Gemma 4 E2B — Q4_0 QAT",
        license = "Apache-2.0",
        bytes = 3_349_516_256L,
        sha256 = "fa401b55b07ee70a54c6dae3903c783a6e65064312529ea57175cb5f8dec6634",
        url = "https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf/resolve/675cff42a74c774d6cb76f76d8eacb49b48c9b93/gemma-4-E2B_q4_0-it.gguf",
        description = "Google's small model. In our tests it made about half as many factual errors as " +
            "Qwen3.5 2B and was better with numbers. It answers a little slower and is a larger download.",
        minimumMemoryBytes = 5_500_000_000L,
        minimumMemoryLabel = "6 GB",
    )

    val all = listOf(recommended, gemma)

    fun byId(id: String): ModelOffer? = all.firstOrNull { it.id == id }
}
