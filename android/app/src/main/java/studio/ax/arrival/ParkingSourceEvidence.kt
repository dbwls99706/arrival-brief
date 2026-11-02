package studio.ax.arrival

/** Side-by-side source evidence only; unresolved values never become guidance. */
data class ParkingSourceEvidence(val title: String, val url: String, val recommendation: String, val publishedAt: String? = null)

object ParkingSourceConflicts {
    fun forDestination(id: String?): List<ParkingSourceEvidence> {
        val recommendations = when (id) {
            "conferenceE" -> "B2 퍼플존" to "B2·B3·B4 블루존"
            "rooms300", "rooms400" -> "B2 그린존" to "B2 퍼플존"
            else -> return emptyList()
        }
        return listOf(
            ParkingSourceEvidence("공식 회의실 주차안내 · 일반차량", BriefEngine.SOURCE, recommendations.first),
            ParkingSourceEvidence("공식 주차장 공지 · 회의실 주차 표", BriefEngine.CONFLICT_SOURCE, recommendations.second, "2026-02-11")
        )
    }
}
