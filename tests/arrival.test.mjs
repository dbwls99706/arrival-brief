import {readFileSync} from 'node:fs';
import test from 'node:test';import assert from 'node:assert/strict';import {extractDestinations,buildBrief,formatBrief,DESTINATIONS,reviewAllowsConfirmation,createRecentDestination,inspectRecentDestination,RECENT_DESTINATION_TTL_MS,DATASET_VERSION} from '../dist/arrival/engine.mjs';
test('official destination mappings',()=>{for(const [name,gate,zone] of [['오디토리움','북문','블루존'],['그랜드볼룸','북문','블루존'],['아셈볼룸','북문','블루존'],['스튜디오159','동문','퍼플존'],['THE PLATZ','동문','지하 2층']]){const b=buildBrief({text:`코엑스 ${name}`,height:1.65});assert.equal(b.status,'ready');assert.equal(b.destination.gate,gate);assert.equal(b.destination.zone,zone);}});
test('reject unsupported facilities including Magok',()=>{for(const text of ['예술의전당 오디토리움','코엑스 마곡 오디토리움','COEX MAGOK auditorium','마곡 코엑스 오디토리움','MAGOK COEX auditorium'])assert.equal(buildBrief({text,height:1.65}).status,'unsupported');});
test('ambiguous destinations require selection',()=>{const text='코엑스 오디토리움과 스튜디오159';assert.equal(buildBrief({text,height:1.65}).status,'ambiguous');const selected=buildBrief({text,height:1.65,destinationId:'studio159'});assert.equal(selected.destination.gate,'동문');assert.equal(selected.canConfirm,true);});
test('selected destination cannot contradict parsed ticket',()=>{assert.equal(buildBrief({text:'코엑스 오디토리움',height:1.65,destinationId:'conferenceE'}).status,'conflict');});
test('height fail closed for missing, invalid and over limit',()=>{for(const height of [2.21,3])assert.equal(buildBrief({text:'코엑스 오디토리움',height}).status,'blocked');for(const height of ['NaN',0,99])assert.equal(buildBrief({text:'코엑스 오디토리움',height}).status,'invalid');assert.equal(buildBrief({text:'코엑스 오디토리움',height:''}).canConfirm,false);assert.equal(buildBrief({text:'코엑스 오디토리움',height:2.2}).warnings.length,3);});
test('driving and unknown state prohibit confirmation',()=>{for(const mode of ['driving','unknown'])assert.equal(buildBrief({text:'코엑스 오디토리움',height:1.65,mode}).canConfirm,false);});
test('exports contain source and limitations and no loading instruction',()=>{const s=formatBrief(buildBrief({text:'코엑스 오디토리움',height:1.65}));assert.match(s,/https:\/\/business.coex/);assert.match(s,/실시간/);assert.doesNotMatch(s,/하역장|8호기/);});
test('numeric dates and long text do not fabricate places',()=>{assert.equal(extractDestinations('코엑스 2026-10-28 13:00').status,'unmatched');assert.equal(extractDestinations('a'.repeat(10001)).status,'invalid');});
test('unmatched COEX room can be explicitly selected',()=>{const b=buildBrief({text:'코엑스 행사 안내',height:1.7,destinationId:'studio159'});assert.equal(b.destination.name,'스튜디오 159');assert.equal(b.status,'ready');});
test('all supported places have no invented coordinates',()=>{assert.equal(DESTINATIONS.length,8);for(const d of DESTINATIONS)assert.equal('lat' in d,false);});

test('contradictory venue and floor fail closed',()=>{for(const text of ['코엑스가 아닌 킨텍스 오디토리움에 갑니다','코엑스 회의실301호는 취소, 목적지는 벡스코입니다','코엑스 오디토리움 2층 행사','코엑스 오디토리움 13층 행사','코엑스 오디토리움 13F']){const b=buildBrief({text,height:1.65});assert.equal(b.status,'needs_review');assert.equal(b.canConfirm,false);}});

test('cancelled event and F-style floor fail closed',()=>{for(const text of ['코엑스 오디토리움 행사는 취소','코엑스 오디토리움이 아니다','코엑스 오디토리움 2F'])assert.equal(buildBrief({text,height:1.65}).status,'needs_review');});


