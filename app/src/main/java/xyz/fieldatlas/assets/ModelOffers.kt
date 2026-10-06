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
     * Optional, from models/compact-qwen3.5-4b.example.json. It needs about 3.5 GB resident:
     * llama.cpp keeps a 2.7 GB repacked copy of the weights besides the mapped file, plus 0.58 GB
     * of context and compute buffers. A 4 GB phone rebooted running it, and a 6 GB Redmi 13C had the app killed by
     * Android just after loading it, so it is offered from 8 GB phones, which report about
     * 7,500,000,000 bytes; a 6 GB phone reports under 6,100,000,000.
     */
    val larger = ModelOffer(
        id = "qwen3.5-4b-q4-k-m",
        version = "1.0.0",
        name = "Qwen3.5 4B",
        title = "Qwen3.5 4B — Q4_K_M",
        license = "Apache-2.0",
        bytes = 3_013_027_808L,
        sha256 = "13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983",
        url = "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/4168f45a16a1290d65a4ec0fa312ae917a4c15d6/Qwen_Qwen3.5-4B-Q4_K_M.gguf",
        description = "More accurate: in our tests it made about half as many factual errors as " +
            "Qwen3.5 2B. It answers about 2.5 times slower.",
        minimumMemoryBytes = 7_000_000_000L,
        minimumMemoryLabel = "8 GB",
    )

    val all = listOf(recommended, larger)

    fun byId(id: String): ModelOffer? = all.firstOrNull { it.id == id }
}
