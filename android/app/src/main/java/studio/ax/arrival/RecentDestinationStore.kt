package studio.ax.arrival

import android.content.Context

/** Explicitly saved public destination ID, timestamp and dataset version only. */
class RecentDestinationStore(context: Context) {
    private val preferences = context.getSharedPreferences("recent_destination", Context.MODE_PRIVATE)
    fun load(): RecentDestination? {
        try {
        val id = preferences.getString("destination_id", null) ?: return null
        val record = RecentDestination(id, preferences.getLong("created_at", 0), preferences.getString("dataset_version", "").orEmpty())
        if (!RecentDestinationPolicy.valid(record, System.currentTimeMillis())) { clear(); return null }
        return record
        } catch (_: ClassCastException) { clear(); return null }
    }
    fun save(destinationId: String) {
        require(BriefEngine.destinations.any { it.id == destinationId })
        require(destinationId !in setOf("conferenceE", "rooms300", "rooms400"))
        preferences.edit().clear().putString("destination_id", destinationId)
            .putLong("created_at", System.currentTimeMillis())
            .putString("dataset_version", RecentDestinationPolicy.DATASET_VERSION).apply()
    }
    fun clear() { preferences.edit().clear().apply() }
}
