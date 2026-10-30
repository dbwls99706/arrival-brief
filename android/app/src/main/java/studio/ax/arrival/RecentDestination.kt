package studio.ax.arrival

data class RecentDestination(val destinationId: String, val createdAt: Long, val datasetVersion: String)

object RecentDestinationPolicy {
    const val DATASET_VERSION = "coex-2026-10-30-v2"
    const val TTL_MILLIS = 24 * 60 * 60 * 1000L
    fun valid(record: RecentDestination?, now: Long): Boolean {
        if (record == null || record.datasetVersion != DATASET_VERSION) return false
        val age = now - record.createdAt
        return record.createdAt > 0 && age >= 0 && age < TTL_MILLIS &&
            BriefEngine.destinations.any { it.id == record.destinationId } &&
            record.destinationId !in setOf("conferenceE", "rooms300", "rooms400")
    }
}
