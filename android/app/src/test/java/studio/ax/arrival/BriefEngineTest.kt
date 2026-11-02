package studio.ax.arrival

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BriefEngineTest {
    @Test fun subjectlessCompletionKeepsOriginalMeaning() {
        for(note in listOf("종료되었습니다.", "일정이 끝났습니다.", "모두 마쳤습니다.", "It has ended.", "It is over.", "It’s over.", "This is over.", "Already completed.", "Now finished.", "Concluded yesterday."))for(selected in listOf(null,"auditorium")) {
            val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
            assertEquals(note,"review",d.status)
            assertTrue(note,d.eventNotes.contains(note))
            assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
        }
    }

    @Test fun eventLifecycleNeedsReviewEvenWithKnownRoom() {
        for(note in listOf("행사가 종료되었습니다.","이번 행사는 이미 끝났습니다.","오늘 공연은 모두 마쳤습니다.","The event has ended.","The conference is over.","The meeting has concluded.","행사는 18시에 종료 예정입니다.","The event ends at 18:00."))for(selected in listOf(null,"auditorium")) {
            val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
            assertEquals(note,"review",d.status)
            assertTrue(note,d.eventNotes.contains(note))
            assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
        }
    }

    @Test fun eventNegationDoesNotAuthorizePhysicalVisits() {
        for(note in listOf("오늘 행사는 안 합니다.","이번 행사는 하지 않습니다.","오늘 공연은 안 해요.","이번 회의는 못 합니다.","강연은 하지 않아요.","This event is not happening.","There is no event today.","The conference won't take place.","Today's meeting isn't happening.","No concert today.","This seminar does not happen today."))for(selected in listOf(null,"auditorium")) {
            val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
            assertEquals(note,"review",d.status)
            assertTrue(note,d.eventNotes.contains(note))
            assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
        }
    }

    @Test fun eventStateAndRemoteFormatDoNotAuthorizePhysicalVisits() {
        for(note in listOf("행사가 무산되었습니다.","행사는 비대면으로 개최됩니다.","온라인으로만 진행합니다.","Zoom으로만 진행합니다.","금일은 행사가 없습니다.","행사가 불성립되었습니다.","원격 행사로 진행합니다.","하이브리드 행사로 진행합니다."))for(selected in listOf(null,"auditorium")) {
            val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
            assertEquals(note,"review",d.status)
            assertTrue(note,d.eventNotes.contains(note))
            assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
        }
    }
    @Test fun ordinaryDatesTicketsAndEventDescriptionsStayUsable() {
        for(note in listOf("행사 일시: 2026년 11월 7일 14:00","입장권은 현장에서 확인합니다. Admission is free.","행사 소개: 디자인과 미래 모빌리티를 다루는 강연입니다."))assertEquals(note,"ready",BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65").status)
    }
    @Test fun facilityOperatingStatusIsPreservedAndHeldRegardlessOfPolarity() {
        for(note in listOf("금일 휴관합니다.","행사장 운영을 중단합니다.","행사는 중단되었습니다.","시설은 휴업 중입니다.","오늘 행사장 문을 닫습니다.","금일은 휴장입니다.","시설은 휴무입니다.","행사장이 폐관했습니다.","오늘은 쉬는 날입니다.","이번 행사는 비개최입니다.","시설은 정상 운영합니다.","내일 영업을 재개합니다.","오늘 행사장 문을 엽니다."))for(selected in listOf(null,"auditorium")) {
            val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
            assertEquals(note,"review",d.status)
            assertTrue(note,d.facilityNotes.contains(note))
            assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
        }
    }
    @Test fun ordinaryIntroductionsRemainDistinctFromFacilityStatus() {
        for(text in listOf("코엑스 오디토리움 3층에서 열리는 디자인 컨퍼런스입니다.","코엑스 오디토리움 3층\n행사 소개: 미래 모빌리티 기술 강연입니다.","코엑스 오디토리움 3층\n주차는 B3 블루존을 이용해 주세요."))assertEquals(text,"ready",BriefEngine.resolve(text,"1.65").status)
        for(text in listOf("코엑스 오디토리움에서 행사를 열지 않습니다.","코엑스 오디토리움 행사 취소"))assertEquals(text,"review",BriefEngine.resolve(text,"1.65").status)
    }
    @Test fun unlabelledRoomsCannotBorrowFacilityFromSponsorship() {
        for(text in listOf("연세대학교 백양누리 그랜드볼룸\n후원: 코엑스","행사 안내\n연세대학교 백양누리 그랜드볼룸에서 만나요.\n후원: 코엑스","코엑스 후원 연세대학교 백양누리 그랜드볼룸","후원: 코엑스 그랜드볼룸"))for(selected in listOf(null,"grand"))assertEquals(text,"review",BriefEngine.resolve(text,"1.65",selected).status)
    }
    @Test fun explicitNonOccurrenceAndInabilityToVisitBlockMatchingNames() {
        for(text in listOf("코엑스 오디토리움에서는 행사를 진행하지 않습니다.","코엑스 오디토리움에서는 행사를 개최하지 않습니다.","코엑스 그랜드볼룸 1층\n엘리베이터 공사로 행사장 방문이 불가능합니다.","코엑스 그랜드볼룸 1층\n행사장을 방문할 수 없습니다.","COEX grand ballroom 1F. Visitors cannot visit the venue."))assertEquals(text,"review",BriefEngine.resolve(text,"1.65").status)
        assertEquals("ready",BriefEngine.resolve("코엑스 오디토리움 3층에서 열리는 디자인 컨퍼런스입니다.","1.65").status)
    }
    @Test fun unparsedParkingAndDoorWordsAreHeldAnywhereInAClause() {
        for(note in listOf("오늘은 주차장을 이용할 수 없습니다.","오늘은 주차할 수 없습니다.","금일 입구를 이용할 수 없습니다.","오늘 입구를 막습니다.","정문 이용 불가","후문은 사용하지 마세요.","오늘 출구를 사용할 수 없습니다.","옆문은 안내에 따라 이용하세요.","쪽문을 사용해주세요.","대문으로 오세요.","Today parking is unavailable.","The exit cannot be used today.","The car park is unavailable.")) {
            for(selected in listOf(null,"auditorium")) {
                val d=BriefEngine.resolve("코엑스 오디토리움\n$note","1.65",selected)
                assertEquals(note,"review",d.status)
                assertTrue(note,(d.unparsedParkingNotes+d.entryNotes).contains(note))
            }
        }
    }
    @Test fun venueDeclarationsRequireTheirOwnFacilityAndNoUnknownRemainder() {
        for(text in listOf("장소: 연세대학교 백양누리 그랜드볼룸\n후원: 코엑스","장소: 그랜드볼룸\n후원: 코엑스","장소: 코엑스 연세대학교 백양누리 그랜드볼룸 1층","Venue: Yonsei University Grand Ballroom 1F. Sponsor: COEX.","장소: 코엑스 그랜드볼룸 1층 별관"))for(selected in listOf(null,"grand"))assertEquals(text,"review",BriefEngine.resolve(text,"1.65",selected).status)
        for(text in listOf("장소: 코엑스 그랜드볼룸 1층입니다.","장소: 코엑스 삼성동 오디토리움 (3층)","장소: 코엑스 오디토리움 3층\n후원: 협력사"))assertEquals(text,"ready",BriefEngine.resolve(text,"1.65").status)
    }
    @Test fun venueDeclarationsAreCheckedBeyondTheFormerLengthBound() {
        val text="장소: 코엑스 그랜드볼룸 1층"+" ".repeat(180)+"연세대학교 별관"
        assertEquals("review",BriefEngine.resolve(text,"1.65","grand").status)
    }
    @Test fun unparsedAccessOperationsAreHeldRegardlessOfPermissionGrammar() {
        for(note in listOf("금일은 차량 진입이 불가합니다.","입차 불가","오늘은 차량 출입을 금합니다.","차량 출입을 삼가주세요.","출차가 허용되지 않습니다.","통행을 제한합니다.","출입문 운영이 중단되었습니다.","Vehicle access unavailable.","Entrance is not permitted.","Entry prohibited today.","Drive-in is not allowed.","The gate is unavailable.")) {
            for(selected in listOf(null,"auditorium")) {
                val d=BriefEngine.resolve("코엑스 오디토리움 3층 행사\n$note","1.65",selected)
                assertEquals(note,"review",d.status)
                assertTrue(note,d.entryNotes.contains(note))
            }
        }
    }
    @Test fun exactHeightFactsAndOrdinaryAdmissionRemainUsable() {
        for(note in listOf("시설 진입 제한 높이: 2.2m.","주차장 진입 제한 높이 2.20미터입니다.","Parking entrance height limit: 2.2m.","Admission opens at 09:00. 행사 주제는 미래 모빌리티입니다."))assertEquals(note,"ready",BriefEngine.resolve("코엑스 오디토리움 3층 행사\n$note","1.65").status)
        for(note in listOf("시설 진입 제한 높이: 2.3m.","시설 진입 제한 높이: 2.2m 입차 불가"))assertEquals(note,"review",BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65").status)
    }
    @Test fun unparsedParkingAndEntryNotesRemainVisibleAndBlock() {
        for(note in listOf("주차: 퍼플존 / 동문","주차는 퍼플존만 이용하세요.","주차: 퍼플존에 주차하세요.","주차: 별도 승인 구역","진입문: 남문","입구: 동문","gate:south","진입문: 북문")) {
            for(selected in listOf(null,"auditorium")) {
                val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
                assertEquals(note,"review",d.status)
                assertTrue(note,(d.unparsedParkingNotes+d.entryNotes).contains(note))
                assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
            }
        }
    }
    @Test fun originalKnownZonesAndGatesCannotBypassComparison() {
        for((text,selected) in listOf(
            "코엑스 오디토리움 3층. 현장 운영에 따라 퍼플존만 개방합니다." to null,
            "코엑스 오디토리움 3층. 남문으로 오세요." to null,
            "COEX auditorium 3F. Use the east gate." to null,
            "코엑스 오디토리움 3층. purple zone만 개방합니다." to null,
            "코엑스 행사 안내. 블루존에서 뵙겠습니다." to "studio159",
            "코엑스 행사 안내. 북문에서 뵙겠습니다." to "studio159"
        ))assertEquals(text,"review",BriefEngine.resolve(text,"1.65",selected).status)
        assertEquals("review",BriefEngine.resolve("코엑스 오디토리움 3층. 북문과 블루존에서 안내합니다.","1.65").status)
    }
    @Test fun sameNameClosedOrUninterpretedAccessStillNeedsReview() {
        for(note in listOf("북문은 폐쇄되었습니다.","북문 이용 금지","블루존은 폐쇄되었습니다.","블루존은 통제 중입니다.","진입 금지","The north gate is closed.","Entry restricted.","블루존에서 뵙겠습니다.")) {
            for(selected in listOf(null,"auditorium")) {
                val d=BriefEngine.resolve("코엑스 오디토리움 3층\n$note","1.65",selected)
                assertEquals(note,"review",d.status)
                assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
            }
        }
        assertEquals("ready",BriefEngine.resolve("코엑스 오디토리움 3층\n주차는 B3 블루존을 이용해 주세요.","1.65").status)
    }
    @Test fun politeParkingClausesKeepTheirOriginalWords() {
        for((text,note) in listOf(
            "코엑스 오디토리움 3층에서 열리는 정기총회입니다. 주차는 B3 블루존을 이용해 주세요." to "주차는 B3 블루존을 이용해 주세요.",
            "장소: 코엑스 오디토리움 3층\n주차: B3 블루존 이용 바랍니다." to "주차: B3 블루존 이용 바랍니다.",
            "코엑스 오디토리움 3층\n주차: B3 블루존은 이용해 주십시오." to "주차: B3 블루존은 이용해 주십시오.",
            "코엑스 스튜디오159 2층\n주차: B2 퍼플존을 이용하여 주십시오." to "주차: B2 퍼플존을 이용하여 주십시오."
        )) {
            val d=BriefEngine.resolve(text,"1.65")
            assertEquals(text,"ready",d.status)
            assertEquals(listOf(note),d.parkingNotes)
        }
    }
    @Test fun politeParkingCannotHideUnknownWordsOrConflicts() {
        for(text in listOf(
            "코엑스 오디토리움 3층\n주차: B3 블루존을 이용해 주세요. 진입은 지하 2층입니다.",
            "코엑스 오디토리움 3층\n주차: B3 블루존을 이용 바랍니다 뒷문으로",
            "코엑스 오디토리움 3층\n주차: B2 블루존을 이용해 주세요.",
            "코엑스 오디토리움 3층\n주차: B3 퍼플존을 이용해 주십시오."
        ))assertEquals(text,"review",BriefEngine.resolve(text,"1.65").status)
        assertTrue(BriefEngine.resolve("코엑스 오디토리움 3층\n주차: B3 블루존을 이용 바랍니다 뒷문으로","1.65").message.contains("바꾸거나 생략하지"))
    }
    @Test fun stoppedDeferredReplacedAndOnlineEventsFailClosed() {
        for(text in listOf(
            "코엑스 오디토리움 3층. 행사는 더 이상 열리지 않습니다.",
            "코엑스 오디토리움 3층 행사. 본 행사는 다음 달로 미뤄졌습니다.",
            "코엑스 오디토리움 3층. 최종 개최지: 한빛센터.",
            "코엑스 오디토리움 3층. 이번 행사는 한빛센터에서 합니다.",
            "코엑스 오디토리움 3층 행사를 줌 온라인으로 전환했습니다."
        ))for(selected in listOf(null,"auditorium"))assertEquals(text,"review",BriefEngine.resolve(text,"1.65",selected).status)
    }
    @Test fun recognisedVenueRemainsACandidateUntilUserChecksTheNotice() {
        val d=BriefEngine.resolve("코엑스 오디토리움 3층","1.65")
        assertEquals("ready",d.status)
        assertTrue(d.message.contains("장소명 후보"))
        assertTrue(d.message.contains("안내문 원본을 직접 확인"))
    }
    @Test fun changedCancelledAndConflictingDeclarationsCannotBeSelected() {
        for(text in listOf(
            "코엑스 오디토리움에서 진행하던 행사를 송도컨벤시아로 옮겼습니다.",
            "코엑스 오디토리움 3층. 실제 행사장은 송도컨벤시아입니다.",
            "코엑스 오디토리움 3층 행사는 DDP 아트홀에서 진행합니다.",
            "COEX auditorium 3F event cancelled",
            "코엑스 오디토리움 3층. 실제 행사장은 한빛센터입니다.",
            "코엑스 오디토리움 3층 행사는 한빛센터에서 진행합니다.",
            "장소: 코엑스 오디토리움. 실제 장소: 코엑스 아셈볼룸."
        )) {
            for(selected in listOf(null,"auditorium")) {
                val d=BriefEngine.resolve(text,"1.65",selected)
                assertEquals(text,"review",d.status)
                assertThrows(IllegalArgumentException::class.java) { BriefEngine.export(d) }
            }
        }
    }
    @Test fun mixedScheduleAndRegistrationFloorsExplainHowToRecover() {
        val text="코엑스 오전 그랜드볼룸 1층, 오후 오디토리움 3층"
        val initial=BriefEngine.resolve(text,"1.65")
        assertEquals("select",initial.status)
        assertTrue(initial.message.contains("이번에 방문할 행사장과 층이 있는 문장만"))
        for(d in listOf(BriefEngine.resolve(text,"1.65","auditorium"),BriefEngine.resolve("코엑스 오디토리움 3층. 1층 로비에서 등록해주세요.","1.65"))) {
            assertEquals("review",d.status)
            assertTrue(d.message.contains("등록 장소와 다른 일정"))
        }
        assertEquals("ready",BriefEngine.resolve("코엑스 오디토리움 3층","1.65").status)
    }
    @Test fun sharedWebAndAndroidDecisionCorpus() {
        val path = System.getProperty("arrival.decisionCases") ?: error("Shared decision corpus path is required")
        val lines = File(path).readLines().drop(1).filter { it.isNotBlank() }
        assertTrue("Shared corpus must contain representative cases", lines.size >= 60)
        assertEquals("Corpus case IDs must be unique", lines.size, lines.map { it.substringBefore('\t') }.toSet().size)
        for (line in lines) {
            val c = line.split('\t')
            assertEquals("Malformed shared case", 5, c.size)
            val result = BriefEngine.resolve(c[1].replace("\\n", "\n"), c[2], c[3].ifEmpty { null })
            assertEquals(c[0] + ": " + result.message, c[4].toBoolean(), result.status == "ready")
        }
    }

    @Test fun parkingClausesStaySeparateAndInspectible() {
        val text="11월 7일 코엑스 오디토리움 3층에서 만나요. 주차는 B3 블루존입니다."
        val d=BriefEngine.resolve(text,"1.65")
        assertEquals("ready",d.status)
        assertEquals(listOf("주차는 B3 블루존입니다."),d.parkingNotes)
        assertEquals("B3 블루존",d.destination?.parking)
    }
    @Test fun invitationParkingCannotOverrideOfficialParking() {
        for(text in listOf("코엑스 오디토리움 3층\n주차: B2 블루존", "코엑스 오디토리움 3층\n주차: B3 퍼플존", "코엑스 오디토리움 3층\n주차: B3 블루존 행사장 지하 2층")) {
            assertEquals(text,"review",BriefEngine.resolve(text,"1.65").status)
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