test('conflicting official sources block confirmation and export for every affected range',()=>{
 for(const [name,id] of [['컨퍼런스룸 E','conferenceE'],['회의실 300호','rooms300'],['회의실 328호','rooms300'],['회의실 400호','rooms400'],['회의실 403호','rooms400']]){
  for(const mode of ['parked','driving','unknown']){
   const brief=buildBrief({text:`코엑스 ${name}`,height:1.65,mode,confirmed:true});
   assert.equal(brief.status,'source_conflict');assert.equal(brief.destination.id,id);assert.equal(brief.canConfirm,false);
   assert.equal(brief.evidence.length,2);assert.notEqual(brief.evidence[0].recommendation,brief.evidence[1].recommendation);
   assert.throws(()=>formatBrief(brief),/확정 가능한/);
  }
 }
 assert.equal(buildBrief({text:'코엑스 컨퍼런스룸 E',height:2.3}).status,'blocked');
});

test('manual destination selection cannot bypass floor validation or accept an unknown ID',()=>{
 for(const args of [
  {text:'코엑스 3층 행사',destinationId:'studio159'},
  {text:'코엑스 오디토리움과 스튜디오159 3층',destinationId:'studio159'},
  {text:'코엑스 13층 행사',destinationId:'auditorium'}
 ]){const brief=buildBrief({...args,height:1.65});assert.equal(brief.status,'needs_review');assert.equal(brief.canConfirm,false);}
 assert.equal(buildBrief({text:'코엑스 오디토리움',destinationId:'typo',height:1.65}).status,'invalid');
 // A basement parking reference must not masquerade as the event floor.
 assert.equal(buildBrief({text:'코엑스 오디토리움 3F, 주차 B3F',height:1.65}).status,'needs_review');
});


test('basement and nearby hotel descriptions cannot produce a confirmed route',()=>{
 for(const text of ['코엑스 오디토리움 지하 3층 행사','코엑스 오디토리움 B3F 행사','COEX auditorium B3','COEX auditorium basement','코엑스 맞은편 호텔 그랜드볼룸 1층에서 행사','코엑스 인근 그랜드볼룸 1층 행사','Hotel near COEX grand ballroom 1F','grand ballroom opposite COEX 1F']){
  const b=buildBrief({text,height:1.65,destinationId:text.toLowerCase().includes('ballroom')||text.includes('볼룸')?'grand':'auditorium'});
  assert.equal(b.status,'needs_review',text);assert.equal(b.canConfirm,false,text);
 }
});

test('an explicit unsupported room cannot be replaced by a supported manual choice',()=>{
 for(const text of ['코엑스 회의실 329호','코엑스 999호 행사','코엑스 회의실 201호','COEX room 500','코엑스 오디토리움 3층과 회의실 329호']){
  const b=buildBrief({text,height:1.65,destinationId:'auditorium'});assert.equal(b.status,'needs_review',text);assert.equal(b.canConfirm,false,text);
 }
 assert.equal(buildBrief({text:'코엑스 그랜드볼룸 1층',height:1.65}).status,'ready');
 assert.equal(buildBrief({text:'코엑스 회의실 3층 오디토리움',height:1.65}).status,'ready');
});

test('confirmation requires current venue, actual height and safe parking reviews',()=>{
 const b=buildBrief({text:'코엑스 오디토리움 3층 행사',height:1.65});
 for(const review of [{},{mode:'parked'},{mode:'parked',heightReviewed:true,venueReviewed:true},{mode:'parked',parkedConfirmed:true,venueReviewed:true},{mode:'driving',heightReviewed:true,parkedConfirmed:true,venueReviewed:true},{mode:'unknown',heightReviewed:true,parkedConfirmed:true,venueReviewed:true},{mode:'parked',heightReviewed:true,parkedConfirmed:true},{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:false}])assert.equal(reviewAllowsConfirmation(b,review),false);
 assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),true);
 assert.equal(reviewAllowsConfirmation(buildBrief({text:'코엑스 컨퍼런스룸 E',height:1.65}),{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false);
});

