package studio.ax.arrival

enum class NavigationStatus { NOT_CHECKED, CHECKING, MATCHED, MISMATCH, UNKNOWN, UNAVAILABLE }

/** Only the verdict survives the callback. Navigation names and addresses are never retained. */
data class NavigationCheck(val status: NavigationStatus, val message: String) {
    companion object {
        fun initial() = NavigationCheck(NavigationStatus.NOT_CHECKED, "차량 내비 목적지와 행사 시설을 대조하세요.")
        fun checking() = NavigationCheck(NavigationStatus.CHECKING, "차량 내비 목적지를 확인하고 있습니다.")
        fun unavailable() = NavigationCheck(NavigationStatus.UNAVAILABLE, "차량 내비 정보를 읽지 못했습니다. 연결·권한 또는 목적지 설정을 확인하세요.")
    }
}

object NavigationMatcher {
    fun compare(name: String?, address: String?): NavigationCheck {
        val n = name.orEmpty().trim().lowercase()
        val a = address.orEmpty().trim().lowercase()
        val combined = "$n $a"
        if (Regex("마곡|magok|인터컨티넨탈|intercontinental|파르나스|parnas|웨스틴|westin|호텔|hotel|킨텍스|kintex|벡스코|bexco|아쿠아리움|aquarium|메가박스|megabox|카페|cafe|restaurant|식당|도서관|library|코엑스\\s*몰|coex\\s*mall").containsMatchIn(combined)) {
            return NavigationCheck(NavigationStatus.MISMATCH, "내비 목적지가 지원하는 코엑스 삼성동 회의시설과 다릅니다. 목적지를 확인한 뒤 다시 대조하세요.")
        }
        if (n.isEmpty()) return NavigationCheck(NavigationStatus.UNKNOWN, "내비 목적지 이름이 없어 행사 시설과 대조할 수 없습니다.")
        // Exact aliases only. A café/library with “COEX” in its name is not the convention facility.
        val facilityNames = setOf("코엑스", "코엑스컨벤션센터", "코엑스전시컨벤션센터", "coex", "coexconventioncenter", "coexconvention&exhibitioncenter", "coexconventionandexhibitioncenter", "코엑스오디토리움", "코엑스그랜드볼룸", "코엑스아셈볼룸", "코엑스스튜디오159", "코엑스더플라츠", "코엑스theplatz", "coexauditorium", "coexgrandballroom", "coexasemballroom", "coexstudio159", "coextheplatz")
        val normalizedName = n.replace(Regex("\\s+"), "")
        if (normalizedName !in facilityNames) {
            if (Regex("코엑스|\\bcoex\\b").containsMatchIn(n)) return NavigationCheck(NavigationStatus.UNKNOWN, "내비 목적지에 코엑스 이름이 있지만 지원하는 시설 명칭과 대조할 수 없습니다. 행사 시설을 직접 확인하세요.")
            return NavigationCheck(NavigationStatus.MISMATCH, "내비 목적지의 시설명이 지원하는 코엑스 회의시설과 다릅니다. 목적지를 확인한 뒤 다시 대조하세요.")
        }
        // Match the road/lot number next to its own street/dong, never a later room number.
        // Unsupported address formats remain unknown, rather than guessing from token presence.
        val koreanAddress = Regex("""서울(?:특별시)?[\s,]+강남구[\s,]+(?:영동대로\s+513(?:\s*\(삼성동\))?|삼성동\s+159)""")
        val englishAddress = Regex("""(?:513\s+yeongdong[ -]daero|159\s+samseong[ -]dong),\s*gangnam[ -]gu,\s*seoul(?:,\s*(?:south\s+korea|republic\s+of\s+korea))?""")
        if (koreanAddress.matches(a) || englishAddress.matches(a)) return NavigationCheck(NavigationStatus.MATCHED, "내비 목적지의 코엑스 삼성동 시설명과 주소가 일치합니다. 회의실·진입문까지 확인한 결과는 아닙니다.")
        return NavigationCheck(NavigationStatus.UNKNOWN, "코엑스 이름은 있지만 주소만으로 삼성동 회의시설을 확인할 수 없습니다.")
    }
}

/** One-shot request tokens prevent late SDK callbacks from reviving old state. */
class NavigationRequestState {
    private var generation = 0
    private var pending = false
    fun begin(): Int { generation++; pending = true; return generation }
    fun isPending(token: Int) = pending && token == generation
    fun complete(token: Int): Boolean {
        if (!isPending(token)) return false
        pending = false
        return true
    }
    fun cancel() { generation++; pending = false }
}
