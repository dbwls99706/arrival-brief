import {DESTINATIONS,SOURCE,SOURCE_CONFLICTS,extractDestinations,buildBrief,formatBrief,reviewAllowsConfirmation,createRecentDestination,inspectRecentDestination} from './engine.mjs';
const $=id=>document.getElementById(id);let mode='parked',confirmedBrief=null,currentBrief=null,recentMessage='';
const RECENT_KEY='arrival-brief.recent.v1';
const reviewState=()=>({mode,heightReviewed:$('heightReviewed').checked,parkedConfirmed:$('parkedConfirmed').checked});
const canExport=()=>!!confirmedBrief&&mode==='parked'&&$('heightReviewed').checked&&$('parkedConfirmed').checked;

const examples={auditorium:'코엑스 오디토리움 3층에서 열리는 디자인 컨퍼런스에 참석합니다.',conferenceE:'행사장: 코엑스 3층 컨퍼런스룸 E. 등록 데스크에서 입장권을 확인해주세요.',ambiguous:'코엑스 오전 행사는 오디토리움, 오후 워크숍은 컨퍼런스룸 E에서 진행합니다.'};
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function updateOptions(){const result=extractDestinations($('ticket').value);const choices=result.matches.length?result.matches:DESTINATIONS;$('destination').innerHTML='<option value="">방문할 장소를 선택해주세요</option>'+choices.map(d=>`<option value="${d.id}">${d.name} · ${d.floor}</option>`).join('');$('destination').value=result.matches.length===1?result.matches[0].id:'';$('parseStatus').textContent=result.message;$('parseStatus').classList.toggle('error',result.status!=='matched');}
function render(){syncScenarioState();currentBrief=buildBrief({text:$('ticket').value,destinationId:$('destination').value,height:$('height').value,mode});const visible=mode==='parked'?currentBrief:confirmedBrief;const canSave=canExport();$('confirm').disabled=!reviewAllowsConfirmation(currentBrief,reviewState());$('reviewHelp').textContent=$('confirm').disabled?'장소·높이와 두 확인 항목을 검토하면 확정할 수 있습니다.':'확인한 내용으로 도착 브리핑을 확정할 수 있습니다.';renderRecent();$('download').disabled=!canSave;$('copy').disabled=!canSave;$('briefBadge').textContent=confirmedBrief?'확정됨':currentBrief.status==='blocked'?'진입 제외':currentBrief.status==='source_conflict'?'자료 상충':'확인 전';$('briefBadge').classList.toggle('confirmed',!!confirmedBrief);
if(visible?.status==='source_conflict'){$('briefBody').innerHTML=`<div class="source-conflict"><div class="destination-line"><span>COEX</span><strong>${esc(visible.destination.name)}</strong><span>${esc(visible.destination.floor)}</span></div><h3>주차 안내를 확정할 수 없어요.</h3><p>${esc(visible.reason)}</p><ul class="conflict-evidence">${visible.evidence.map(e=>`<li><a href="${esc(e.source.url)}" target="_blank" rel="noopener noreferrer">${esc(e.source.title)} ↗</a><strong>${esc(e.recommendation)}</strong><small>${e.source.publishedAt?`공지 ${e.source.publishedAt} · `:''}확인 ${e.source.checkedAt}</small></li>`).join('')}</ul><p class="helper">위 구역은 서로 다른 원문을 비교하기 위한 표시입니다. 현재 사용할 주차 안내가 아닙니다.</p></div>`;return;}
if(!visible||visible.status!=='ready'&&visible.status!=='needs_height'){$('briefBody').innerHTML=`<div class="empty ${visible?.status==='blocked'?'blocked':''}"><span class="empty-icon" aria-hidden="true">${visible?.status==='blocked'?'⊘':'↗'}</span><h3>${mode!=='parked'?'확정된 브리핑이 없습니다':visible?.status==='blocked'?'다른 주차 계획이 필요해요':'목적지를 먼저 확인해주세요'}</h3><p>${esc(mode!=='parked'?'정차 상태에서 목적지와 차량 높이를 확인한 뒤 브리핑을 확정하세요.':visible?.reason||'행사 안내를 입력해주세요.')}</p></div>`;return;}
const d=visible.destination;$('briefBody').innerHTML=`<div class="destination-line"><span>COEX</span><strong>${esc(d.name)}</strong><span>${d.floor}</span></div><div class="gate"><div class="gate-icon" aria-hidden="true">↗</div><div><div class="gate-title">${d.gate} 진입</div><div class="gate-sub">${d.gateContext}</div></div></div><div class="parking"><div class="floor">${d.level}</div><div class="zone"><span class="dot ${d.color}"></span>${d.zone}<small>공식 안내의 추천 주차 구역</small></div></div><div class="brief-note"><span aria-hidden="true">ⓘ</span><div><strong>${mode==='parked'?'출발 전에 장소와 높이를 확인하세요.':'출발 전에 확정한 브리핑입니다.'}</strong><br>${visible.height===null?'차량 높이를 입력해야 확정할 수 있습니다.':`차량 ${visible.height.toFixed(2)}m · 시설 진입 제한 2.2m`}<br>현장 표지와 안내를 우선하세요.</div></div><ul class="warnings">${visible.warnings.map(w=>`<li>${esc(w)}</li>`).join('')}</ul><a class="source-line" href="${SOURCE.url}" target="_blank" rel="noopener noreferrer">코엑스 공식 안내 확인 ↗</a>`;
}
function syncScenarioState(){document.querySelectorAll('[data-example]').forEach(b=>{const active=$('ticket').value===examples[b.dataset.example];b.setAttribute('aria-pressed',String(active));b.classList.toggle('active',active);});}
function invalidate(){confirmedBrief=null;$('heightReviewed').checked=false;$('parkedConfirmed').checked=false;$('actionStatus').textContent='';render();}
$('ticket').addEventListener('input',()=>{updateOptions();invalidate();});$('destination').addEventListener('change',invalidate);$('height').addEventListener('input',invalidate);
$('briefForm').addEventListener('submit',e=>{e.preventDefault();if(mode!=='parked')return;const b=buildBrief({text:$('ticket').value,destinationId:$('destination').value,height:$('height').value,mode});if(!reviewAllowsConfirmation(b,reviewState()))return;confirmedBrief=structuredClone(b);$('actionStatus').textContent='브리핑을 확정했습니다. 주행 시뮬레이션에서 간결한 화면을 확인할 수 있습니다.';render();$('briefTitle').focus({preventScroll:true});$('briefTitle').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});});
document.querySelectorAll('[data-example]').forEach(b=>b.addEventListener('click',()=>{if(mode!=='parked')return;$('ticket').value=examples[b.dataset.example];updateOptions();invalidate();$('briefForm').scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});$('ticket').focus({preventScroll:true});}));
document.querySelectorAll('[data-mode]').forEach(b=>b.addEventListener('click',()=>{mode=b.dataset.mode;if(mode!=='parked')$('parkedConfirmed').checked=false;document.querySelectorAll('[data-mode]').forEach(x=>{x.classList.toggle('active',x===b);x.setAttribute('aria-pressed',String(x===b));});document.body.classList.toggle('driving',mode!=='parked');$('motionBanner').hidden=mode==='parked';$('motionBanner').textContent=mode==='driving'?'주행 시뮬레이션 · 출발 전에 확정한 안내만 표시합니다.':'상태 미확인 시뮬레이션 · 안전하게 정차했는지 확인할 때까지 입력을 잠급니다.';document.querySelectorAll('#briefForm input,#briefForm select,#briefForm textarea,#briefForm button,[data-example],[data-place]').forEach(x=>x.disabled=mode!=='parked');render();}));
$('download').addEventListener('click',()=>{if(!canExport())return;const u=URL.createObjectURL(new Blob([formatBrief(confirmedBrief)],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=u;a.download='arrival-brief.txt';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);$('actionStatus').textContent='브리핑 파일을 저장했습니다.';});
$('copy').addEventListener('click',async()=>{if(!canExport())return;try{await navigator.clipboard.writeText(formatBrief(confirmedBrief));$('actionStatus').textContent='브리핑을 복사했습니다.';}catch{$('actionStatus').textContent='복사 권한이 없습니다. 브리핑 저장 버튼을 사용해주세요.';}});
$('reset').addEventListener('click',()=>{if(mode!=='parked'){$('actionStatus').textContent='정차 모드에서 초기화할 수 있습니다.';return;}$('ticket').value=examples.auditorium;$('height').value='';updateOptions();invalidate();});
$('heightReviewed').addEventListener('change',()=>{if(!$('heightReviewed').checked)confirmedBrief=null;render();});
$('parkedConfirmed').addEventListener('change',()=>{if(!$('parkedConfirmed').checked)confirmedBrief=null;render();});
$('quickSelect').addEventListener('click',()=>{if(mode!=='parked')return;$('ticket').value='코엑스 삼성동 행사';updateOptions();invalidate();$('destination').focus();});
function readRecent(){
 try{
  const stored=inspectRecentDestination(localStorage.getItem(RECENT_KEY));
  if(!['empty','available'].includes(stored.status)){
   localStorage.removeItem(RECENT_KEY);
   recentMessage=stored.status==='expired'?'저장 후 24시간이 지나 장소 기억을 지웠습니다.':stored.status==='version_mismatch'?'안내 자료가 바뀌어 이전 장소 기억을 지웠습니다.':'사용할 수 없는 장소 기억을 지웠습니다.';
  }
  return stored;
 }catch{return {status:'storage_unavailable'};}
}
function renderRecent(){
 const stored=readRecent(),available=stored.status==='available';
 $('saveRecent').disabled=!canExport();$('restoreRecent').disabled=!available||mode!=='parked';$('forgetRecent').disabled=!available||mode!=='parked';
 $('recentStatus').textContent=stored.status==='storage_unavailable'?'이 브라우저에서는 장소 기억을 사용할 수 없습니다.':recentMessage||(available?`${stored.destination.name} · ${stored.destination.floor}를 기억하고 있습니다. 불러온 뒤 높이와 주차 상태를 다시 확인하세요.`:'저장한 장소가 없습니다.');
}
$('saveRecent').addEventListener('click',()=>{
 if(!canExport())return;
 try{const record=createRecentDestination(confirmedBrief.destination.id);localStorage.setItem(RECENT_KEY,JSON.stringify(record));recentMessage=`${confirmedBrief.destination.name} 장소 1건을 24시간 기억합니다. 안내문과 높이는 저장하지 않았습니다.`;}
 catch{recentMessage='장소를 기억하지 못했습니다. 브라우저 저장 설정을 확인하세요.';}
 render();
});
$('restoreRecent').addEventListener('click',()=>{
 if(mode!=='parked')return;
 const stored=readRecent();if(stored.status!=='available'){render();return;}
 $('ticket').value=`코엑스 ${stored.destination.name} ${parseInt(stored.destination.floor)}층 행사`;$('height').value='';updateOptions();invalidate();
 recentMessage='기억한 장소를 확인 전 초안으로 불러왔습니다. 실제 행사 장소와 차량 전체 높이를 다시 확인하세요.';render();$('briefForm').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
});
$('forgetRecent').addEventListener('click',()=>{if(mode!=='parked')return;try{localStorage.removeItem(RECENT_KEY);recentMessage='기억한 장소를 삭제했습니다.';}catch{recentMessage='장소 기억을 지우지 못했습니다. 브라우저 저장 설정을 확인하세요.';}render();});
window.addEventListener('storage',e=>{if(e.key===RECENT_KEY){recentMessage='';renderRecent();}});
const initialExample=new URLSearchParams(location.search).get('example');
if(Object.hasOwn(examples,initialExample))$('ticket').value=examples[initialExample];
updateOptions();render();

function renderDirectory(){
 const groups=[{gate:'북문',subtitle:'봉은사 맞은편'},{gate:'동문',subtitle:'영동대로 방면'},{gate:'보류',subtitle:'시설 확인이 필요한 장소'}];
 $('destinationDirectory').innerHTML=groups.map(g=>`<section class="gate-group"><div class="directory-gate"><h3>${g.gate}</h3><span>${g.subtitle}</span></div>${DESTINATIONS.filter(d=>g.gate==='보류'?SOURCE_CONFLICTS[d.id]:d.gate===g.gate&&!SOURCE_CONFLICTS[d.id]).map(d=>`<div class="place-row"><div><strong>${esc(d.name)} <span>${d.floor}</span></strong><small>${SOURCE_CONFLICTS[d.id]?'공식 자료 상충 · 확정 보류':`<span class="dot ${d.color}" aria-hidden="true"></span>${d.level} · ${esc(d.zone)}`}</small></div><button type="button" data-place="${d.id}" aria-label="${esc(d.name)} 예시 채우기">예시 채우기 <span aria-hidden="true">↗</span></button></div>`).join('')}</section>`).join('');
 $('destinationDirectory').querySelectorAll('[data-place]').forEach(b=>b.addEventListener('click',()=>{
  if(mode!=='parked')return;
  const d=DESTINATIONS.find(x=>x.id===b.dataset.place);
  if(!d)return;
  const roomNames={rooms300:'회의실 300호',rooms400:'회의실 400호'};
  $('ticket').value=`코엑스 ${parseInt(d.floor)}층 ${roomNames[d.id]||d.name}에서 진행되는 행사에 참석합니다.`;
  updateOptions();invalidate();$('briefForm').scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});$('ticket').focus({preventScroll:true});
 }));
}
renderDirectory();