test('height input follows the same decimal contract as Android',()=>{
 for(const height of ['1e0','0x2','1.234','1,65','Infinity','NaN'])assert.equal(buildBrief({text:'코엑스 오디토리움',height}).status,'invalid',height);
 for(const height of ['1.65',' 1.65 ',2,2.2])assert.equal(buildBrief({text:'코엑스 오디토리움',height}).status,'ready');
});

test('remembered destination contains no raw notice or height and expires after 24 hours',()=>{
 const now=1800000000000,record=createRecentDestination('auditorium',now);
 assert.deepEqual(Object.keys(record).sort(),['datasetVersion','destinationId','savedAt']);
 assert.equal(inspectRecentDestination(JSON.stringify(record),now+RECENT_DESTINATION_TTL_MS-1).status,'available');
 assert.equal(inspectRecentDestination(JSON.stringify(record),now+RECENT_DESTINATION_TTL_MS).status,'expired');
 assert.equal(inspectRecentDestination({...record,datasetVersion:'old'},now).status,'version_mismatch');
 assert.equal(inspectRecentDestination({...record,savedAt:now+1},now).status,'invalid');
 assert.equal(inspectRecentDestination({...record,height:1.65},now).status,'invalid');
 assert.equal(inspectRecentDestination({...record,destinationId:'conferenceE'},now).status,'unavailable');
 assert.equal(inspectRecentDestination({...record,destinationId:'missing'},now).status,'unavailable');
 assert.equal(inspectRecentDestination('{invalid',now).status,'invalid');
 assert.throws(()=>createRecentDestination('conferenceE',now));
 assert.throws(()=>createRecentDestination('missing',now));
 const restored=inspectRecentDestination(record,now);
 const draft=buildBrief({text:`코엑스 ${restored.destination.name}`,height:'',destinationId:restored.destination.id});
 assert.equal(draft.canConfirm,false);assert.equal(reviewAllowsConfirmation(draft,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false);
 assert.equal(record.datasetVersion,DATASET_VERSION);
});


test('shared Android and web decision fixtures reject the same unsafe drafts',()=>{
 const lines=readFileSync(new URL('./decision-cases.tsv',import.meta.url),'utf8').trimEnd().split('\n').slice(1);
 for(const line of lines){const [id,text,height,destinationId,expected]=line.split('\t');const b=buildBrief({text:text.replaceAll('\\n','\n'),height,destinationId});assert.equal(b.status==='ready',expected==='true',id);}
 assert.ok(lines.length>=60);assert.equal(new Set(lines.map(l=>l.split('\t')[0])).size,lines.length);
});


test('multiline invitations cannot bypass nearby venue or changed location review',()=>{
 for(const text of ['코엑스\n맞은편 오디토리움','코엑스\n근처 그랜드볼룸','코엑스 오디토리움 장소\n변경']){
  const brief=buildBrief({text,height:1.65});assert.equal(brief.status,'needs_review',text);assert.equal(brief.canConfirm,false,text);
 }
});


test('explicit parking clauses remain inspectable without becoming venue floors',()=>{
 const text='11월 7일 코엑스 오디토리움 3층에서 만나요. 주차는 B3 블루존입니다.';
 const b=buildBrief({text,height:1.65});
 assert.equal(b.status,'ready');
 assert.deepEqual(b.extraction.parkingNotes,['주차는 B3 블루존입니다.']);
 assert.equal(b.extraction.venueText,'11월 7일 코엑스 오디토리움 3층에서 만나요.');
 assert.equal(b.destination.level,'B3');
 assert.equal(b.destination.zone,'블루존');
});
test('parking details never override source or explicit facility conflicts',()=>{
 for(const text of [
  '코엑스 오디토리움 3층\n주차: B2 블루존',
  '코엑스 오디토리움 3층\n주차: B3 퍼플존',
  '코엑스 오디토리움 3층\n주차: B3 블루존. 장소 변경',
  '코엑스 오디토리움 3층\n주차: B3 블루존 행사장 지하 2층',
  '코엑스 오디토리움 3층, 주차 B3 블루존',
 ])assert.equal(buildBrief({text,height:1.65}).canConfirm,false,text);
});

test('changed, cancelled and explicitly contradicted venues cannot be manually overridden',()=>{
 for(const text of [
  '코엑스 오디토리움에서 진행하던 행사를 송도컨벤시아로 옮겼습니다.',
  '코엑스 오디토리움 3층. 실제 행사장은 송도컨벤시아입니다.',
  '코엑스 오디토리움 3층 행사는 DDP 아트홀에서 진행합니다.',
  'COEX auditorium 3F event cancelled',
  '코엑스 오디토리움 3층. 실제 행사장은 한빛센터입니다.',
  '코엑스 오디토리움 3층 행사는 한빛센터에서 진행합니다.',
  '장소: 코엑스 오디토리움. 실제 장소: 코엑스 아셈볼룸.',
 ]){
  for(const destinationId of ['', 'auditorium']){
   const b=buildBrief({text,height:1.65,destinationId});
   assert.equal(b.status,'needs_review',text);assert.equal(b.canConfirm,false,text);
   assert.throws(()=>formatBrief(b),/확정 가능한/);
  }
 }
});
test('multiple event floors and registration floors explain a recoverable edit',()=>{
 const text='코엑스 오전 그랜드볼룸 1층, 오후 오디토리움 3층';
 const initial=buildBrief({text,height:1.65});
 assert.equal(initial.canConfirm,false);assert.match(initial.reason,/이번에 방문할 행사장과 층이 있는 문장만/);
 for(const b of [buildBrief({text,height:1.65,destinationId:'auditorium'}),buildBrief({text:'코엑스 오디토리움 3층. 1층 로비에서 등록해주세요.',height:1.65})]){
  assert.equal(b.status,'needs_review');assert.match(b.reason,/등록 장소와 다른 일정/);
 }
 assert.equal(buildBrief({text:'코엑스 오디토리움 3층',height:1.65}).status,'ready');
});
test('stopped, deferred, replaced and online events fail closed',()=>{
 for(const text of [
  '코엑스 오디토리움 3층. 행사는 더 이상 열리지 않습니다.',
  '코엑스 오디토리움 3층 행사. 본 행사는 다음 달로 미뤄졌습니다.',
  '코엑스 오디토리움 3층. 최종 개최지: 한빛센터.',
  '코엑스 오디토리움 3층. 이번 행사는 한빛센터에서 합니다.',
  '코엑스 오디토리움 3층 행사를 줌 온라인으로 전환했습니다.',
 ])for(const destinationId of ['', 'auditorium']){
  const b=buildBrief({text,height:1.65,destinationId});
  assert.equal(b.status,'needs_review',text);assert.equal(b.canConfirm,false,text);
 }
});
test('a recognised name is described as a candidate requiring original-notice review',()=>{
 const extraction=extractDestinations('코엑스 오디토리움 3층');
 assert.equal(extraction.status,'matched');
 assert.match(extraction.message,/장소명 후보/);
 assert.match(extraction.message,/안내문 원본을 직접 확인/);
 assert.doesNotMatch(extraction.message,/공식 시설 안내와 일치/);
});
test('explicit parking clauses support Korean particles and polite usage phrases',()=>{
 for(const [text,note] of [
  ['코엑스 오디토리움 3층에서 열리는 정기총회입니다. 주차는 B3 블루존을 이용해 주세요.','주차는 B3 블루존을 이용해 주세요.'],
  ['장소: 코엑스 오디토리움 3층\n주차: B3 블루존 이용 바랍니다.','주차: B3 블루존 이용 바랍니다.'],
  ['코엑스 오디토리움 3층\n주차: B3 블루존은 이용해 주십시오.','주차: B3 블루존은 이용해 주십시오.'],
  ['코엑스 스튜디오159 2층\n주차: B2 퍼플존을 이용하여 주십시오.','주차: B2 퍼플존을 이용하여 주십시오.'],
 ]){
  const b=buildBrief({text,height:1.65});assert.equal(b.status,'ready',text);
  assert.deepEqual(b.extraction.parkingNotes,[note]);
 }
});
test('polite parking instructions cannot hide unknown suffixes or different parking',()=>{
 for(const text of [
  '코엑스 오디토리움 3층\n주차: B3 블루존을 이용해 주세요. 진입은 지하 2층입니다.',
  '코엑스 오디토리움 3층\n주차: B3 블루존을 이용 바랍니다 뒷문으로',
  '코엑스 오디토리움 3층\n주차: B2 블루존을 이용해 주세요.',
  '코엑스 오디토리움 3층\n주차: B3 퍼플존을 이용해 주십시오.',
 ])assert.equal(buildBrief({text,height:1.65}).canConfirm,false,text);
 const b=buildBrief({text:'코엑스 오디토리움 3층\n주차: B3 블루존을 이용 바랍니다 뒷문으로',height:1.65});
 assert.match(b.reason,/바꾸거나 생략하지/);
});
test('unparsed explicit parking and entry clauses are preserved and always block confirmation',()=>{
 for(const note of ['주차: 퍼플존 / 동문','주차는 퍼플존만 이용하세요.','주차: 퍼플존에 주차하세요.','주차: 별도 승인 구역','진입문: 남문','입구: 동문','gate:south','진입문: 북문']){
  for(const destinationId of ['', 'auditorium']){
   const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
   assert.equal(b.status,'needs_review',note);assert.equal(b.canConfirm,false,note);
   assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,note);
   const kept=[...b.extraction.unparsedParkingNotes,...b.extraction.entryNotes];
   assert.ok(kept.includes(note),note);assert.ok(b.extraction.venueText.includes(note),note);
   assert.throws(()=>formatBrief(b),/확정 가능한/);
  }
 }
});
test('known parking zones and gates anywhere in the original must agree with the selected source',()=>{
 for(const [text,destinationId] of [
  ['코엑스 오디토리움 3층. 현장 운영에 따라 퍼플존만 개방합니다.',''],
  ['코엑스 오디토리움 3층. 남문으로 오세요.',''],
  ['COEX auditorium 3F. Use the east gate.',''],
  ['코엑스 오디토리움 3층. purple zone만 개방합니다.',''],
  ['코엑스 행사 안내. 블루존에서 뵙겠습니다.','studio159'],
  ['코엑스 행사 안내. 북문에서 뵙겠습니다.','studio159'],
 ])assert.equal(buildBrief({text,height:1.65,destinationId}).canConfirm,false,text);
 assert.equal(buildBrief({text:'코엑스 오디토리움 3층. 북문과 블루존에서 안내합니다.',height:1.65}).status,'needs_review');
});
test('same-name closed or uninterpreted parking and entry instructions always require review',()=>{
 for(const note of ['북문은 폐쇄되었습니다.','북문 이용 금지','블루존은 폐쇄되었습니다.','블루존은 통제 중입니다.','진입 금지','The north gate is closed.','Entry restricted.','블루존에서 뵙겠습니다.']){
  for(const destinationId of ['', 'auditorium']){
   const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
   assert.equal(b.status,'needs_review',note);
   assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,note);
   assert.throws(()=>formatBrief(b),/확정 가능한/);
  }
 }
 assert.equal(buildBrief({text:'코엑스 오디토리움 3층\n주차는 B3 블루존을 이용해 주세요.',height:1.65}).status,'ready');
});
test('unparsed access operations block regardless of permission grammar in Korean or English',()=>{
 for(const note of ['금일은 차량 진입이 불가합니다.','입차 불가','오늘은 차량 출입을 금합니다.','차량 출입을 삼가주세요.','출차가 허용되지 않습니다.','통행을 제한합니다.','출입문 운영이 중단되었습니다.','Vehicle access unavailable.','Entrance is not permitted.','Entry prohibited today.','Drive-in is not allowed.','The gate is unavailable.']){
  for(const destinationId of ['', 'auditorium']){
   const b=buildBrief({text:`코엑스 오디토리움 3층 행사\n${note}`,height:1.65,destinationId});
   assert.equal(b.status,'needs_review',note);assert.ok(b.extraction.entryNotes.includes(note),note);
   assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,note);
  }
 }
});
test('exact official height facts and ordinary event admission remain usable',()=>{
 for(const note of ['시설 진입 제한 높이: 2.2m.','주차장 진입 제한 높이 2.20미터입니다.','Parking entrance height limit: 2.2m.','Admission opens at 09:00. 행사 주제는 미래 모빌리티입니다.']){
  const b=buildBrief({text:`코엑스 오디토리움 3층 행사\n${note}`,height:1.65});
  assert.equal(b.status,'ready',note);for(const clause of note.split(/(?<=[.!?])\s+/))assert.ok(b.extraction.venueText.includes(clause),clause);
 }
 for(const note of ['시설 진입 제한 높이: 2.3m.','시설 진입 제한 높이: 2.2m 입차 불가'])assert.equal(buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65}).canConfirm,false,note);
});
test('unparsed parking and unnamed doors are held wherever their words occur in a clause',()=>{
 for(const note of ['오늘은 주차장을 이용할 수 없습니다.','오늘은 주차할 수 없습니다.','금일 입구를 이용할 수 없습니다.','오늘 입구를 막습니다.','정문 이용 불가','후문은 사용하지 마세요.','오늘 출구를 사용할 수 없습니다.','옆문은 안내에 따라 이용하세요.','쪽문을 사용해주세요.','대문으로 오세요.','Today parking is unavailable.','The exit cannot be used today.','The car park is unavailable.']){
  for(const destinationId of ['', 'auditorium']){
   const b=buildBrief({text:`코엑스 오디토리움\n${note}`,height:1.65,destinationId});
   assert.equal(b.status,'needs_review',note);
   assert.ok([...b.extraction.unparsedParkingNotes,...b.extraction.entryNotes].includes(note),note);
   assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,note);
  }
 }
});
test('venue declarations must establish COEX ownership and contain no unknown facility words',()=>{
 for(const text of [
  '장소: 연세대학교 백양누리 그랜드볼룸\n후원: 코엑스',
  '장소: 그랜드볼룸\n후원: 코엑스',
  '장소: 코엑스 연세대학교 백양누리 그랜드볼룸 1층',
  'Venue: Yonsei University Grand Ballroom 1F. Sponsor: COEX.',
  '장소: 코엑스 그랜드볼룸 1층 별관',
 ])for(const destinationId of ['', 'grand']){
  const b=buildBrief({text,height:1.65,destinationId});assert.equal(b.status,'needs_review',text);assert.equal(b.canConfirm,false,text);
 }
 for(const text of ['장소: 코엑스 그랜드볼룸 1층입니다.','장소: 코엑스 삼성동 오디토리움 (3층)','장소: 코엑스 오디토리움 3층\n후원: 협력사'])assert.equal(buildBrief({text,height:1.65}).status,'ready',text);
});
test('venue declarations are checked through their full sentence instead of a fixed prefix',()=>{
 const text=`장소: 코엑스 그랜드볼룸 1층${' '.repeat(180)}연세대학교 별관`;
 assert.equal(buildBrief({text,height:1.65,destinationId:'grand'}).status,'needs_review');
});
test('unlabelled shared room names cannot borrow facility ownership from sponsorship',()=>{
 for(const text of ['연세대학교 백양누리 그랜드볼룸\n후원: 코엑스','행사 안내\n연세대학교 백양누리 그랜드볼룸에서 만나요.\n후원: 코엑스','코엑스 후원 연세대학교 백양누리 그랜드볼룸','후원: 코엑스 그랜드볼룸'])for(const destinationId of ['', 'grand']){
  const b=buildBrief({text,height:1.65,destinationId});assert.equal(b.status,'needs_review',text);
  assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,text);
 }
});
test('explicit non-occurrence or inability to visit blocks even a matching venue',()=>{
 for(const text of ['코엑스 오디토리움에서는 행사를 진행하지 않습니다.','코엑스 오디토리움에서는 행사를 개최하지 않습니다.','코엑스 그랜드볼룸 1층\n엘리베이터 공사로 행사장 방문이 불가능합니다.','코엑스 그랜드볼룸 1층\n행사장을 방문할 수 없습니다.','COEX grand ballroom 1F. Visitors cannot visit the venue.']){
  const b=buildBrief({text,height:1.65});assert.equal(b.status,'needs_review',text);assert.equal(b.canConfirm,false,text);
 }
 assert.equal(buildBrief({text:'코엑스 오디토리움 3층에서 열리는 디자인 컨퍼런스입니다.',height:1.65}).status,'ready');
});
test('unparsed facility operating status is preserved and held regardless of its apparent polarity',()=>{
 for(const note of ['금일 휴관합니다.','행사장 운영을 중단합니다.','행사는 중단되었습니다.','시설은 휴업 중입니다.','오늘 행사장 문을 닫습니다.','금일은 휴장입니다.','시설은 휴무입니다.','행사장이 폐관했습니다.','오늘은 쉬는 날입니다.','이번 행사는 비개최입니다.','시설은 정상 운영합니다.','내일 영업을 재개합니다.','오늘 행사장 문을 엽니다.'])for(const destinationId of ['', 'auditorium']){
  const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
  assert.equal(b.status,'needs_review',note);assert.ok(b.extraction.facilityNotes.includes(note),note);
  assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,note);
  assert.throws(()=>formatBrief(b),/확정 가능한/);
 }
});
test('ordinary event introductions remain distinct from unverified facility status',()=>{
 for(const text of ['코엑스 오디토리움 3층에서 열리는 디자인 컨퍼런스입니다.','코엑스 오디토리움 3층\n행사 소개: 미래 모빌리티 기술 강연입니다.','코엑스 오디토리움 3층\n주차는 B3 블루존을 이용해 주세요.'])assert.equal(buildBrief({text,height:1.65}).status,'ready',text);
 for(const text of ['코엑스 오디토리움에서 행사를 열지 않습니다.','코엑스 오디토리움 행사 취소'])assert.equal(buildBrief({text,height:1.65}).canConfirm,false,text);
});
test('event non-occurrence and remote or hybrid format mentions cannot authorize a physical visit',()=>{
 for(const note of ['행사가 무산되었습니다.','행사는 비대면으로 개최됩니다.','온라인으로만 진행합니다.','Zoom으로만 진행합니다.','금일은 행사가 없습니다.','행사가 불성립되었습니다.','원격 행사로 진행합니다.','하이브리드 행사로 진행합니다.'])for(const destinationId of ['', 'auditorium']){
  const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
  assert.equal(b.status,'needs_review',note);assert.ok(b.extraction.eventNotes.includes(note),note);
  assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true,venueReviewed:true}),false,note);
  assert.throws(()=>formatBrief(b),/확정 가능한/);
 }
});
test('ordinary dates tickets and event introductions remain usable with a known physical venue',()=>{
 for(const note of ['행사 일시: 2026년 11월 7일 14:00','입장권은 현장에서 확인합니다. Admission is free.','행사 소개: 디자인과 미래 모빌리티를 다루는 강연입니다.'])assert.equal(buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65}).status,'ready',note);
});


