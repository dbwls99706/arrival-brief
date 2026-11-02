package studio.ax.arrival

data class Destination(val id:String,val name:String,val floor:String,val gate:String,val parking:String,val patterns:List<Regex>)
data class Decision(val status:String,val message:String,val candidates:List<Destination> = emptyList(),val destination:Destination?=null,val height:Double?=null,val parkingNotes:List<String> = emptyList(),val unparsedParkingNotes:List<String> = emptyList(),val entryNotes:List<String> = emptyList(),val facilityNotes:List<String> = emptyList(),val eventNotes:List<String> = emptyList())
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
 // Original notices with changed/cancelled events or unverified explicit venue
 // claims must be reviewed; manual selection cannot override these conflicts.
 private val otherFacility=Regex("""킨텍스|벡스코|세텍|예술의전당|aT센터|송도\s*컨벤시아|동대문\s*디자인\s*플라자|(?<![A-Za-z0-9_])(?:KINTEX|BEXCO|SETEC|DDP|Songdo\s+Convensia|Dongdaemun\s+Design\s+Plaza)(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val noticeChange=Regex("""아닌|아니라|아니다|취소|변경|연기|미뤄|미루|미룬|미룹|(?:열(?:리)?|진행(?:되|하)?|개최(?:되|하)?)지\s*않|안\s*(?:열리|열립|진행|개최)|(?:온라인|비대면|줌|zoom)[\s\S]{0,30}전환|전환[\s\S]{0,30}(?:온라인|비대면|줌|zoom)|옮(?:겼|겨|깁|길|김|긴)|바뀌|바꾸|이동|이전(?:되|했|합|할|합니다|\s*예정)|(?:행사|장소|행사장|목적지)\s*(?:를|가|는|이|을)?\s*이전|(?<![A-Za-z0-9_])(?:cancel(?:led|ed|ling|ing|l?ation)?|postponed?|postponement|relocat(?:e|ed|ing|ion)|reschedul(?:e|ed|ing)|(?:moved?|moving|moves|transfer(?:red|ring)?)\s+(?:to|from)|(?:venue|location|destination)\s+(?:(?:has|have)\s+been\s+|(?:has|was|is)\s+)?changed|change\s+of\s+(?:venue|location)|no\s+longer|not\s+(?:at|in|held))(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val venueClaims=listOf(
  """(?:행사\s*장소|행사장|장소|목적지|개최지|개최\s*장소)\s*(?:은|는|이|가|[:：])\s*([^.!?。\n]+)""",
  """(?:행사|공연|회의|모임|컨퍼런스)(?:는|가|을|를)?\s*(?:(?:실제로|이번에|현재|최종적으로)\s*)?([^.!?。\n]+?)에서\s*(?:진행|개최|열|시작|합니다)""",
  """\b(?:venue|event\s+location|location|destination)\s*(?:(?:is|will\s+be)\s+|[:：]\s*)([^.!?。\n]+)""",
  """\b(?:event|conference|meeting)\s+(?:(?:is|will\s+be|will)\s+)?(?:held|taking\s+place|takes\s+place|take\s+place|hosted)\s+(?:at|in)\s+([^.!?。\n]+)"""
 ).map { Regex(it,RegexOption.IGNORE_CASE) }
 private fun isSupportedVenueClaim(claim:String,destination:Destination):Boolean {
  // COEX sponsorship elsewhere does not establish the venue's facility.
  val facility=Regex("""코엑스(?:의)?|(?<![A-Za-z0-9_])coex(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
  if(!facility.containsMatchIn(claim))return false
  var remainder=claim.replace(facility,"")
  for(pattern in destination.patterns)remainder=remainder.replace(pattern,"")
  remainder=remainder.replace(Regex("""[1-9]\d*\s*(?:층|F(?![A-Za-z0-9_]))""",RegexOption.IGNORE_CASE),"").replace("삼성동","")
   .replace(Regex("""(?:입니다|에서\s*(?:진행합니다|개최합니다|열립니다|합니다|만나요))\s*$""",RegexOption.IGNORE_CASE),"")
   .replace(Regex("""[\s()\[\],·:：-]"""),"")
  return remainder.isEmpty()
 }
 private fun hasConflictingVenueClaims(text:String):Boolean {
  val claims=venueClaims.flatMap { pattern->pattern.findAll(text).map { it.groupValues[1] }.toList() }
  if(claims.isEmpty())return false
  val all=destinations.filter { d->d.patterns.any { it.containsMatchIn(text) } }
  if(all.size!=1)return true
  return claims.any { claim->
   val matches=destinations.filter { d->d.patterns.any { it.containsMatchIn(claim) } }
   matches.size!=1||matches[0].id!=all[0].id||!isSupportedVenueClaim(claim,matches[0])
  }
 }
 private fun hasUnownedVenueMentions(text:String):Boolean {
  for(clause in text.split(Regex("""\r?\n|(?<=[.!?。])\s+"""))){
   val raw=destinations.flatMap { d->d.patterns.flatMap { p->p.findAll(clause).map { it.range.first to it.range.last+1 }.toList() } }.sortedWith(compareBy<Pair<Int,Int>> { it.first }.thenByDescending { it.second })
   val hits=mutableListOf<Pair<Int,Int>>()
   for(hit in raw)if(hits.isEmpty()||hit.first>=hits.last().second)hits.add(hit)
   if(hits.isEmpty())continue
   if(Regex("""^\s*(?:후원|주최|주관|협찬|sponsor(?:ed\s+by)?|organizer|hosted\s+by)\s*[:：]""",RegexOption.IGNORE_CASE).containsMatchIn(clause))return true
   if(!Regex("""(?:코엑스|(?<![A-Za-z0-9_])coex)\s*(?:삼성동\s*)?(?:(?:오전|오후)\s*)?(?:회의실\s*)?(?:[1-9]\d*\s*(?:층|F)\s*)?$""",RegexOption.IGNORE_CASE).containsMatchIn(clause.substring(0,hits[0].first)))return true
   for(i in 1 until hits.size){
    val gap=clause.substring(hits[i-1].second,hits[i].first)
     .replace(Regex("""[1-9]\d*\s*(?:층|F(?![A-Za-z0-9_]))""",RegexOption.IGNORE_CASE),"")
     .replace(Regex("""(?:오전|오후|및|또는|혹은|과|와)|(?<![A-Za-z0-9_])(?:and|or)(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE),"")
     .replace(Regex("""[\s()\[\],·/]"""),"")
    if(gap.isNotEmpty())return true
   }
  }
  return false
 }
 // Only complete, explicit parking clauses may be excluded from venue-floor
 // matching. Global facility/change checks still inspect the untouched input.
 private val parkingFloor=Regex("""(?<![A-Za-z0-9])B([1-9]\d*)\s*(?:층|F(?![A-Za-z0-9])|(?![A-Za-z0-9]))|지하\s*([1-9]\d*)\s*(?:층|F\b)""",RegexOption.IGNORE_CASE)
 private val parkingZone=Regex("""(?:블루\s*존|퍼플\s*존|그린\s*존)(?:을|를|은|는)?|(?<![A-Za-z0-9_])(?:blue|purple|green)\s*zone(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val entryGate=Regex("""북문|동문|남문|서문|(?<![A-Za-z0-9_])(?:(?:north|east|south|west)\s*(?:gate|entrance)|(?:gate|entrance)\s*[:：]?\s*(?:north|east|south|west))(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val operationalRestriction=Regex("""폐쇄|통제|불가|불능|할\s*수\s*없|못\s*(?:이용|방문|접근|참석|입장)|(?:이용|진입|출입|입차|사용)\s*금지|(?<![A-Za-z0-9_])(?:closed|closure|restricted|no\s+(?:entry|parking)|do\s+not\s+enter|(?:cannot|can't|unable\s+to)\s+(?:visit|reach|enter|use|access))(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 // Unparsed access-operation grammar is always reviewed, whether affirmative or
 // negative. The sole exception is an exact statement of the known height fact.
 private val parkingReference=Regex("""주차|(?<![A-Za-z0-9_])(?:parking|car\s+park)(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val accessOperation=Regex("""진입|출입|입차|출차|통행|입구|출구|정문|후문|옆문|쪽문|대문|북문|동문|남문|서문|(?<![A-Za-z0-9_])(?:gate|entrance|entry|exit|access|drive[\s-]?in)(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 // Operating status is unverified even when its wording sounds positive.
 // Preserve the whole status clause and hold it for facility confirmation.
 private val facilityOperation=Regex("""휴관|휴장|휴무|휴업|폐관|폐장|폐업|폐쇄|개관|개장|개업|운영|영업|중단|재개|비개최|미개최|쉬는\s*날|문\s*(?:을|를|이|가|은|는)?\s*(?:닫|열|엽)""",RegexOption.IGNORE_CASE)
 // Remote/hybrid or non-occurrence wording cannot establish a physical visit.
 private val eventStatusReference=Regex("""무산|불성립|비개최|미개최|비대면|온라인|원격|하이브리드|(?<![A-Za-z0-9_])(?:zoom|online|remote|hybrid)(?![A-Za-z0-9_])|(?:행사|공연|회의|모임)\s*(?:가|는|은|이)?\s*없""",RegexOption.IGNORE_CASE)
 // A known room never resolves an event clause containing unverified negation.
 private val eventSubject=Regex("""행사|공연|회의|모임|강연|세미나|컨퍼런스|워크숍|(?<![A-Za-z0-9_])(?:events?|conferences?|meetings?|seminars?|workshops?|shows?|concerts?|talks?)(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val eventNegation=Regex("""(?:안|못)\s*(?:하|해|합|한|열|오|가|갑|참|진|개)|않|없|(?<![A-Za-z0-9_])(?:no|not|never|without|isn['’]?t|aren['’]?t|wasn['’]?t|weren['’]?t|won['’]?t|doesn['’]?t|don['’]?t|didn['’]?t|cannot|can['’]?t)(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val eventLifecycle=Regex("""종료|끝(?:났|나|납|난|낼|냅)|마쳤|마침|완료|(?<![A-Za-z0-9_])(?:end(?:ed|ing|s)?|over|complet(?:e|ed|ion)|finish(?:ed|ing|es)?|conclud(?:e|ed|ing))(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 // The subject is often omitted after a venue line; keep completion clauses for review.
 private val subjectlessCompletion=Regex("""종료|끝(?:났|나|납|난|낼|냅)|마쳤|완료|(?<![A-Za-z0-9_])(?:ended|ending|ends|completed|finished|concluded)(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])(?:it|this|that)\s*(?:is|was|'s|’s)\s+over(?![A-Za-z0-9_])""",RegexOption.IGNORE_CASE)
 private val knownHeightNotice=Regex("""^(?:(?:(?:코엑스|시설)(?:의)?\s*)?(?:(?:지하\s*)?주차장\s*)?(?:차량\s*)?(?:진입\s*)?제한\s*높이\s*[:：]?\s*2\.20?\s*(?:m|미터)(?:입니다)?|(?:(?:facility|parking|car\s+park)\s+)?(?:(?:entry|entrance|access|drive[\s-]?in)\s+)?(?:height\s+limit|maximum\s+height)\s*[:：]?\s*2\.20?\s*m)[.!。]?$""",RegexOption.IGNORE_CASE)
 private data class NoticeParts(val venueText:String,val parkingNotes:List<String>,val unparsedParkingNotes:List<String>,val entryNotes:List<String>,val facilityNotes:List<String>,val eventNotes:List<String>)
 private fun noticeParts(text:String):NoticeParts {
  val notes=mutableListOf<String>(); val unparsed=mutableListOf<String>();val entries=mutableListOf<String>();val facilities=mutableListOf<String>();val events=mutableListOf<String>();val venues=mutableListOf<String>()
  for(original in text.split(Regex("""\r?\n|(?<=[.!?。])\s+"""))){
   val clause=original.trim().replace(Regex("""^[-•]\s*"""),"")
   if(knownHeightNotice.matches(clause)){venues.add(original);continue}
   val labelled=Regex("""^(?:주차(?:\s*(?:안내|정보|구역))?|parking(?:\s*(?:info|information))?)(?:\s*[:：]\s*|\s*는\s*|\s+)""",RegexOption.IGNORE_CASE).find(clause)
   val locationFirst=Regex("""^(?:지하\s*[1-9]\d*\s*(?:층|F\b)|B[1-9]\d*\s*(?:층|F(?![A-Za-z0-9])|(?![A-Za-z0-9])))""",RegexOption.IGNORE_CASE).containsMatchIn(clause)&&clause.contains("주차장")
   val body=if(labelled!=null)clause.substring(labelled.value.length) else clause
   val hasLocation=parkingFloor.containsMatchIn(body)||parkingZone.containsMatchIn(body)
   val remainder=body.replace(parkingFloor,"").replace(parkingZone,"")
    .replace(Regex("""주차장(?:을|를|은|는)?|이용(?:해|하여)\s*(?:주세요|주십시오)|이용\s*바랍니다|이용하세요|이용|입니다""",RegexOption.IGNORE_CASE),"")
    .replace(Regex("""[\s()\[\],·/.!?。]"""),"")
   if((labelled!=null||locationFirst)&&hasLocation&&remainder.isEmpty())notes.add(original.trim()) else {
    venues.add(original)
    if(parkingReference.containsMatchIn(clause))unparsed.add(original.trim())
    if(accessOperation.containsMatchIn(clause))entries.add(original.trim())
    if(facilityOperation.containsMatchIn(clause))facilities.add(original.trim())
    if(subjectlessCompletion.containsMatchIn(clause)||eventStatusReference.containsMatchIn(clause)||(eventSubject.containsMatchIn(clause)&&(eventNegation.containsMatchIn(clause)||eventLifecycle.containsMatchIn(clause))))events.add(original.trim())
   }
  }
  return NoticeParts(venues.joinToString("\n"),notes,unparsed,entries,facilities,events)
 }
 private fun parkingNotesAgree(notes:List<String>,destination:Destination):Boolean {
  val expectedFloor=destination.parking.substringAfter('B').takeWhile { it.isDigit() }.toInt()
  val expectedZone=when { destination.parking.contains("블루존")->"blue";destination.parking.contains("퍼플존")->"purple";destination.parking.contains("그린존")->"green";else->"neutral" }
  return notes.all { note->
   parkingFloor.findAll(note).all { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull()==expectedFloor } &&
   parkingZone.findAll(note).all {
    val zone=when { Regex("블루|blue",RegexOption.IGNORE_CASE).containsMatchIn(it.value)->"blue";Regex("퍼플|purple",RegexOption.IGNORE_CASE).containsMatchIn(it.value)->"purple";else->"green" }
    zone==expectedZone
   }
  }
 }
 private fun entryGatesAgree(text:String,destination:Destination):Boolean {
  val expected=destination.gate.substringBefore(" ·")
  return entryGate.findAll(text).all {
   val gate=when { Regex("북문|north",RegexOption.IGNORE_CASE).containsMatchIn(it.value)->"북문";Regex("동문|east",RegexOption.IGNORE_CASE).containsMatchIn(it.value)->"동문";Regex("남문|south",RegexOption.IGNORE_CASE).containsMatchIn(it.value)->"남문";else->"서문" }
   gate==expected
  }
 }
 fun resolve(text:String,heightText:String,selectedId:String?=null):Decision {
  if(text.isBlank()||text.length>10000)return Decision("invalid","행사 안내를 10,000자 이내로 입력하세요.")
  if(!Regex("코엑스|\\bcoex\\b",RegexOption.IGNORE_CASE).containsMatchIn(text)||Regex("마곡|\\bmagok\\b",RegexOption.IGNORE_CASE).containsMatchIn(text))return Decision("unsupported","현재 코엑스 삼성동 회의실만 지원합니다.")
  val parts=noticeParts(text); val venueText=parts.venueText
  if(parts.unparsedParkingNotes.isNotEmpty()||parts.entryNotes.isNotEmpty()||parts.facilityNotes.isNotEmpty()||parts.eventNotes.isNotEmpty())return Decision("review","해석하지 못한 행사 상태·시설 운영·주차·출입 안내가 있습니다. 실제 현장 개최 여부와 이용 가능 상태를 주최 측·시설의 최신 안내로 확인하세요. 원문 정보를 임의로 바꾸거나 생략하지 마세요.",parkingNotes=parts.parkingNotes,unparsedParkingNotes=parts.unparsedParkingNotes,entryNotes=parts.entryNotes,facilityNotes=parts.facilityNotes,eventNotes=parts.eventNotes)
  if(otherFacility.containsMatchIn(text)||noticeChange.containsMatchIn(text)||hasConflictingVenueClaims(text)||hasUnownedVenueMentions(text))return Decision("review","다른 시설, 장소 변경·취소 또는 시설 귀속이 확인되지 않은 장소 안내가 있습니다. 현재 방문할 장소를 코엑스 세부장소 층으로 확인하고, 원문의 다른 시설·제약 정보를 임의로 생략하지 마세요.")
  if(Regex("""호텔|\bhotel\b|인터컨티넨탈|intercontinental|파르나스|parnas|웨스틴|westin|(?:코엑스|\bcoex\b).{0,20}(?:인근|근처|주변|맞은편|건너편)|(?:인근|근처|주변|맞은편|건너편).{0,20}(?:코엑스|\bcoex\b)|\b(?:near|nearby|opposite|across\s+from)\b.{0,25}\bcoex\b|\bcoex\b.{0,25}\b(?:near|nearby|opposite|across\s+from)\b""",setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(text))return Decision("review","코엑스 외부 또는 호텔을 가리키는 표현이 있습니다. 코엑스 삼성동 내부 행사장인지 확인하세요.")
  // A matching zone/gate name says nothing about operating status. Any such
  // mention outside a completely interpreted parking clause requires review.
  if(operationalRestriction.containsMatchIn(text)||parkingZone.containsMatchIn(venueText)||entryGate.containsMatchIn(venueText))return Decision("review","주차 구역·진입문에 관한 해석되지 않은 안내 또는 운영 제한 표현이 있습니다. 같은 구역명이어도 이용 가능하다고 판단하지 않습니다. 원문을 생략하지 말고 시설의 최신 운영 안내를 확인하세요.",parkingNotes=parts.parkingNotes)
  if(Regex("""지하\s*[1-9]\d*\s*(?:층|F\b)|(?<![A-Za-z0-9])B[1-9]\d*\s*(?:층|F(?![A-Za-z0-9])|(?![A-Za-z0-9]))|\bbasement\b""",RegexOption.IGNORE_CASE).containsMatchIn(venueText))return Decision("review","행사장 지하층 또는 해석하지 못한 주차 표현이 있습니다. 원문의 행사장과 주차층·구역을 직접 확인하고, 정보를 바꾸거나 생략하지 않은 별도 주차 안내를 입력하세요.")
  if(Regex("""(?:[A-Z]\s*홀|\bhall\s*[A-Z]\b|전시(?:홀|관|장))""",RegexOption.IGNORE_CASE).containsMatchIn(venueText))return Decision("review","지원하지 않는 전시홀 안내가 포함되어 있습니다. 전시홀을 다른 회의실로 대신 선택할 수 없습니다.")
  val roomNumbers=Regex("""(?:회의실|(?<!볼)룸|\broom)\s*(\d{1,4})(?:호|\b(?!\s*(?:층|F\b)))|(?<!\d)(\d{3,4})호""",RegexOption.IGNORE_CASE).findAll(venueText).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt() }
  if(roomNumbers.any { it !in 300..328 && it !in 400..403 })return Decision("review","지원하지 않는 회의실 번호가 있습니다. 다른 장소를 대신 선택하지 말고 행사장의 공식 명칭을 확인하세요.")
  val matches=destinations.filter { d->d.patterns.any { it.containsMatchIn(venueText) } }
  val floors=Regex("(?<![\\dB])([1-9][0-9]*)\\s*(?:층|F\\b)",RegexOption.IGNORE_CASE).findAll(venueText).map { it.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE }.toList()
  if(matches.size==1&&floors.any { it!=matches[0].floor.first().digitToInt() })return Decision("review","안내문에 행사장과 다른 층이 있습니다. 이번에 방문할 행사장과 층이 있는 문장만 남겨주세요. 등록 장소와 다른 일정의 안내는 빼고 다시 확인하세요.")
  if(selectedId!=null&&destinations.none { it.id==selectedId })return Decision("invalid","선택한 장소를 찾을 수 없습니다. 장소를 다시 선택하세요.")
  val d=if(selectedId!=null)destinations.find { it.id==selectedId } else matches.singleOrNull()
  if(d==null)return Decision("select",if(matches.size>1&&floors.toSet().size>1)"여러 행사장과 층이 함께 있습니다. 이번에 방문할 행사장과 층이 있는 문장만 남겨주세요. 등록 장소와 다른 일정의 안내는 빼고 다시 확인하세요." else if(matches.size>1)"여러 장소가 있습니다. 방문할 장소를 선택하세요." else "세부 장소를 선택하세요.",matches.ifEmpty { destinations })
  if(matches.isNotEmpty()&&d !in matches)return Decision("conflict","선택한 장소가 입력 안내와 다릅니다.")
  if(floors.any { it!=d.floor.first().digitToInt() })return Decision("review","선택한 행사장과 다른 층이 있습니다. 이번에 방문할 행사장과 층이 있는 문장만 남겨주세요. 등록 장소와 다른 일정의 안내는 빼고 다시 확인하세요.",destination=d)
  if(!parkingNotesAgree(listOf(text),d)||!entryGatesAgree(text,d))return Decision("review","안내문의 주차층·구역·진입문과 공식 추천이 다릅니다. 주최 측과 시설의 최신 안내를 확인하세요.",parkingNotes=parts.parkingNotes)
  val h=if(Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(heightText.trim()))heightText.trim().toDoubleOrNull() else null
  if(h==null||!h.isFinite()||h<1.0||h>5.0)return Decision("invalid","전체 높이를 1.00~5.00m 범위의 숫자로 입력하세요. 단위 없이 소수점 둘째 자리까지 입력할 수 있습니다.",destination=d)
  if(h>2.2)return Decision("blocked","차량 높이가 시설 제한 2.2m를 초과합니다. 이 주차 동선을 사용하지 마세요.",destination=d,height=h)
  if(d.id in conflictingIds)return Decision("source_conflict","공식 자료의 주차 구역이 서로 다릅니다. ${d.name} 주차 브리핑은 확정할 수 없습니다. 코엑스 현장 안내로 확인하세요.",destination=d,height=h)
  return Decision("ready",if(h==2.2)"제한 높이와 같습니다. 현장 표지와 적재물을 확인하세요." else "지원 목록의 장소명 후보를 찾았습니다. 현재 행사장이 맞는지 안내문 원본을 직접 확인하세요.",destination=d,height=h,parkingNotes=parts.parkingNotes)
 }
 fun export(d:Decision):String {
  require(d.status=="ready")
  val p=d.destination!!
  return "Arrival Brief\n코엑스 ${p.name} (${p.floor})\n진입구: ${p.gate}\n추천 주차: ${p.parking}\n차량 전체 높이: ${d.height}m / 시설 제한 2.2m\n${d.message}\n\n실시간 빈자리·입구 좌표·실내 동선·승객 하차 지점은 제공하지 않습니다. 현장 표지와 안내를 우선하세요.\n\n코엑스 공식 회의실 주차안내\n$SOURCE\n자료 확인: $VERIFIED_DATE\n"
 }
}
