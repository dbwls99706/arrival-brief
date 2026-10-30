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

test('confirmation requires both actual height review and safe parking review',()=>{
 const b=buildBrief({text:'코엑스 오디토리움 3층 행사',height:1.65});
 for(const review of [{},{mode:'parked'},{mode:'parked',heightReviewed:true},{mode:'parked',parkedConfirmed:true},{mode:'driving',heightReviewed:true,parkedConfirmed:true},{mode:'unknown',heightReviewed:true,parkedConfirmed:true}])assert.equal(reviewAllowsConfirmation(b,review),false);
 assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true}),true);
 assert.equal(reviewAllowsConfirmation(buildBrief({text:'코엑스 컨퍼런스룸 E',height:1.65}),{mode:'parked',heightReviewed:true,parkedConfirmed:true}),false);
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
 assert.equal(draft.canConfirm,false);assert.equal(reviewAllowsConfirmation(draft,{mode:'parked',heightReviewed:true,parkedConfirmed:true}),false);
 assert.equal(record.datasetVersion,DATASET_VERSION);
});


test('shared Android and web decision fixtures reject the same unsafe drafts',()=>{
 const lines=readFileSync(new URL('./decision-cases.tsv',import.meta.url),'utf8').trimEnd().split('\n').slice(1);
 for(const line of lines){const [id,text,height,destinationId,expected]=line.split('\t');const b=buildBrief({text,height,destinationId});assert.equal(b.status==='ready',expected==='true',id);}
 assert.equal(lines.length,50);
});


test('multiline invitations cannot bypass nearby venue or changed location review',()=>{
 for(const text of ['코엑스\n맞은편 오디토리움','코엑스\n근처 그랜드볼룸','코엑스 오디토리움 장소\n변경']){
  const brief=buildBrief({text,height:1.65});assert.equal(brief.status,'needs_review',text);assert.equal(brief.canConfirm,false,text);
 }
});
