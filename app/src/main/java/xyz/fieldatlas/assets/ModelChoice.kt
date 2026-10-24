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
        // Nothing smaller to fall back to: the model is simply tried again. A note about the earlier load only
        // worried people whose phone loaded it fine the second time, and there was nothing for them to do.
        return ModelChoice(selected)
    }
    // The fallback becomes the selected model, so the note says how to go back: it stays in Library until a model is chosen.
    return ModelChoice(fallback, if (tooLarge) {
        "$name needs a phone with ${offer?.minimumMemoryLabel} of memory or more, so ${recommended.name} answers instead."
    } else {
        "Field Atlas closed while loading $name last time, so ${recommended.name} answers instead. Choose $name here to try it again."
    })
}

/**
 * Whether a model just downloaded is used from then on: when it is recommended for this phone and is larger than the one
 * in use ([activeId], null when none is), as the 4B fetched on an 8 GB phone that answered with the 2B. A smaller one,
 * kept as a fallback, or one only allowed here (the 4B on a 6 GB phone) waits in Library; a model the app does not offer
 * is never replaced.
 */
fun takesOver(offer: ModelOffer, activeId: String?, deviceMemoryBytes: Long, offerFor: (String) -> ModelOffer? = ModelOffers::byId): Boolean {
    if (!offer.fits(deviceMemoryBytes)) return false
    if (activeId == null) return true
    if (activeId == offer.id) return false
    // A model this phone is not recommended (the 4B on a 6 GB phone) waits in Library until it is chosen there.
    if (!offer.recommendedOn(deviceMemoryBytes)) return false
    val active = offerFor(activeId) ?: return false
    return offer.bytes > active.bytes
}
