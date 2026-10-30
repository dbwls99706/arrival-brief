import test from 'node:test';import assert from 'node:assert/strict';import {extractDestinations,buildBrief,formatBrief,DESTINATIONS} from '../dist/arrival/engine.mjs';
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
 assert.equal(buildBrief({text:'코엑스 오디토리움 3F, 주차 B3F',height:1.65}).status,'ready');
});
