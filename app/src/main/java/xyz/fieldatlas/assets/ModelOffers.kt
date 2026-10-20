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
    /** A few words beside the size in Library's list: what sets it apart. */
    val line: String = "",
    /** Setup's line on what choosing it trades, as measured: how it answers and how often it gets details wrong. */
    val tradeOff: String = "",
    /** Phones reporting less total memory are not offered this model and do not load it. */
    val minimumMemoryBytes: Long = 0,
    /** The memory a buyer would see on the box, for messages: "6 GB". */
    val minimumMemoryLabel: String = "",
    /** Tokens this model may reason for before an answer from its own knowledge; 0: it answers straight away. */
    val thinkingBudget: Int = 0,
    /**
     * Whether an ordinary question is answered from this model's own knowledge, with the passages found listed
     * under it, instead of from the passages alone. The 2B knows too little for it: on 40 sealed questions (set 37,
     * two blind rounds) it fell from 26.0% to 20.6% of web-and-AI answers, with four times the factual errors.
     */
    val knowledgeFirst: Boolean = false,
    /**
     * Whether llama.cpp copies this model's weights into its faster in-memory layouts when loading. The copy cannot be
     * reclaimed by Android: Gemma's took 5.9 GB on a 12 GB phone and got the app killed while loading; mapped from the
     * file instead it needed 0.56 GB and wrote just as fast (13.6 against 13.8 tokens a second).
     */
    val repackWeights: Boolean = true,
    /** Phones reporting less memory load this model without the repacked copy, whatever [repackWeights] says. */
    val repackMinimumMemoryBytes: Long = 0,
) {
    fun fits(deviceMemoryBytes: Long): Boolean = deviceMemoryBytes >= minimumMemoryBytes

    /** Whether this phone loads the model with llama.cpp's repacked copy of the weights. */
    fun repacksOn(deviceMemoryBytes: Long): Boolean = repackWeights && deviceMemoryBytes >= repackMinimumMemoryBytes
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
        description = "The default model. It answers from your saved passages, with citations, sooner, and runs on phones " +
            "with 4 GB of memory.",
        line = "Faster, smaller",
        tradeOff = "Answers from your saved passages, with citations. Quicker.",
    )

    /**
     * The fuller choice where it fits, from models/compact-qwen3.5-4b.example.json. Answering from its own knowledge it
     * reached 41.8% of web-and-AI answers on 40 sealed questions (set 37, two blind rounds) against 26.0% for the 2B
     * answering from the passages, but with more wrong details: 1.94 factual errors per answer against 0.71. (Citing
     * the passages, it made 0.30 against the 2B's 0.62 on sets 28 and 29; that is no longer how it answers.) With
     * llama.cpp's repacked copy of the weights it needs about 3.1 GB that
     * Android cannot reclaim, which left a 6 GB Redmi 13C 0.43 GB of free memory; mapped from the file instead it needs
     * 0.3 GB and left 3.2 GB free, reading 3.3 and writing 2.2 tokens a second (against 8.0 and 2.6), so a knowledge
     * answer takes about three minutes there. 6 GB phones report about 5,900,000,000 bytes and 4 GB phones about
     * 3,800,000,000; from 8 GB (about 7,500,000,000) there is room for the faster copy.
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
        description = "Answers more questions well, from what it knows rather than from your saved passages, but gets more " +
            "details wrong than Qwen3.5 2B: check what matters in the passages listed under each answer. Slower to answer.",
        line = "Fuller answers, slower",
        tradeOff = "Fuller answers from what it knows. More details to check.",
        minimumMemoryBytes = 5_000_000_000L,
        minimumMemoryLabel = "6 GB",
        repackMinimumMemoryBytes = 7_000_000_000L,
        // From its own knowledge: 33.7% -> 41.8% on set 37, 39.8% -> 56-58% on set 34 (two blind rounds each).
        knowledgeFirst = true,
    )

    /**
     * The most accurate choice, for phones with 12 GB of memory, from models/compact-gemma-4-26b-a4b.example.json: a
     * mixture of experts that reads about 4B of its 26B parameters for each word, so it answers about as fast as the
     * 4B while knowing far more. Answering from its own knowledge and reasoning first it scored 82.5% of web-and-AI
     * answers on 24 sealed questions (set 33, two blind rounds), against 62.0% for the 4B, with 0.23 factual errors
     * per answer. The 10.5 GB file is mapped from storage, not copied; 12 GB phones report about 11,500,000,000 bytes.
     */
    val largest = ModelOffer(
        id = "gemma-4-26b-a4b-ud-q2-k-xl",
        version = "1.0.0",
        name = "Gemma 4 26B",
        title = "Gemma 4 26B-A4B — UD-Q2_K_XL",
        license = "Apache-2.0",
        bytes = 10_546_934_240L,
        sha256 = "2a1d26dfe6ea00a467940a5728316af6edb366bbdba950d65b85d232392fb658",
        url = "https://huggingface.co/unsloth/gemma-4-26B-A4B-it-GGUF/resolve/c099eb48e663fd284577b04978a94ffccb261841/gemma-4-26B-A4B-it-UD-Q2_K_XL.gguf",
        description = "The most accurate: it knows far more and thinks before it answers. For phones with 12 GB of memory.",
        line = "Most accurate, thinks first",
        tradeOff = "Best answers and fewest wrong details in our tests.",
        minimumMemoryBytes = 10_500_000_000L,
        minimumMemoryLabel = "12 GB",
        thinkingBudget = 2048,
        knowledgeFirst = true,
        repackWeights = false,
    )

    /**
     * Qwen3.5 2B everywhere, and the 4B on phones with the memory for it. Gemma 4 E2B was tried as a second choice;
     * asked 20 fresh questions in the app on a Redmi 13C and graded blind, it scored 3.75 of 10 against 4.65 for
     * Qwen3.5 2B, answering "the excerpts do not contain this" where Qwen explained.
     */
    val all = listOf(recommended, larger, largest)

    /** What setup offers this phone, best first: the 4B where it fits, then the 2B as the faster choice. */
    fun forDevice(deviceMemoryBytes: Long): List<ModelOffer> =
        all.filter { it.fits(deviceMemoryBytes) }.sortedByDescending { it.bytes }

    fun byId(id: String): ModelOffer? = all.firstOrNull { it.id == id }
}
