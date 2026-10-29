export const SOURCE = {url:'https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/',title:'코엑스 공식 회의실 주차안내 · 일반차량',checkedAt:'2026-10-28'};
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
export function extractDestinations(text) {
 if(typeof text!=='string'||text.length>10000)return {status:'invalid',matches:[],message:'행사 안내는 10,000자 이내로 입력해주세요.'};
 if(!text.trim())return {status:'empty',matches:[],message:'행사 안내에서 코엑스와 세부 장소를 확인합니다.'};
 if(!/코엑스|\bcoex\b/i.test(text)||/코엑스\s*마곡|coex\s*magok/i.test(text))return {status:'unsupported',matches:[],message:'현재 코엑스 삼성동의 회의실 8개 범위만 지원합니다. 다른 시설은 안내하지 않습니다.'};
 if(/(?:킨텍스|벡스코|세텍|예술의전당|aT센터|아닌|아니라|아니다|취소|장소.{0,12}(?:변경|이전)|(?:변경|취소).{0,20}(?:목적지|행사장)|회의실.{0,12}취소)/i.test(text))return {status:'needs_review',matches:[],message:'다른 시설 또는 장소 변경 표현이 있습니다. 현재 유효한 행사장과 장소만 남겨 다시 확인해주세요.'};
 const matches=DESTINATIONS.filter(d=>d.patterns.some(p=>p.test(text)));
 const floors=[...text.matchAll(/(?<![\dB])([1-9])(?:층|F\b)/gi)].map(m=>Number(m[1]));
 if(matches.length===1&&floors.length&&floors.some(f=>f!==parseInt(matches[0].floor)))return {status:'needs_review',matches:[],message:'입력한 층과 공식 안내의 장소 층이 다릅니다. 행사장 정보를 다시 확인해주세요.'};
 return {status:matches.length===1?'matched':matches.length>1?'ambiguous':'unmatched',matches,message:matches.length>1?'안내에 여러 장소가 있습니다. 실제 방문할 장소를 선택해주세요.':matches.length?'공식 시설 안내와 일치하는 장소를 찾았습니다.':'코엑스 내 세부 장소를 찾지 못했습니다. 지원 장소를 직접 선택해주세요.'};
}
export function buildBrief({text='',destinationId='',height='',mode='parked',confirmed=false}={}){
 const extraction=extractDestinations(text);
 const allowedModes=['parked','driving','unknown'];
 if(!allowedModes.includes(mode))return {status:'invalid',reason:'차량 상태를 확인할 수 없습니다.',canConfirm:false};
 if(extraction.status==='invalid'||extraction.status==='unsupported'||extraction.status==='empty'||extraction.status==='needs_review')return {status:extraction.status,reason:extraction.message,extraction,canConfirm:false};
 const destination=DESTINATIONS.find(d=>d.id===destinationId)|| (extraction.matches.length===1?extraction.matches[0]:null);
 if(!destination)return {status:extraction.status,reason:extraction.message,extraction,canConfirm:false};
 if(extraction.matches.length&&destinationId&&!extraction.matches.some(d=>d.id===destinationId))return {status:'conflict',reason:'선택 장소와 입력한 행사 안내가 다릅니다. 행사 안내를 수정해주세요.',extraction,canConfirm:false};
 const heightValue=height===''||height==null?null:Number(height);
 if(heightValue!==null&&(!Number.isFinite(heightValue)||heightValue<1||heightValue>5))return {status:'invalid',reason:'차량 전체 높이는 1.00~5.00m 범위로 입력해주세요.',extraction,canConfirm:false};
 if(heightValue>2.2)return {status:'blocked',reason:'차량 높이가 공식 지하주차장 진입 제한 2.2m를 초과합니다. 이 주차 동선을 사용하지 마세요.',destination,extraction,canConfirm:false};
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
