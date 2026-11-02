import test from 'node:test';import assert from 'node:assert/strict';
import {createParkingMemo,inspectParkingMemo,PARKING_TTL_MS,canUseParkingMemo,validateNoticeFile} from '../dist/arrival/visit.mjs';
test('parking memo is a bounded explicit local record',()=>{
 const r=createParkingMemo(' B3 블루존 C17 기둥 ',1000);assert.deepEqual(r,{schema:1,memo:'B3 블루존 C17 기둥',savedAt:1000});
 for(const text of ['', '  ', 'a'.repeat(65),'B3\nC17','B3\u0000C17'])assert.throws(()=>createParkingMemo(text,1000));
 assert.doesNotThrow(()=>createParkingMemo('가'.repeat(64),1000));
});
test('parking reminder rejects stale, future, malformed or extended records',()=>{
 const r=createParkingMemo('B3 C17',1000);assert.equal(inspectParkingMemo(r,1001).status,'available');
 assert.equal(inspectParkingMemo(r,1000+PARKING_TTL_MS-1).status,'available');assert.equal(inspectParkingMemo(r,1000+PARKING_TTL_MS).status,'expired');
 for(const x of [{...r,savedAt:2000},{...r,schema:2},{...r,location:'extra'},{...r,memo:4},[], '{bad'])assert.equal(inspectParkingMemo(x,1001).status,'invalid');
});
test('parking memo access requires an affirmative parked review',()=>{
 for(const mode of ['driving','unknown',null,'parked'])for(const checked of [false,null,'true',1])assert.equal(canUseParkingMemo(mode,checked),false);
 assert.equal(canUseParkingMemo('driving',true),false);assert.equal(canUseParkingMemo('parked',true),true);
});
test('notice import accepts bounded plain text files only',()=>{
 assert.equal(validateNoticeFile({name:'행사.txt',size:300,type:'text/plain'}),true);
 for(const f of [{name:'event.html',size:300,type:'text/plain'},{name:'event.txt',size:40001,type:''},{name:'event.txt',size:0,type:''},{name:'event.txt',size:30,type:'text/html'}])assert.equal(validateNoticeFile(f),false);
});