test('event negation is preserved regardless of unfamiliar occurrence wording',()=>{
 for(const note of ["오늘 행사는 안 합니다.", "이번 행사는 하지 않습니다.", "오늘 공연은 안 해요.", "이번 회의는 못 합니다.", "강연은 하지 않아요.", "This event is not happening.", "There is no event today.", "The conference won't take place.", "Today's meeting isn't happening.", "No concert today.", "This seminar does not happen today."])for(const destinationId of ['', 'auditorium']){
  const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
  assert.equal(b.status,'needs_review',note); assert.equal(b.canConfirm,false,note); assert.ok(b.extraction.eventNotes.includes(note),note);
 }
});

test('event lifecycle statements are held without inferring occurrence from a room',()=>{
 for(const note of ["행사가 종료되었습니다.", "이번 행사는 이미 끝났습니다.", "오늘 공연은 모두 마쳤습니다.", "The event has ended.", "The conference is over.", "The meeting has concluded.", "행사는 18시에 종료 예정입니다.", "The event ends at 18:00."])for(const destinationId of ['', 'auditorium']){
  const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
  assert.equal(b.status,'needs_review',note); assert.equal(b.canConfirm,false,note); assert.ok(b.extraction.eventNotes.includes(note),note);
 }
});


test('completion clauses keep their original meaning when the event subject is omitted',()=>{
 for(const note of ["종료되었습니다.", "일정이 끝났습니다.", "모두 마쳤습니다.", "It has ended.", "It is over.", "It’s over.", "This is over.", "Already completed.", "Now finished.", "Concluded yesterday."])for(const destinationId of ['', 'auditorium']){
  const b=buildBrief({text:`코엑스 오디토리움 3층\n${note}`,height:1.65,destinationId});
  assert.equal(b.status,'needs_review',note); assert.equal(b.canConfirm,false,note); assert.ok(b.extraction.eventNotes.includes(note),note);
 }
});
