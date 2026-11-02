export const DATASET_VERSION = 'coex-2026-10-30-v2';
export const RECENT_DESTINATION_TTL_MS = 24 * 60 * 60 * 1000;
export const SOURCE = {url:'https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/',title:'코엑스 공식 회의실 주차안내 · 일반차량',checkedAt:'2026-10-30'};
export const NOTICE_SOURCE = {url:'https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/',title:'코엑스 공식 주차장·화물차 진입 약도 공지',publishedAt:'2026-02-11',checkedAt:'2026-10-30'};
// The notice includes a separate destination parking table. Its relationship to
// the general-vehicle guide is unresolved, so conflicting recommendations stop.
export const SOURCE_CONFLICTS = {
 conferenceE:[{source:SOURCE,recommendation:'B2 퍼플존'},{source:NOTICE_SOURCE,recommendation:'B2·B3·B4 블루존'}],
 rooms300:[{source:SOURCE,recommendation:'B2 그린존'},{source:NOTICE_SOURCE,recommendation:'B2 퍼플존'}],
 rooms400:[{source:SOURCE,recommendation:'B2 그린존'},{source:NOTICE_SOURCE,recommendation:'B2 퍼플존'}]
};
export const DESTINATIONS = [
 {id:'auditorium',name:'오디토리움',floor:'3F',gate:'북문',gateContext:'봉은사 맞은편',level:'B3',zone:'블루존',color:'blue',patterns:[/오디토리움/i,/auditorium/i]},
 {id:'grand',name:'그랜드볼룸',floor:'1F',gate:'북문',gateContext:'봉은사 맞은편',level:'B3',zone:'블루존',color:'blue',patterns:[/그랜드\s*볼룸/i,/grand\s*ballroom/i,/\bgbr\b/i]},
 {id:'asem',name:'아셈볼룸',floor:'2F',gate:'북문',gateContext:'봉은사 맞은편',level:'B3',zone:'블루존',color:'blue',patterns:[/아셈\s*볼룸/i,/asem\s*ballroom/i]},
 {id:'studio159',name:'스튜디오 159',floor:'2F',gate:'동문',gateContext:'영동대로 방면',level:'B2',zone:'퍼플존',color:'purple',patterns:[/스튜디오\s*159/i,/studio\s*159/i]},
 {id:'conferenceE',name:'컨퍼런스룸 E',floor:'3F',gate:'동문',gateContext:'영동대로 방면',level:'B2',zone:'퍼플존',color:'purple',patterns:[/컨퍼런스\s*룸\s*e\b/i,/conference\s*room\s*e\b/i]},
 {id:'platz',name:'THE PLATZ',floor:'2F',gate:'동문',gateContext:'영동대로 방면',level:'B2',zone:'지하 2층',color:'neutral',patterns:[/\bthe\s*platz\b/i,/더\s*플라츠/i]},
 {id:'rooms300',name:'회의실 300~328호',floor:'3F',gate:'동문',gateContext:'영동대로 방면',level:'B2',zone:'그린존',color:'green',patterns:[/(?:회의실|룸)\s*3(?:0\d|1\d|2[0-8])(?:호|\b)/i,/(?<!\d)3(?:0\d|1\d|2[0-8])호/]},
 {id:'rooms400',name:'회의실 400~403호',floor:'4F',gate:'동문',gateContext:'영동대로 방면',level:'B2',zone:'그린존',color:'green',patterns:[/(?:회의실|룸)\s*40[0-3](?:호|\b)/i,/(?<!\d)40[0-3]호/]}
];
// Changes/cancellations apply to the untouched notice. Explicit venue claims
// must identify a supported destination, and contradictory claims cannot be
// resolved by choosing whichever recognised name happens to appear first.
const OTHER_FACILITY = /킨텍스|벡스코|세텍|예술의전당|aT센터|송도\s*컨벤시아|동대문\s*디자인\s*플라자|(?<![A-Za-z0-9_])(?:KINTEX|BEXCO|SETEC|DDP|Songdo\s+Convensia|Dongdaemun\s+Design\s+Plaza)(?![A-Za-z0-9_])/i;
const NOTICE_CHANGE = /아닌|아니라|아니다|취소|변경|연기|미뤄|미루|미룬|미룹|(?:열(?:리)?|진행(?:되|하)?|개최(?:되|하)?)지\s*않|안\s*(?:열리|열립|진행|개최)|(?:온라인|비대면|줌|zoom)[\s\S]{0,30}전환|전환[\s\S]{0,30}(?:온라인|비대면|줌|zoom)|옮(?:겼|겨|깁|길|김|긴)|바뀌|바꾸|이동|이전(?:되|했|합|할|합니다|\s*예정)|(?:행사|장소|행사장|목적지)\s*(?:를|가|는|이|을)?\s*이전|(?<![A-Za-z0-9_])(?:cancel(?:led|ed|ling|ing|l?ation)?|postponed?|postponement|relocat(?:e|ed|ing|ion)|reschedul(?:e|ed|ing)|(?:moved?|moving|moves|transfer(?:red|ring)?)\s+(?:to|from)|(?:venue|location|destination)\s+(?:(?:has|have)\s+been\s+|(?:has|was|is)\s+)?changed|change\s+of\s+(?:venue|location)|no\s+longer|not\s+(?:at|in|held))(?![A-Za-z0-9_])/i;
const VENUE_CLAIMS = [
 /(?:행사\s*장소|행사장|장소|목적지|개최지|개최\s*장소)\s*(?:은|는|이|가|[:：])\s*([^.!?。\n]+)/gi,
 /(?:행사|공연|회의|모임|컨퍼런스)(?:는|가|을|를)?\s*(?:(?:실제로|이번에|현재|최종적으로)\s*)?([^.!?。\n]+?)에서\s*(?:진행|개최|열|시작|합니다)/gi,
 /\b(?:venue|event\s+location|location|destination)\s*(?:(?:is|will\s+be)\s+|[:：]\s*)([^.!?。\n]+)/gi,
 /\b(?:event|conference|meeting)\s+(?:(?:is|will\s+be|will)\s+)?(?:held|taking\s+place|takes\s+place|take\s+place|hosted)\s+(?:at|in)\s+([^.!?。\n]+)/gi,
];
function isSupportedVenueClaim(claim,destination){
 // Facility ownership must appear inside this declaration. A sponsor elsewhere
 // in the notice and a shared room name do not establish a COEX destination.
 if(!/코엑스|(?<![A-Za-z0-9_])coex(?![A-Za-z0-9_])/i.test(claim))return false;
 let remainder=claim.replace(/코엑스(?:의)?|(?<![A-Za-z0-9_])coex(?![A-Za-z0-9_])/gi,'');
 for(const pattern of destination.patterns)remainder=remainder.replace(new RegExp(pattern.source,'gi'),'');
 remainder=remainder.replace(/[1-9]\d*\s*(?:층|F(?![A-Za-z0-9_]))/gi,'').replace(/삼성동/gi,'')
  .replace(/(?:입니다|에서\s*(?:진행합니다|개최합니다|열립니다|합니다|만나요))\s*$/i,'')
  .replace(/[\s()[\],·:：-]/g,'');
 return !remainder;
}
function hasConflictingVenueClaims(text){
 const claims=VENUE_CLAIMS.flatMap(p=>[...text.matchAll(p)].map(m=>m[1]));
 if(!claims.length)return false;
 const all=DESTINATIONS.filter(d=>d.patterns.some(p=>p.test(text)));
 if(all.length!==1)return true;
 return claims.some(claim=>{
  const matches=DESTINATIONS.filter(d=>d.patterns.some(p=>p.test(claim)));
  return matches.length!==1||matches[0].id!==all[0].id||!isSupportedVenueClaim(claim,matches[0]);
 });
}
function hasUnownedVenueMentions(text){
 // A supported room name needs a local COEX prefix. Sponsorship and other
 // metadata on another line cannot establish physical facility ownership.
 for(const clause of text.split(/\r?\n|(?<=[.!?。])\s+/u)){
  const raw=DESTINATIONS.flatMap(d=>d.patterns.flatMap(p=>[...clause.matchAll(new RegExp(p.source,'gi'))].map(m=>({start:m.index,end:m.index+m[0].length})))).sort((a,b)=>a.start-b.start||b.end-a.end);
  const hits=[];for(const hit of raw)if(!hits.length||hit.start>=hits.at(-1).end)hits.push(hit);
  if(!hits.length)continue;
  if(/^\s*(?:후원|주최|주관|협찬|sponsor(?:ed\s+by)?|organizer|hosted\s+by)\s*[:：]/i.test(clause))return true;
  if(!/(?:코엑스|(?<![A-Za-z0-9_])coex)\s*(?:삼성동\s*)?(?:(?:오전|오후)\s*)?(?:회의실\s*)?(?:[1-9]\d*\s*(?:층|F)\s*)?$/i.test(clause.slice(0,hits[0].start)))return true;
  for(let i=1;i<hits.length;i++){
   const gap=clause.slice(hits[i-1].end,hits[i].start).replace(/[1-9]\d*\s*(?:층|F(?![A-Za-z0-9_]))/gi,'').replace(/(?:오전|오후|및|또는|혹은|과|와)|(?<![A-Za-z0-9_])(?:and|or)(?![A-Za-z0-9_])/gi,'').replace(/[\s()[\],·/]/g,'');
   if(gap)return true;
  }
 }
 return false;
}
// Separate only complete, explicitly labelled parking clauses with a narrow
// vocabulary. Unknown words remain venue input; original text still undergoes
// facility/change/cancellation checks. Parking notes never determine the route.
const PARKING_FLOOR = /(?<![A-Za-z0-9])B([1-9]\d*)\s*(?:층|F(?![A-Za-z0-9])|(?![A-Za-z0-9]))|지하\s*([1-9]\d*)\s*(?:층|F\b)/gi;
const PARKING_ZONE = /(?:블루\s*존|퍼플\s*존|그린\s*존)(?:을|를|은|는)?|(?<![A-Za-z0-9_])(?:blue|purple|green)\s*zone(?![A-Za-z0-9_])/gi;
const ENTRY_GATE = /북문|동문|남문|서문|(?<![A-Za-z0-9_])(?:(?:north|east|south|west)\s*(?:gate|entrance)|(?:gate|entrance)\s*[:：]?\s*(?:north|east|south|west))(?![A-Za-z0-9_])/gi;
const OPERATIONAL_RESTRICTION = /폐쇄|통제|불가|불능|할\s*수\s*없|못\s*(?:이용|방문|접근|참석|입장)|(?:이용|진입|출입|입차|사용)\s*금지|(?<![A-Za-z0-9_])(?:closed|closure|restricted|no\s+(?:entry|parking)|do\s+not\s+enter|(?:cannot|can't|unable\s+to)\s+(?:visit|reach|enter|use|access))(?![A-Za-z0-9_])/i;
// The parser does not interpret permission/denial grammar. Any unparsed access
// operation clause is held for review; event admission wording is outside this
// vocabulary. Only the exact, static official height fact is exempted.
const PARKING_REFERENCE = /주차|(?<![A-Za-z0-9_])(?:parking|car\s+park)(?![A-Za-z0-9_])/i;
const ACCESS_OPERATION = /진입|출입|입차|출차|통행|입구|출구|정문|후문|옆문|쪽문|대문|북문|동문|남문|서문|(?<![A-Za-z0-9_])(?:gate|entrance|entry|exit|access|drive[\s-]?in)(?![A-Za-z0-9_])/i;
// Facility status is not live data. Hold any unparsed operating-status mention,
// including reopening or positive operating wording, rather than inferring
// availability from whether a particular negative suffix was recognised.
const FACILITY_OPERATION = /휴관|휴장|휴무|휴업|폐관|폐장|폐업|폐쇄|개관|개장|개업|운영|영업|중단|재개|비개최|미개최|쉬는\s*날|문\s*(?:을|를|이|가|은|는)?\s*(?:닫|열|엽)/i;
// A physical room name does not establish that an event exists or is on-site.
// Remote/hybrid and non-occurrence mentions are unverified event-state clauses.
const EVENT_STATUS_REFERENCE = /무산|불성립|비개최|미개최|비대면|온라인|원격|하이브리드|(?<![A-Za-z0-9_])(?:zoom|online|remote|hybrid)(?![A-Za-z0-9_])|(?:행사|공연|회의|모임)\s*(?:가|는|은|이)?\s*없/i;
// Do not infer occurrence from a matching room when an event clause contains
// negation. Preserve the entire clause, including unfamiliar predicate wording.
const EVENT_SUBJECT = /행사|공연|회의|모임|강연|세미나|컨퍼런스|워크숍|(?<![A-Za-z0-9_])(?:events?|conferences?|meetings?|seminars?|workshops?|shows?|concerts?|talks?)(?![A-Za-z0-9_])/i;
const EVENT_NEGATION = /(?:안|못)\s*(?:하|해|합|한|열|오|가|갑|참|진|개)|않|없|(?<![A-Za-z0-9_])(?:no|not|never|without|isn['’]?t|aren['’]?t|wasn['’]?t|weren['’]?t|won['’]?t|doesn['’]?t|don['’]?t|didn['’]?t|cannot|can['’]?t)(?![A-Za-z0-9_])/i;
// Lifecycle statements are unverified even when they describe a scheduled end.
const EVENT_LIFECYCLE = /종료|끝(?:났|나|납|난|낼|냅)|마쳤|마침|완료|(?<![A-Za-z0-9_])(?:end(?:ed|ing|s)?|over|complet(?:e|ed|ion)|finish(?:ed|ing|es)?|conclud(?:e|ed|ing))(?![A-Za-z0-9_])/i;
// Status clauses often omit the event subject after a venue line.
const SUBJECTLESS_COMPLETION = /종료|끝(?:났|나|납|난|낼|냅)|마쳤|완료|(?<![A-Za-z0-9_])(?:ended|ending|ends|completed|finished|concluded)(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])(?:it|this|that)\s*(?:is|was|'s|’s)\s+over(?![A-Za-z0-9_])/i;
const KNOWN_HEIGHT_NOTICE = /^(?:(?:(?:코엑스|시설)(?:의)?\s*)?(?:(?:지하\s*)?주차장\s*)?(?:차량\s*)?(?:진입\s*)?제한\s*높이\s*[:：]?\s*2\.20?\s*(?:m|미터)(?:입니다)?|(?:(?:facility|parking|car\s+park)\s+)?(?:(?:entry|entrance|access|drive[\s-]?in)\s+)?(?:height\s+limit|maximum\s+height)\s*[:：]?\s*2\.20?\s*m)[.!。]?$/i;
function noticeParts(text){
 const parkingNotes=[],unparsedParkingNotes=[],entryNotes=[],facilityNotes=[],eventNotes=[],venueClauses=[];
 for(const original of text.split(/\r?\n|(?<=[.!?。])\s+/u)){
  const clause=original.trim().replace(/^[-•]\s*/, '');
  if(KNOWN_HEIGHT_NOTICE.test(clause)){venueClauses.push(original);continue;}
  const labelled=/^(?:주차(?:\s*(?:안내|정보|구역))?|parking(?:\s*(?:info|information))?)(?:\s*[:：]\s*|\s*는\s*|\s+)/i.exec(clause);
  const locationFirst=/^(?:지하\s*[1-9]\d*\s*(?:층|F\b)|B[1-9]\d*\s*(?:층|F(?![A-Za-z0-9])|(?![A-Za-z0-9])))/i.test(clause)&&/주차장/.test(clause);
  const body=labelled?clause.slice(labelled[0].length):clause;
  const hasLocation=[...body.matchAll(PARKING_FLOOR)].length>0||[...body.matchAll(PARKING_ZONE)].length>0;
  const remainder=body.replace(PARKING_FLOOR,'').replace(PARKING_ZONE,'')
   .replace(/주차장(?:을|를|은|는)?|이용(?:해|하여)\s*(?:주세요|주십시오)|이용\s*바랍니다|이용하세요|이용|입니다/gi,'')
   .replace(/[\s()[\],·/.!?。]/g,'');
  if((labelled||locationFirst)&&hasLocation&&!remainder)parkingNotes.push(original.trim());
  else {
   venueClauses.push(original);
   if(PARKING_REFERENCE.test(clause))unparsedParkingNotes.push(original.trim());
   if(ACCESS_OPERATION.test(clause))entryNotes.push(original.trim());
   if(FACILITY_OPERATION.test(clause))facilityNotes.push(original.trim());
   if(SUBJECTLESS_COMPLETION.test(clause)||EVENT_STATUS_REFERENCE.test(clause)||(EVENT_SUBJECT.test(clause)&&(EVENT_NEGATION.test(clause)||EVENT_LIFECYCLE.test(clause))))eventNotes.push(original.trim());
  }
 }
 return {venueText:venueClauses.join('\n'),parkingNotes,unparsedParkingNotes,entryNotes,facilityNotes,eventNotes};
}
function parkingNotesAgree(notes,destination){
 const expectedFloor=Number(destination.level.slice(1));
 for(const note of notes){
  if([...note.matchAll(PARKING_FLOOR)].some(m=>Number(m[1]||m[2])!==expectedFloor))return false;
  for(const m of note.matchAll(PARKING_ZONE)){
   const zone=/블루|blue/i.test(m[0])?'blue':/퍼플|purple/i.test(m[0])?'purple':'green';
   if(zone!==destination.color)return false;
  }
 }
 return true;
}
function entryGatesAgree(text,destination){
 return [...text.matchAll(ENTRY_GATE)].every(m=>{
  const gate=/북문|north/i.test(m[0])?'북문':/동문|east/i.test(m[0])?'동문':/남문|south/i.test(m[0])?'남문':'서문';
  return gate===destination.gate;
 });
}
export function extractDestinations(text) {
 if(typeof text!=='string'||text.length>10000)return {status:'invalid',matches:[],message:'행사 안내는 10,000자 이내로 입력해주세요.'};
 if(!text.trim())return {status:'empty',matches:[],message:'행사 안내에서 코엑스와 세부 장소를 확인합니다.'};
 if(!/코엑스|\bcoex\b/i.test(text)||/마곡|\bmagok\b/i.test(text))return {status:'unsupported',matches:[],message:'현재 코엑스 삼성동의 회의실 8개 범위만 식별합니다. 다른 시설은 안내하지 않습니다.'};
 const parts=noticeParts(text),venueText=parts.venueText;
 if(parts.unparsedParkingNotes.length||parts.entryNotes.length||parts.facilityNotes.length||parts.eventNotes.length)return {status:'needs_review',matches:[],...parts,message:'해석하지 못한 행사 상태·시설 운영·주차·출입 안내가 있습니다. 실제 현장 개최 여부와 이용 가능 상태를 주최 측·시설의 최신 안내로 확인해주세요. 원문 정보를 임의로 바꾸거나 생략하지 마세요.'};
 if(OTHER_FACILITY.test(text)||NOTICE_CHANGE.test(text)||hasConflictingVenueClaims(text)||hasUnownedVenueMentions(text))return {status:'needs_review',matches:[],message:'다른 시설, 장소 변경·취소 또는 시설 귀속이 확인되지 않은 장소 안내가 있습니다. 현재 방문할 장소를 “코엑스 세부장소 층”으로 확인하고, 원문의 다른 시설·제약 정보를 임의로 생략하지 마세요.'};
 if(/호텔|\bhotel\b|인터컨티넨탈|intercontinental|파르나스|parnas|웨스틴|westin|(?:코엑스|\bcoex\b).{0,20}(?:인근|근처|주변|맞은편|건너편)|(?:인근|근처|주변|맞은편|건너편).{0,20}(?:코엑스|\bcoex\b)|\b(?:near|nearby|opposite|across\s+from)\b.{0,25}\bcoex\b|\bcoex\b.{0,25}\b(?:near|nearby|opposite|across\s+from)\b/is.test(text))return {status:'needs_review',matches:[],message:'코엑스 외부 또는 호텔을 가리키는 표현이 있습니다. 코엑스 삼성동 내부 행사장인지 확인하고 해당 장소의 안내만 남겨주세요.'};
 // Matching a gate/zone name cannot prove that it is open or usable. Only a
 // completely interpreted parking clause may leave the venue text; every
 // remaining gate/zone mention requires review regardless of source agreement.
 if(OPERATIONAL_RESTRICTION.test(text)||[...venueText.matchAll(PARKING_ZONE)].length||[...venueText.matchAll(ENTRY_GATE)].length)return {status:'needs_review',matches:[],...parts,message:'주차 구역·진입문에 관한 해석되지 않은 안내 또는 운영 제한 표현이 있습니다. 같은 구역명이어도 이용 가능하다고 판단하지 않습니다. 원문을 생략하지 말고 시설의 최신 운영 안내를 확인해주세요.'};
 if(/지하\s*[1-9]\d*\s*(?:층|F\b)|(?<![A-Za-z0-9])B[1-9]\d*\s*(?:층|F(?![A-Za-z0-9])|(?![A-Za-z0-9]))|\bbasement\b/i.test(venueText))return {status:'needs_review',matches:[],message:'행사장 지하층 또는 해석하지 못한 주차 표현이 있습니다. 원문의 행사장과 주차층·구역을 직접 확인하고, 정보를 바꾸거나 생략하지 않은 별도 주차 안내를 입력해주세요.'};
 if(/(?:[A-Z]\s*홀|\bhall\s*[A-Z]\b|전시(?:홀|관|장))/i.test(venueText))return {status:'needs_review',matches:[],message:'지원하지 않는 전시홀 또는 전시장 표현이 있습니다. 다른 회의실로 대신 선택하지 말고 행사장의 공식 명칭을 확인해주세요.'};
 const roomNumbers=[...venueText.matchAll(/(?:회의실|(?<!볼)룸|\broom)\s*(\d{1,4})(?:호|\b(?!\s*(?:층|F\b)))|(?<!\d)(\d{3,4})호/gi)].map(m=>Number(m[1]||m[2]));
 if(roomNumbers.some(n=>!(n>=300&&n<=328||n>=400&&n<=403)))return {status:'needs_review',matches:[],message:'지원하지 않는 회의실 번호가 있습니다. 다른 장소를 대신 선택하지 말고 행사장의 공식 명칭을 확인해주세요.'};
 const matches=DESTINATIONS.filter(d=>d.patterns.some(p=>p.test(venueText)));
 const floors=extractFloors(venueText);
 if(matches.length===1&&floors.length&&floors.some(f=>f!==parseInt(matches[0].floor)))return {status:'needs_review',matches:[],message:'안내문에 행사장과 다른 층이 있습니다. 이번에 방문할 행사장과 층이 있는 문장만 남겨주세요. 등록 장소와 다른 일정의 안내는 빼고 다시 확인해주세요.'};
 if(matches.length===1&&(!parkingNotesAgree([text],matches[0])||!entryGatesAgree(text,matches[0])))return {status:'needs_review',matches:[],...parts,message:'안내문의 주차층·구역·진입문과 공식 추천이 다릅니다. 주최 측과 시설의 최신 안내를 확인해주세요.'};
 return {...parts,status:matches.length===1?'matched':matches.length>1?'ambiguous':'unmatched',matches,message:matches.length>1?(new Set(floors).size>1?'여러 행사장과 층이 함께 있습니다. 이번에 방문할 행사장과 층이 있는 문장만 남겨주세요. 등록 장소와 다른 일정의 안내는 빼고 다시 확인해주세요.':'안내에 여러 장소가 있습니다. 실제 방문할 장소를 선택해주세요.'):matches.length?'지원 목록의 장소명 후보를 찾았습니다. 현재 행사장이 맞는지 안내문 원본을 직접 확인해주세요.':'코엑스 내 세부 장소를 찾지 못했습니다. 지원 장소를 직접 선택해주세요.'};
}
function extractFloors(text){return [...text.matchAll(/(?<![\dB])([1-9]\d*)\s*(?:층|F\b)/gi)].map(m=>Number(m[1]));}
export function buildBrief({text='',destinationId='',height='',mode='parked',confirmed=false}={}){
 const extraction=extractDestinations(text);
 const allowedModes=['parked','driving','unknown'];
 if(!allowedModes.includes(mode))return {status:'invalid',reason:'차량 상태를 확인할 수 없습니다.',canConfirm:false};
 if(extraction.status==='invalid'||extraction.status==='unsupported'||extraction.status==='empty'||extraction.status==='needs_review')return {status:extraction.status,reason:extraction.message,extraction,canConfirm:false};
 const selected=DESTINATIONS.find(d=>d.id===destinationId);
 if(destinationId&&!selected)return {status:'invalid',reason:'지원 목록에서 방문할 장소를 다시 선택해주세요.',extraction,canConfirm:false};
 const destination=selected|| (extraction.matches.length===1?extraction.matches[0]:null);
 if(!destination)return {status:extraction.status,reason:extraction.message,extraction,canConfirm:false};
 if(extraction.matches.length&&destinationId&&!extraction.matches.some(d=>d.id===destinationId))return {status:'conflict',reason:'선택 장소와 입력한 행사 안내가 다릅니다. 행사 안내를 수정해주세요.',extraction,canConfirm:false};
 if(extractFloors(extraction.venueText??text).some(f=>f!==parseInt(destination.floor)))return {status:'needs_review',reason:'선택한 행사장과 다른 층이 있습니다. 이번에 방문할 행사장과 층이 있는 문장만 남겨주세요. 등록 장소와 다른 일정의 안내는 빼고 다시 확인해주세요.',extraction,canConfirm:false};
 if(!parkingNotesAgree([text],destination)||!entryGatesAgree(text,destination))return {status:'needs_review',reason:'안내문의 주차층·구역·진입문과 공식 추천이 다릅니다. 주최 측과 시설의 최신 안내를 확인해주세요.',extraction,canConfirm:false};
 const heightText=height==null?'':String(height).trim();
 const heightValue=heightText===''?null:/^[0-9]+(?:\.[0-9]{1,2})?$/.test(heightText)?Number(heightText):NaN;
 if(heightValue!==null&&(!Number.isFinite(heightValue)||heightValue<1||heightValue>5))return {status:'invalid',reason:'차량 전체 높이는 1.00~5.00m 범위로 입력해주세요.',extraction,canConfirm:false};
 if(heightValue>2.2)return {status:'blocked',reason:'차량 높이가 공식 지하주차장 진입 제한 2.2m를 초과합니다. 이 주차 동선을 사용하지 마세요.',destination,extraction,canConfirm:false};
 if(SOURCE_CONFLICTS[destination.id])return {status:'source_conflict',reason:'공식 자료의 추천 주차 구역이 서로 다릅니다. 시설의 최신 안내와 현장 표지를 확인할 때까지 진입문·주차 구역을 확정하지 않습니다.',destination,extraction,evidence:SOURCE_CONFLICTS[destination.id],canConfirm:false};
 const warnings=['빈 주차면·혼잡도·진입 가능 여부는 실시간 확인되지 않았습니다.','지하 실내 길 안내와 승객 하차 지점은 제공하지 않습니다. 현장 표지와 안내를 우선하세요.'];
 if(heightValue===null)warnings.unshift('차량 높이 미입력: 루프박스 등을 포함한 전체 높이를 먼저 확인해주세요.');
 if(heightValue===2.2)warnings.unshift('제한 높이와 동일합니다. 적재물·현장 표지를 확인하고 여유 높이가 없으면 진입하지 마세요.');
 return {status:heightValue===null?'needs_height':'ready',destination,extraction,height:heightValue,limit:2.2,source:SOURCE,warnings,canConfirm:mode==='parked'&&heightValue!==null,confirmed:confirmed&&mode==='parked'&&heightValue!==null};
}
export function formatBrief(brief){
 if(!brief.destination||brief.status!=='ready')throw new Error('확정 가능한 브리핑이 없습니다.');
 const d=brief.destination;
 return `Arrival Brief | 코엑스 ${d.name} (${d.floor})\n\n진입구: ${d.gate} (${d.gateContext})\n추천 주차: ${d.level} ${d.zone}\n차량 높이: ${brief.height.toFixed(2)}m / 시설 제한: 2.2m\n\n${brief.warnings.join('\n')}\n\n출처: ${SOURCE.title}\n${SOURCE.url}\n자료 확인: ${SOURCE.checkedAt}\n\n본 문서는 출발 전 확인용 브리핑이며 실시간 내비게이션이 아닙니다.\n`;
}


/** UI confirmation requires explicit checks in addition to a valid destination. */
export function reviewAllowsConfirmation(brief,{mode='unknown',heightReviewed=false,parkedConfirmed=false,venueReviewed=false}={}){
 return brief?.status==='ready' && brief.canConfirm===true && mode==='parked' && heightReviewed===true && parkedConfirmed===true && venueReviewed===true;
}

/** Only an agreed destination is remembered. Invitation text and height never enter the record. */
export function createRecentDestination(destinationId,now=Date.now()){
 if(!DESTINATIONS.some(d=>d.id===destinationId)||SOURCE_CONFLICTS[destinationId]||!Number.isSafeInteger(now)||now<0)throw new Error('저장할 수 없는 장소입니다.');
 return {destinationId,datasetVersion:DATASET_VERSION,savedAt:now};
}
export function inspectRecentDestination(raw,now=Date.now()){
 if(raw==null)return {status:'empty'};
 let record;
 try{record=typeof raw==='string'?JSON.parse(raw):raw;}catch{return {status:'invalid'};}
 if(!record||typeof record!=='object'||Array.isArray(record)||Object.keys(record).sort().join(',')!=='datasetVersion,destinationId,savedAt'||!Number.isSafeInteger(record.savedAt)||record.savedAt<0||!Number.isSafeInteger(now)||now<record.savedAt)return {status:'invalid'};
 if(record.datasetVersion!==DATASET_VERSION)return {status:'version_mismatch'};
 if(now-record.savedAt>=RECENT_DESTINATION_TTL_MS)return {status:'expired'};
 const destination=DESTINATIONS.find(d=>d.id===record.destinationId);
 if(!destination||SOURCE_CONFLICTS[destination.id])return {status:'unavailable'};
 return {status:'available',destination,savedAt:record.savedAt};
}
