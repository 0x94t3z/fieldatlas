package xyz.fieldatlas.assets

/** The model pack to load, and a note for the user when it is not the one they selected. */
data class ModelChoice(val pack: InstalledAsset, val notice: String? = null)

/** How a model pack is named in [chooseModel]'s unfinished-load record. */
fun modelLoadKey(pack: InstalledAsset): String = "${pack.id}:${pack.version}"

/**
 * Picks the installed model to load: the selected one, unless this phone has too little memory
 * for it or the app ended while loading it, or just after, last time ([unfinishedLoad], a
 * [modelLoadKey]). Then the recommended model answers instead when it is installed, because
 * running out of memory again can freeze or restart the phone.
 */
fun chooseModel(
    models: List<InstalledAsset>,
    deviceMemoryBytes: Long,
    unfinishedLoad: String?,
    offerFor: (String) -> ModelOffer? = ModelOffers::byId,
): ModelChoice {
    val selected = models.firstOrNull { it.active } ?: models.firstOrNull()
        ?: error("Install a verified model pack first")
    val recommended = ModelOffers.recommended
    val offer = offerFor(selected.id)
    val tooLarge = offer != null && !offer.fits(deviceMemoryBytes)
    val interrupted = selected.id != recommended.id && unfinishedLoad == modelLoadKey(selected)
    if (!tooLarge && !interrupted) return ModelChoice(selected)
    val name = offer?.name ?: selected.title
    val fallback = models.firstOrNull { it.id == recommended.id && it.version == recommended.version }
        ?: models.firstOrNull { it.id == recommended.id }
    if (fallback == null) {
        check(!tooLarge) {
            "$name needs a phone with ${offer?.minimumMemoryLabel} of memory or more. " +
                "Download ${recommended.name} in Library to answer on this phone."
        }
        return ModelChoice(selected)
    }
    return ModelChoice(fallback, if (tooLarge) {
        "$name needs a phone with ${offer?.minimumMemoryLabel} of memory or more, so ${recommended.name} answers instead."
    } else {
        "Field Atlas closed while loading $name last time, so ${recommended.name} answers instead."
    })
}
