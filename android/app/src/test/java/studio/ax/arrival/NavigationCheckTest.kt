package studio.ax.arrival

import org.junit.Assert.*
import org.junit.Test

class NavigationCheckTest {
    @Test fun facilityNameRequiresSpecificSamseongAddress() {
        assertEquals(NavigationStatus.MATCHED, NavigationMatcher.compare("코엑스", "서울 강남구 영동대로 513").status)
        assertEquals(NavigationStatus.MATCHED, NavigationMatcher.compare("COEX", "513 Yeongdong-daero, Gangnam-gu, Seoul").status)
        assertEquals(NavigationStatus.MATCHED, NavigationMatcher.compare("코엑스 컨벤션센터", "서울특별시 강남구 삼성동 159").status)
        for (address in listOf("", "서울 강남구", "서울 강남구 영동대로 5130", "서울 강남구 삼성동 1599", "서울 강남구 영동대로 513-9")) {
            assertEquals(address, NavigationStatus.UNKNOWN, NavigationMatcher.compare("코엑스", address).status)
        }
    }

    @Test fun magokHotelsAndOtherFacilitiesCannotMatch() {
        for (name in listOf("코엑스 마곡", "COEX Magok", "그랜드 인터컨티넨탈 코엑스", "코엑스몰", "COEX Mall", "코엑스 아쿠아리움", "킨텍스")) {
            assertEquals(name, NavigationStatus.MISMATCH, NavigationMatcher.compare(name, "서울 강남구 영동대로 513").status)
        }
        assertEquals(NavigationStatus.UNKNOWN, NavigationMatcher.compare(null, null).status)
    }

    @Test fun businessNamesAndUnrelatedNumbersCannotMasqueradeAsFacility() {
        for (name in listOf("별마당도서관 코엑스", "카페 코엑스점", "COEX restaurant", "코엑스 북문 카페")) {
            assertEquals(name, NavigationStatus.MISMATCH, NavigationMatcher.compare(name, "서울 강남구 영동대로 513").status)
        }
        for (address in listOf("서울 강남구 영동대로 517 513호", "서울 강남구 삼성동 150 159호", "서울 강남구 영동대로 513-1", "서울 강남구 삼성동 159-9", "513 Other Street, Gangnam-gu, Seoul", "517 Yeongdong-daero, Gangnam-gu, Seoul, suite 513")) {
            assertNotEquals(address, NavigationStatus.MATCHED, NavigationMatcher.compare("코엑스", address).status)
        }
        assertEquals(NavigationStatus.MATCHED, NavigationMatcher.compare("코엑스", "서울특별시 강남구 영동대로 513 (삼성동)").status)
    }

    @Test fun supportedMeetingRoomAliasesVerifyOnlyTheFacility() {
        for (name in listOf("코엑스 오디토리움", "코엑스 그랜드볼룸", "코엑스 아셈볼룸", "코엑스 스튜디오 159", "코엑스 THE PLATZ", "COEX Auditorium", "COEX Grand Ballroom", "COEX ASEM Ballroom", "COEX Studio 159", "COEX The Platz")) {
            val check = NavigationMatcher.compare(name, "서울 강남구 영동대로 513")
            assertEquals(name, NavigationStatus.MATCHED, check.status)
            assertTrue(check.message.contains("회의실·진입문까지 확인한 결과는 아닙니다"))
        }
        assertEquals(NavigationStatus.UNKNOWN, NavigationMatcher.compare("코엑스 미확인 세부시설", "서울 강남구 영동대로 513").status)
    }

    @Test fun rawNavigationDataIsNotReturnedInVerdict() {
        val check = NavigationMatcher.compare("개인 주소 예시", "개인 상세주소 예시")
        assertFalse(check.toString().contains("개인 주소 예시"))
        assertFalse(check.toString().contains("개인 상세주소 예시"))
    }

    @Test fun cancelledOrSupersededCallbacksCannotFinish() {
        val request = NavigationRequestState()
        val first = request.begin(); request.cancel()
        assertFalse(request.complete(first))
        val second = request.begin(); val third = request.begin()
        assertFalse(request.complete(second))
        assertTrue(request.complete(third))
        assertFalse(request.complete(third))
        assertFalse(request.isPending(third))
    }

    @Test fun mismatchCannotBeOverriddenByManualCheckOrFailedRetry() {
        val session = BriefSession().apply {
            updateNotice("코엑스 오디토리움 3층"); updateHeight("1.65")
            reviewHeight(true); confirmParked(true)
            updateNavigation(NavigationMatcher.compare("코엑스 마곡", "서울 강서구"))
            reviewNavigationManually(true)
        }
        val parked = SimulatorVehicleGateway(VehicleMode.PARKED)
        assertFalse(session.confirm(parked))
        session.updateNavigation(NavigationCheck.checking())
        session.updateNavigation(NavigationCheck.unavailable())
        session.reviewNavigationManually(true)
        assertEquals(NavigationStatus.MISMATCH, session.navigationCheck.status)
        assertFalse(session.confirm(parked))
        session.updateNavigation(NavigationMatcher.compare("코엑스", "서울 강남구 영동대로 513"))
        assertTrue(session.confirm(parked))
    }

    @Test fun unconnectedOrUnknownDestinationNeedsExplicitManualReview() {
        val session = BriefSession().apply {
            updateNotice("코엑스 오디토리움"); updateHeight("1.65")
            reviewHeight(true); confirmParked(true)
        }
        val parked = SimulatorVehicleGateway(VehicleMode.PARKED)
        assertFalse(session.confirm(parked))
        session.updateNavigation(NavigationCheck.unavailable())
        assertFalse(session.confirm(parked))
        session.reviewNavigationManually(true)
        assertTrue(session.confirm(parked))
        session.updateNavigation(NavigationCheck.checking())
        session.reviewNavigationManually(true)
        assertFalse(session.confirm(parked))
    }
}
