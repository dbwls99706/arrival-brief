package studio.ax.arrival

data class Destination(val id:String,val name:String,val floor:String,val gate:String,val parking:String,val patterns:List<Regex>)
data class Decision(val status:String,val message:String,val candidates:List<Destination> = emptyList(),val destination:Destination?=null,val height:Double?=null)
object BriefEngine {
 const val SOURCE="https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/"
 const val CONFLICT_SOURCE="https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/"
 const val VERIFIED_DATE="2026-10-30"
 private val conflictingIds=setOf("conferenceE","rooms300","rooms400")
 private fun patterns(vararg p:String)=p.map { Regex(it,RegexOption.IGNORE_CASE) }
 val destinations=listOf(
  Destination("auditorium","오디토리움","3F","북문 · 봉은사 맞은편","B3 블루존",patterns("오디토리움","auditorium")),
  Destination("grand","그랜드볼룸","1F","북문 · 봉은사 맞은편","B3 블루존",patterns("그랜드\\s*볼룸","grand\\s*ballroom","\\bgbr\\b")),
  Destination("asem","아셈볼룸","2F","북문 · 봉은사 맞은편","B3 블루존",patterns("아셈\\s*볼룸","asem\\s*ballroom")),
  Destination("studio159","스튜디오 159","2F","동문 · 영동대로 방면","B2 퍼플존",patterns("스튜디오\\s*159","studio\\s*159")),
  Destination("conferenceE","컨퍼런스룸 E","3F","동문 · 영동대로 방면","B2 퍼플존",patterns("컨퍼런스\\s*룸\\s*e\\b","conference\\s*room\\s*e\\b")),
  Destination("platz","THE PLATZ","2F","동문 · 영동대로 방면","B2 지하 2층",patterns("the\\s*platz","더\\s*플라츠")),
  Destination("rooms300","회의실 300~328호","3F","동문 · 영동대로 방면","B2 그린존",patterns("(?:회의실|룸)\\s*3(?:0\\d|1\\d|2[0-8])(?:호|\\b)","(?<!\\d)3(?:0\\d|1\\d|2[0-8])호")),
  Destination("rooms400","회의실 400~403호","4F","동문 · 영동대로 방면","B2 그린존",patterns("(?:회의실|룸)\\s*40[0-3](?:호|\\b)","(?<!\\d)40[0-3]호"))
 )
 fun resolve(text:String,heightText:String,selectedId:String?=null):Decision {
  if(text.isBlank()||text.length>10000)return Decision("invalid","행사 안내를 10,000자 이내로 입력하세요.")
  if(!Regex("코엑스|\\bcoex\\b",RegexOption.IGNORE_CASE).containsMatchIn(text)||Regex("마곡|\\bmagok\\b",RegexOption.IGNORE_CASE).containsMatchIn(text))return Decision("unsupported","현재 코엑스 삼성동 회의실만 지원합니다.")
  if(Regex("킨텍스|벡스코|세텍|예술의전당|aT센터|아닌|아니라|아니다|취소|장소.{0,12}(변경|이전)|(변경|취소).{0,20}(목적지|행사장)|회의실.{0,12}취소",setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(text))return Decision("review","다른 시설 또는 장소 변경 표현이 있습니다. 현재 유효한 장소만 남겨주세요.")
  if(Regex("""호텔|\bhotel\b|인터컨티넨탈|intercontinental|파르나스|parnas|웨스틴|westin|(?:코엑스|\bcoex\b).{0,20}(?:인근|근처|주변|맞은편|건너편)|(?:인근|근처|주변|맞은편|건너편).{0,20}(?:코엑스|\bcoex\b)|\b(?:near|nearby|opposite|across\s+from)\b.{0,25}\bcoex\b|\bcoex\b.{0,25}\b(?:near|nearby|opposite|across\s+from)\b""",setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(text))return Decision("review","코엑스 외부 또는 호텔을 가리키는 표현이 있습니다. 코엑스 삼성동 내부 행사장인지 확인하세요.")
  if(Regex("""지하\s*[1-9]\d*\s*(?:층|F\b)|\bB[1-9]\d*\s*(?:층|F\b|\b)|\bbasement\b""",RegexOption.IGNORE_CASE).containsMatchIn(text))return Decision("review","지하 층이나 주차장 안내가 포함되어 있습니다. 방문할 회의실의 장소와 층만 남겨주세요.")
  if(Regex("""(?:[A-Z]\s*홀|\bhall\s*[A-Z]\b|전시(?:홀|관|장))""",RegexOption.IGNORE_CASE).containsMatchIn(text))return Decision("review","지원하지 않는 전시홀 안내가 포함되어 있습니다. 전시홀을 다른 회의실로 대신 선택할 수 없습니다.")
  val roomNumbers=Regex("""(?:회의실|(?<!볼)룸|\broom)\s*(\d{1,4})(?:호|\b(?!\s*(?:층|F\b)))|(?<!\d)(\d{3,4})호""",RegexOption.IGNORE_CASE).findAll(text).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt() }
  if(roomNumbers.any { it !in 300..328 && it !in 400..403 })return Decision("review","지원하지 않는 회의실 번호가 있습니다. 다른 장소를 대신 선택하지 말고 행사장의 공식 명칭을 확인하세요.")
  val matches=destinations.filter { d->d.patterns.any { it.containsMatchIn(text) } }
  val floors=Regex("(?<![\\dB])([1-9][0-9]*)\\s*(?:층|F\\b)",RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE }.toList()
  if(matches.size==1&&floors.any { it!=matches[0].floor.first().digitToInt() })return Decision("review","입력 층과 공식 장소 층이 다릅니다. 안내를 확인해주세요.")
  if(selectedId!=null&&destinations.none { it.id==selectedId })return Decision("invalid","선택한 장소를 찾을 수 없습니다. 장소를 다시 선택하세요.")
  val d=if(selectedId!=null)destinations.find { it.id==selectedId } else matches.singleOrNull()
  if(d==null)return Decision("select",if(matches.size>1)"여러 장소가 있습니다. 방문할 장소를 선택하세요." else "세부 장소를 선택하세요.",matches.ifEmpty { destinations })
  if(matches.isNotEmpty()&&d !in matches)return Decision("conflict","선택한 장소가 입력 안내와 다릅니다.")
  if(floors.any { it!=d.floor.first().digitToInt() })return Decision("review","선택한 장소의 공식 층과 입력 층이 다릅니다. 방문할 장소의 안내만 남겨주세요.",destination=d)
  val h=if(Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(heightText.trim()))heightText.trim().toDoubleOrNull() else null
  if(h==null||!h.isFinite()||h<1.0||h>5.0)return Decision("invalid","전체 높이를 1.00~5.00m 범위의 숫자로 입력하세요. 단위 없이 소수점 둘째 자리까지 입력할 수 있습니다.",destination=d)
  if(h>2.2)return Decision("blocked","차량 높이가 시설 제한 2.2m를 초과합니다. 이 주차 동선을 사용하지 마세요.",destination=d,height=h)
  if(d.id in conflictingIds)return Decision("source_conflict","공식 자료의 주차 구역이 서로 다릅니다. ${d.name} 주차 브리핑은 확정할 수 없습니다. 코엑스 현장 안내로 확인하세요.",destination=d,height=h)
  return Decision("ready",if(h==2.2)"제한 높이와 같습니다. 현장 표지와 적재물을 확인하세요." else "공식 안내와 매칭되었습니다.",destination=d,height=h)
 }
 fun export(d:Decision):String {
  require(d.status=="ready")
  val p=d.destination!!
  return "Arrival Brief\n코엑스 ${p.name} (${p.floor})\n진입구: ${p.gate}\n추천 주차: ${p.parking}\n차량 전체 높이: ${d.height}m / 시설 제한 2.2m\n${d.message}\n\n실시간 빈자리·입구 좌표·실내 동선·승객 하차 지점은 제공하지 않습니다. 현장 표지와 안내를 우선하세요.\n\n코엑스 공식 회의실 주차안내\n$SOURCE\n자료 확인: $VERIFIED_DATE\n"
 }
}
