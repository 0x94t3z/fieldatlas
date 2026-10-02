package xyz.fieldatlas.research

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "coordinates out of range" }
    }
}

/** A saved place, its distance from the phone and the compass bearing to it (degrees from north). */
data class NearbyPlace(val evidence: Evidence, val distanceKm: Double, val bearingDegrees: Double? = null)

object GeoDistance {
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 6371.0 * 2 * asin(min(1.0, sqrt(a)))
    }

    /** Initial great-circle bearing from the first point to the second, 0–360 degrees from north. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    /** One of eight compass directions, so a list reads "350 m north-east" without a map. */
    fun compass(degrees: Double): String =
        COMPASS[(((degrees % 360.0) + 360.0) % 360.0 / 45.0 + .5).toInt() % 8]

    private val COMPASS = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")

    /** "350 m" under a kilometre, "1.2 km" under ten, whole kilometres beyond. */
    fun label(km: Double): String = when {
        km < 1.0 -> "${(km * 1000 / 10).toInt() * 10} m"
        km < 10.0 -> "%.1f km".format(java.util.Locale.ROOT, km)
        else -> "${km.toInt()} km"
    }
}
