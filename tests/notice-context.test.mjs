import test from 'node:test';
import assert from 'node:assert/strict';
import {buildBrief, formatBrief, reviewAllowsConfirmation} from '../dist/arrival/engine.mjs';

const venue='장소: 코엑스 아셈볼룸 2층';
const schedule='행사는 10:00에 시작하여 18:00에 종료됩니다.';
const registration='참가 등록은 1층 로비에서 진행합니다.';

test('complete same-day timetables remain visible without claiming current occurrence',()=>{
 for(const note of [schedule,'행사는 0:00에 시작하여 0:01에 종료됩니다.','행사는 23:58에 시작하여 23:59에 종료됩니다.']){
  const b=buildBrief({text:`${venue}\n${note}`,height:1.65});
  assert.equal(b.status,'ready');
  assert.deepEqual(b.extraction.scheduleNotes,[note]);
  assert.equal(b.extraction.venueText,venue);
  assert.ok(formatBrief(b).includes(note));
  assert.match(formatBrief(b),/현재 개최 여부를 확인한 결과가 아닙니다/);
  assert.equal(reviewAllowsConfirmation(b,{mode:'parked',heightReviewed:true,parkedConfirmed:true}),false);
 }
});

test('invalid or incomplete schedule grammar never becomes a permissive exception',()=>{
 for(const note of [
  '행사는 24:00에 시작하여 25:00에 종료됩니다.',
  '행사는 10:60에 시작하여 18:00에 종료됩니다.',
  '행사는 10:00에 시작하여 18:60에 종료됩니다.',
  '행사는 18:00에 시작하여 10:00에 종료됩니다.',
  '행사는 10:00에 시작하여 10:00에 종료됩니다.',
  '행사는 10:0에 시작하여 18:00에 종료됩니다.',
  '행사는 10:00에 시작하여 18:00에 종료되었습니다.',
  '행사는 10:00에 시작하여 18:00에 종료됩니다?',
  '이미 행사는 10:00에 시작하여 18:00에 종료됩니다.',
  `${schedule} 하지만 행사가 취소되었습니다.`,
  `${schedule}\n종료되었습니다.`,
  `${schedule}\n행사는 09:00에 시작하여 17:00에 종료됩니다.`
 ])for(const destinationId of ['', 'asem']){
  const b=buildBrief({text:`${venue}\n${note}`,height:1.65,destinationId});
  assert.equal(b.canConfirm,false,note);assert.throws(()=>formatBrief(b),/확정 가능한/);
 }
});

test('a registration-only lobby clause keeps its original words and its separate floor',()=>{
 for(const note of [registration,'등록은 4층 로비에서 진행합니다.']){
  const b=buildBrief({text:`${venue}\n${note}`,height:1.65});
  assert.equal(b.status,'ready');assert.equal(b.destination.id,'asem');
  assert.equal(b.destination.floor,'2F');assert.deepEqual(b.extraction.registrationNotes,[note]);
  assert.equal(b.extraction.venueText,venue);
  assert.ok(formatBrief(b).includes(note));assert.match(formatBrief(b),/실내 이동 경로가 아닙니다/);
 }
});

test('registration clauses cannot conceal another facility, an invalid floor or conflicting notes',()=>{
 for(const note of [
  '참가 등록은 한빛센터 2층 로비에서 진행합니다.',
  '참가 등록은 코엑스 마곡 2층 로비에서 진행합니다.',
  '참가 등록은 코엑스 스튜디오159 2층에서 진행합니다.',
  '참가 등록은 0층 로비에서 진행합니다.',
  '참가 등록은 02층 로비에서 진행합니다.',
  '참가 등록은 -2층 로비에서 진행합니다.',
  '참가 등록은 2.0층 로비에서 진행합니다.',
  '참가 등록은 5층 로비에서 진행합니다.',
  '참가 등록은 B2 로비에서 진행합니다.',
  '참가 등록은 2층 로비에서 진행하지 않습니다.',
  '참가 등록은 2층 로비에서 진행합니다만 장소 변경입니다.',
  `${registration}\n등록은 3층 로비에서 진행합니다.`,
  `${registration}\n행사장 3층`
 ])for(const destinationId of ['', 'asem']){
  const b=buildBrief({text:`${venue}\n${note}`,height:1.65,destinationId});
  assert.equal(b.canConfirm,false,note);assert.throws(()=>formatBrief(b),/확정 가능한/);
 }
});

test('separated notes preserve whole-notice checks and all confirmation gates',()=>{
 const text=`${venue}\n${schedule}\n${registration}`;
 const b=buildBrief({text,height:1.65});
 assert.equal(b.status,'ready');
 const full={mode:'parked',venueReviewed:true,heightReviewed:true,parkedConfirmed:true};
 assert.equal(reviewAllowsConfirmation(b,full),true);
 for(const field of ['venueReviewed','heightReviewed','parkedConfirmed'])assert.equal(reviewAllowsConfirmation(b,{...full,[field]:false}),false);
 for(const mode of ['driving','unknown'])assert.equal(reviewAllowsConfirmation(buildBrief({text,height:1.65,mode}),{...full,mode}),false);
 assert.equal(buildBrief({text,height:2.35}).status,'blocked');
 for(const note of ['행사가 취소되었습니다.','행사장은 송도컨벤시아로 변경되었습니다.','북문을 이용할 수 없습니다.','주차: B2 퍼플존','금일 휴관합니다.']){
  assert.equal(buildBrief({text:`${text}\n${note}`,height:1.65}).canConfirm,false,note);
 }
 assert.equal(buildBrief({text:`장소: 코엑스 회의실 301호 3층\n${schedule}\n${registration}`,height:1.65}).status,'source_conflict');
});
