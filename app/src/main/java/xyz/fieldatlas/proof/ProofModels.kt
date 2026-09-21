package xyz.fieldatlas.proof

enum class ProofSection { OfflineContract, Device, LatestRun }

enum class ProofOrigin { ManifestAudit, OsReport, MeasuredQuery }

enum class ProofState { Pass, Info, Missing, Warning }

data class ProofFact(
    val label: String,
    val value: String,
    val origin: ProofOrigin,
    val state: ProofState,
)

data class ProofModel(
    val offline: List<ProofFact>,
    val device: List<ProofFact>,
    val latestRun: List<ProofFact>,
)
