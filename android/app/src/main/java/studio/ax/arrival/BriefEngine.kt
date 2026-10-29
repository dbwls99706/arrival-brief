package studio.ax.arrival

data class Destination(val id:String,val name:String,val floor:String,val gate:String,val parking:String,val patterns:List<Regex>)
data class Decision(val status:String,val message:String,val candidates:List<Destination> = emptyList(),val destination:Destination?=null,val height:Double?=null)
object BriefEngine {
 const val SOURCE="https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/"
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
  if(!Regex("코엑스|\\bcoex\\b",RegexOption.IGNORE_CASE).containsMatchIn(text)||Regex("코엑스\\s*마곡|coex\\s*magok",RegexOption.IGNORE_CASE).containsMatchIn(text))return Decision("unsupported","현재 코엑스 삼성동 회의실만 지원합니다.")
  if(Regex("킨텍스|벡스코|세텍|예술의전당|aT센터|아닌|아니라|아니다|취소|장소.{0,12}(변경|이전)|(변경|취소).{0,20}(목적지|행사장)|회의실.{0,12}취소",RegexOption.IGNORE_CASE).containsMatchIn(text))return Decision("review","다른 시설 또는 장소 변경 표현이 있습니다. 현재 유효한 장소만 남겨주세요.")
  val matches=destinations.filter { d->d.patterns.any { it.containsMatchIn(text) } }
  val floors=Regex("(?<![\\dB])([1-9])(?:층|F\\b)",RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1].toInt() }.toList()
  if(matches.size==1&&floors.any { it!=matches[0].floor.first().digitToInt() })return Decision("review","입력 층과 공식 장소 층이 다릅니다. 안내를 확인해주세요.")
  val d=if(selectedId!=null)destinations.find { it.id==selectedId } else matches.singleOrNull()
  if(d==null)return Decision("select",if(matches.size>1)"여러 장소가 있습니다. 방문할 장소를 선택하세요." else "세부 장소를 선택하세요.",matches.ifEmpty { destinations })
  if(matches.isNotEmpty()&&d !in matches)return Decision("conflict","선택한 장소가 입력 안내와 다릅니다.")
  val h=heightText.toDoubleOrNull()
  if(h==null||!h.isFinite()||h<1.0||h>5.0)return Decision("invalid","실제 차량 전체 높이를 1.00~5.00m로 입력하세요.",destination=d)
  if(h>2.2)return Decision("blocked","차량 높이가 시설 제한 2.2m를 초과합니다. 이 주차 동선을 사용하지 마세요.",destination=d,height=h)
  return Decision("ready",if(h==2.2)"제한 높이와 같습니다. 현장 표지와 적재물을 확인하세요." else "공식 안내와 매칭되었습니다.",destination=d,height=h)
 }
 fun export(d:Decision):String {
  require(d.status=="ready")
  val p=d.destination!!
  return "Arrival Brief\n코엑스 ${p.name} (${p.floor})\n진입구: ${p.gate}\n추천 주차: ${p.parking}\n차량 전체 높이: ${d.height}m / 시설 제한 2.2m\n\n실시간 빈자리·입구 좌표·실내 동선·승객 하차 지점은 제공하지 않습니다. 현장 표지와 안내를 우선하세요.\n\n코엑스 공식 회의실 주차안내\n$SOURCE\n자료 확인: 2026-10-28\n"
 }
}
