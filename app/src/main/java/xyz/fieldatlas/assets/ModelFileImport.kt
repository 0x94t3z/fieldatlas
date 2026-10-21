package xyz.fieldatlas.assets

/**
 * A model file (.gguf) chosen for import, as its size, its name and the phone's memory make it out to be. People
 * download a model as the file its publisher offers, not as a .fapack, so this is how a phone that cannot download in
 * the app gets one.
 */
sealed interface ModelFileKind {
    /** Exactly the size of a model Field Atlas tested: installed as that model once its SHA-256 matches too. */
    data class Tested(val offer: ModelOffer) : ModelFileKind

    /** Named like a tested model but shorter than it: a download that stopped early. */
    data class Incomplete(val offer: ModelOffer, val bytes: Long) : ModelFileKind

    /** Any other model: installed as the user's own, with the settings of a model the app has not measured. */
    data class Own(val bytes: Long) : ModelFileKind

    /**
     * A model this phone cannot hold in memory: [neededBytes] for the user's own, or the [offer]'s minimum memory for a
     * tested one. Android closes an app that loads it, so it is refused before it is copied.
     */
    data class TooBig(val bytes: Long, val neededBytes: Long, val offer: ModelOffer? = null) : ModelFileKind
}

/** A model file the app will not install: a short [headline], and the sentence that says why and what to do. */
class ModelFileRefusal(val headline: String, message: String) : Exception(message)

/** What import knows about a chosen model file before copying it. */
data class ModelFilePlan(val kind: ModelFileKind, val fileName: String?, val bytes: Long, val title: String, val license: String?)

object ModelFileImport {
    private val MAGIC = "GGUF".toByteArray(Charsets.US_ASCII)

    /** Memory a model needs beyond its weights: the context cache, the runtime and the app around it. */
    const val RUNTIME_BYTES = 1_000_000_000L

    /** The share of the phone's memory a model the app has not measured may take; Android and other apps keep the rest. */
    const val MEMORY_SHARE = 0.8

    /** A GGUF file opens with its magic bytes; anything else is left to the pack importer. */
    fun isGguf(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** The name a tested model's file has where its publisher offers it, as the model guide links it. */
    fun publishedName(offer: ModelOffer): String = offer.url.substringAfterLast('/').substringBefore('?')

    fun neededBytes(bytes: Long): Long = bytes + RUNTIME_BYTES

    fun classify(name: String?, bytes: Long, memoryBytes: Long, offers: List<ModelOffer> = ModelOffers.all): ModelFileKind {
        offers.firstOrNull { it.bytes == bytes }?.let { offer ->
            return if (memoryBytes <= 0 || offer.fits(memoryBytes)) ModelFileKind.Tested(offer)
                else ModelFileKind.TooBig(bytes, offer.minimumMemoryBytes, offer)
        }
        val named = name?.let { file -> offers.firstOrNull { publishedName(it).equals(file.trim(), ignoreCase = true) } }
        if (named != null && bytes < named.bytes) return ModelFileKind.Incomplete(named, bytes)
        val needed = neededBytes(bytes)
        if (memoryBytes > 0 && needed > memoryBytes * MEMORY_SHARE) return ModelFileKind.TooBig(bytes, needed)
        return ModelFileKind.Own(bytes)
    }

    /** An id for the user's own model, the same for the same file: "own-" and the start of its SHA-256. */
    fun ownId(sha256: String): String = "own-" + sha256.lowercase().take(12)

    fun isOwn(id: String): Boolean = id.startsWith("own-")

    /** The model's own name from its header, else the file name without its extension. */
    fun ownTitle(headerName: String?, fileName: String?): String =
        headerName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
            ?: fileName?.substringBeforeLast(".gguf")?.substringBeforeLast(".GGUF")?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
            ?: "Your own model"

    /** Whole decimal gigabytes, for an estimate that only says "about". */
    fun wholeGb(bytes: Long): String = "${kotlin.math.round(bytes / 1e9).toLong()} GB"

    /** Decimal gigabytes, as phones and download pages state sizes. */
    fun gb(bytes: Long): String = "%.2f GB".format(java.util.Locale.ROOT, bytes / 1e9)

    /**
     * A phone's memory as it is sold: the smallest usual size, in binary gigabytes, that holds what Android reports. The
     * kernel keeps part of the memory out of the total, so 12.53 billion bytes reported is a 12 GB phone, and 6.01 a 6 GB one.
     */
    fun phoneMemory(bytes: Long): String {
        val gib = bytes / (1L shl 30).toDouble()
        val size = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32).firstOrNull { it >= gib - 0.05 } ?: kotlin.math.ceil(gib).toInt()
        return "$size GB"
    }

    /** Why [kind] is not installed, or null when it is installed. */
    fun refusal(kind: ModelFileKind, memoryBytes: Long): ModelFileRefusal? = when (kind) {
        is ModelFileKind.Incomplete -> ModelFileRefusal("Incomplete download",
            "It has ${gb(kind.bytes).removeSuffix(" GB")} of the ${gb(kind.offer.bytes)} in the published ${kind.offer.name} file. Download it again, then import it.")
        is ModelFileKind.TooBig -> kind.offer?.let {
            ModelFileRefusal("Too big for this phone", "${it.name} needs a phone with ${it.minimumMemoryLabel} of memory; this one has ${phoneMemory(memoryBytes)}.")
        } ?: ModelFileRefusal("Too big for this phone",
            "It needs about ${wholeGb(kind.neededBytes)} of memory; a ${phoneMemory(memoryBytes)} phone can give one app about " +
                "${wholeGb((memoryBytes * MEMORY_SHARE).toLong())}.")
        else -> null
    }

    /**
     * Why a model the app has not tested can't be used, from its header: none that can be read (a damaged file, or one
     * that only starts like a model), no weights, or no chat format, which an embedding model such as a search encoder
     * lacks. Null when it can answer questions. Checked before copying, so a file that would not load is never copied.
     */
    fun ownModelProblem(headerRead: Boolean, tensorCount: Long?, chatTemplate: String?): ModelFileRefusal? = when {
        !headerRead || tensorCount == null || tensorCount <= 0 ->
            ModelFileRefusal("Not a model file", "Its header is damaged, or it isn't a model. Choose a .gguf model file.")
        chatTemplate.isNullOrBlank() ->
            ModelFileRefusal("Not a chat model", "It can't answer questions; it may be a search model. Choose a chat model.")
        else -> null
    }

    fun mismatch(offer: ModelOffer): ModelFileRefusal = ModelFileRefusal("Not the published file",
        "It's the size of the published ${offer.name} file, but its contents differ. Download it again, then import it.")
}
