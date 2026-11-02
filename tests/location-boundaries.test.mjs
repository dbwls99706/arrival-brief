import test from 'node:test';
import assert from 'node:assert/strict';
import {buildBrief} from '../dist/arrival/engine.mjs';

test('English numeric rooms retain their identity through manual selection',()=>{
 for(const prefix of ['Room','Rooms','Room No.','room number','Room #','Room:','Room No:','Room №']){
  for(const [number,id,floor] of [[300,'rooms300',3],[328,'rooms300',3],[400,'rooms400',4],[403,'rooms400',4]]){
   const text='COEX '+prefix+' '+number+' '+floor+'F';
   for(const destinationId of ['',id]){
    const b=buildBrief({text,destinationId,height:'1.65'});
    assert.equal(b.status,'source_conflict',text);
    assert.equal(b.destination.id,id,text);
    assert.equal(b.canConfirm,false,text);
   }
   assert.equal(buildBrief({text,destinationId:'auditorium',height:'1.65'}).canConfirm,false,text);
  }
 }
});

test('unknown room suffixes and unsupported numbers cannot become another room',()=>{
 for(const text of ['COEX room 300A 3F','코엑스 회의실 300A 3층','코엑스 300A호 3층',
  'COEX Room No. 329 3F','COEX Rooms 404 4F','COEX room number 99999 3F',
  'COEX room 0300 3F','COEX Room 300_A 3F','COEX Rooms 301-302 3F']){
  for(const destinationId of ['','auditorium','studio159']){
   assert.equal(buildBrief({text,destinationId,height:'1.65'}).canConfirm,false,text+' -> '+destinationId);
  }
 }
});

test('noncanonical floor tokens are not read as a positive trailing floor',()=>{
 for(const floor of ['-3층','−3층','－3층','- 3층','0층','0F','01층','03F','0.3층','1.3F','3.0층','+3F']){
  for(const destinationId of ['','auditorium']){
   const text='코엑스 오디토리움 '+floor;
   const b=buildBrief({text,destinationId,height:'1.65'});
   assert.equal(b.status,'needs_review',text);
   assert.equal(b.canConfirm,false,text);
  }
 }
 for(const text of ['코엑스 그랜드볼룸 1층','코엑스 아셈볼룸 2층','COEX auditorium 3F','코엑스 회의실 3층 오디토리움']){
  assert.equal(buildBrief({text,height:'1.65'}).status,'ready',text);
 }
});


test('basement prefixes and malformed numeric floor expressions cannot confirm',()=>{
 const tokens=['B03','B0','B-3','B−3','B－3','B+3','B＋3','B 3','B.3','B .3','B-.3','B0.3',
  '.3','-.3','+.3','−.3','－.3','＋.3','3.','＋3','＋ 3','3,3','0,3','3/3','3e0','3E0','3 / 3','3, 3'];
 for(const token of tokens){
  for(const suffix of ['층','F']){
   const text='코엑스 오디토리움 '+token+suffix;
   for(const destinationId of ['','auditorium']){
    const b=buildBrief({text,destinationId,height:'1.65'});
    assert.equal(b.status,'needs_review',text);
    assert.equal(b.canConfirm,false,text);
   }
  }
 }
 for(const floor of ['3층','3F','3 F','3 층','3층 3F']){
  assert.equal(buildBrief({text:'코엑스 오디토리움 '+floor,height:'1.65'}).status,'ready',floor);
 }
});
