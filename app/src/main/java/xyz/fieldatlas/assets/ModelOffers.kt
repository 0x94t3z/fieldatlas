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
     * Qwen3.5 2B is the only download. Gemma 4 E2B was tried as a second choice; asked 20 fresh
     * questions in the app on a Redmi 13C and graded blind, it scored 3.75 of 10 against 4.65 for
     * Qwen3.5 2B, answering "the excerpts do not contain this" where Qwen explained.
     */
    val all = listOf(recommended)

    fun byId(id: String): ModelOffer? = all.firstOrNull { it.id == id }
}
