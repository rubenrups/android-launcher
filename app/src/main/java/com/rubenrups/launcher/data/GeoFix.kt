package com.rubenrups.launcher.data

/** A position with the time it was taken and how accurate it was, as saved for the parked-car card. */
data class GeoFix(val lat: Double, val lng: Double, val timeMs: Long, val accuracyM: Float) {
    fun encode(): String = "$lat,$lng,$timeMs,$accuracyM"

    companion object {
        fun decode(text: String?): GeoFix? {
            val parts = text?.split(',') ?: return null
            if (parts.size != 4) return null
            return runCatching { GeoFix(parts[0].toDouble(), parts[1].toDouble(), parts[2].toLong(), parts[3].toFloat()) }.getOrNull()
        }
    }
}
