package studio.ax.arrival

import android.content.Context

/** Explicitly saved one-line memo only. Application backup/transfer excludes all preferences. */
class ParkingMemoStore(context: Context) {
    private val preferences = context.getSharedPreferences("parking_memo", Context.MODE_PRIVATE)
    fun load(now: Long = System.currentTimeMillis()): ParkingMemo? {
        return try {
            val value = preferences.getString("text", null) ?: return null
            val memo = ParkingMemo(value, preferences.getLong("saved_at", 0))
            if (ParkingMemoPolicy.valid(memo, now)) memo else { clear(); null }
        } catch (_: ClassCastException) { clear(); null }
    }
    fun save(text: String, now: Long = System.currentTimeMillis()): Boolean {
        val memo = ParkingMemoPolicy.create(text, now) ?: return false
        return preferences.edit().clear().putString("text", memo.text).putLong("saved_at", memo.savedAt).commit()
    }
    fun clear(): Boolean = preferences.edit().clear().commit()
}
