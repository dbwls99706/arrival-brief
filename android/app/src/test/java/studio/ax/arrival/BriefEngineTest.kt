package studio.ax.arrival

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BriefEngineTest {
    @Test fun sharedWebAndAndroidDecisionCorpus() {
        val path = System.getProperty("arrival.decisionCases") ?: error("Shared decision corpus path is required")
        val lines = File(path).readLines().drop(1).filter { it.isNotBlank() }
        assertTrue("Shared corpus must contain representative cases", lines.size >= 40)
        for (line in lines) {
            val c = line.split('\t')
            assertEquals("Malformed shared case", 5, c.size)
            val result = BriefEngine.resolve(c[1], c[2], c[3].ifEmpty { null })
            assertEquals(c[0] + ": " + result.message, c[4].toBoolean(), result.status == "ready")
        }
    }

    @Test fun unboundedFloorNumbersCannotDisappear() {
        for (floor in listOf("103층", "999999999999999999층", "13F", "B3", "B3F", "지하3층", "지하3F")) {
            assertEquals(floor, "review", BriefEngine.resolve("코엑스 오디토리움 $floor", "1.65").status)
        }
    }
    @Test fun exhibitionHallAndHotelCannotBeOverriddenByMeetingRoomSelection() {
        for (text in listOf("코엑스 A홀", "코엑스 Hall A", "코엑스 전시장 B홀", "코엑스 오디토리움 및 A홀")) {
            assertEquals(text, "review", BriefEngine.resolve(text, "1.65", "auditorium").status)
        }
        for (text in listOf("COEX Grand Intercontinental Grand Ballroom", "COEX Westin Seoul Grand Ballroom", "COEX Parnas Grand Ballroom", "코엑스 B홀 1층")) {
            assertEquals(text, "review", BriefEngine.resolve(text, "1.65", "grand").status)
        }
    }

    @Test fun pastedLineBreaksCannotBypassNearbyOrChangedVenueChecks() {
        for (text in listOf("코엑스\n맞은편 오디토리움", "코엑스\n근처 그랜드볼룸", "코엑스 오디토리움 장소\n변경")) {
            assertEquals(text, "review", BriefEngine.resolve(text, "1.65").status)
        }
    }

    @Test fun uncontestedOfficialMappings() {
        val cases = listOf("오디토리움" to "B3 블루존", "그랜드볼룸" to "B3 블루존", "아셈볼룸" to "B3 블루존", "스튜디오159" to "B2 퍼플존", "THE PLATZ" to "B2 지하 2층")
        for ((name, parking) in cases) {
            val r = BriefEngine.resolve("코엑스 $name", "1.65")
            assertEquals(name, "ready", r.status)
            assertEquals(parking, r.destination?.parking)
        }
    }

    @Test fun conflictingOfficialSourcesBlockConfirmation() {
        for (room in listOf("컨퍼런스룸 E", "회의실 300호", "회의실 328호", "회의실 403호")) {
            assertEquals(room, "source_conflict", BriefEngine.resolve("코엑스 $room", "1.65").status)
        }
    }

    @Test fun sourceConflictCannotBeExported() {
        val decision = BriefEngine.resolve("코엑스 컨퍼런스룸 E", "1.65")
        assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(decision) }
    }

    @Test fun invalidAndExcessHeight() {
        assertEquals("blocked", BriefEngine.resolve("코엑스 오디토리움", "2.21").status)
        for (v in listOf("", "NaN", "Infinity", "0", "10", "165", "1e0", "1.6m", "1,65", "1.650", "-1.65")) {
            assertEquals(v, "invalid", BriefEngine.resolve("코엑스 오디토리움", v).status)
        }
    }

    @Test fun heightBoundaryWarningSurvivesExport() {
        val decision = BriefEngine.resolve("코엑스 오디토리움", "2.20")
        assertEquals("ready", decision.status)
        assertTrue(decision.message.contains("제한 높이와 같습니다"))
        assertTrue(BriefEngine.export(decision).contains(decision.message))
    }

    @Test fun excessiveHeightTakesPriorityOverSourceConflict() {
        assertEquals("blocked", BriefEngine.resolve("코엑스 컨퍼런스룸 E", "2.21").status)
    }

    @Test fun ambiguityMustBeExplicit() {
        val t = "코엑스 오디토리움과 그랜드볼룸"
        assertEquals("select", BriefEngine.resolve(t, "1.65").status)
        assertEquals("grand", BriefEngine.resolve(t, "1.65", "grand").destination?.id)
    }

    @Test fun selectionCannotBypassFloorConflict() {
        for (t in listOf("코엑스 오디토리움과 아셈볼룸 2층", "코엑스 2층", "코엑스 13층", "코엑스 13F", "코엑스 2 층", "코엑스 오디토리움 지하3층")) {
            assertEquals(t, "review", BriefEngine.resolve(t, "1.65", "auditorium").status)
        }
    }

    @Test fun invalidSelectionCannotFallBackToGuessedDestination() {
        assertEquals("invalid", BriefEngine.resolve("코엑스 오디토리움", "1.65", "invalid-id").status)
        assertEquals("conflict", BriefEngine.resolve("코엑스 아셈볼룸", "1.65", "auditorium").status)
    }

    @Test fun conflictingVenueAndFloor() {
        for (t in listOf("코엑스가 아닌 킨텍스 오디토리움", "코엑스 오디토리움 2층", "코엑스 회의실301호는 취소, 목적지는 벡스코")) {
            assertEquals("review", BriefEngine.resolve(t, "1.65").status)
        }
    }

    @Test fun unknownAndOtherFacility() {
        for (t in listOf("코엑스 마곡 오디토리움", "마곡 코엑스 오디토리움", "MAGOK COEX auditorium", "킨텍스 오디토리움")) {
            assertEquals(t, "unsupported", BriefEngine.resolve(t, "1.65").status)
        }
        assertEquals("select", BriefEngine.resolve("코엑스 2026-10-28 13:00", "1.65").status)
    }

    @Test fun outputHasSourceAndNoLoadingDock() {
        val s = BriefEngine.export(BriefEngine.resolve("코엑스 오디토리움", "1.65"))
        assertTrue(s.contains(BriefEngine.SOURCE)); assertFalse(s.contains("하역장")); assertTrue(s.contains("실시간"))
    }

    @Test fun cancelledAndFFloor() {
        for (t in listOf("코엑스 오디토리움 행사는 취소", "코엑스 오디토리움이 아니다", "코엑스 오디토리움 2F")) {
            assertEquals("review", BriefEngine.resolve(t, "1.65").status)
        }
    }
}
