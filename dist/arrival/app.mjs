import {DESTINATIONS,SOURCE,SOURCE_CONFLICTS,extractDestinations,buildBrief,formatBrief,reviewAllowsConfirmation,createRecentDestination,inspectRecentDestination} from './engine.mjs';
const $=id=>document.getElementById(id);let mode='parked',confirmedBrief=null,currentBrief=null,recentMessage='';
const RECENT_KEY='arrival-brief.recent.v1';
const PARKING_RECHECK_MESSAGE='안전하게 주차한 상태인지 다시 확인해주세요. 확인 전에는 저장·복사할 수 없습니다.';
const reviewState=()=>({mode,heightReviewed:$('heightReviewed').checked,parkedConfirmed:$('parkedConfirmed').checked});
const canExport=()=>!!confirmedBrief&&mode==='parked'&&$('heightReviewed').checked&&$('parkedConfirmed').checked;

const examples={auditorium:'코엑스 오디토리움 3층에서 열리는 디자인 컨퍼런스에 참석합니다.',conferenceE:'행사장: 코엑스 3층 컨퍼런스룸 E. 등록 데스크에서 입장권을 확인해주세요.',ambiguous:'코엑스 오전 행사는 오디토리움, 오후 워크숍은 컨퍼런스룸 E에서 진행합니다.'};
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=name=>`<svg class="ui-icon" aria-hidden="true"><use href="../assets/icons.svg#${name}"/></svg>`;
function updateOptions(){
 const result=extractDestinations($('ticket').value);
 const choices=result.matches.length?result.matches:DESTINATIONS;
 if(!['matched','empty','unmatched'].includes(result.status))$('noticeEntry').open=true;
 $('destination').innerHTML='<option value="">방문할 장소를 선택해주세요</option>'+choices.map(d=>`<option value="${d.id}">${d.name} · ${d.floor}</option>`).join('');
 $('destination').value=result.matches.length===1?result.matches[0].id:'';
 $('parseStatus').textContent=result.status==='empty'?'시설명과 행사장 이름만으로도 확인할 수 있어요.':result.message;
 $('parseStatus').classList.toggle('error',!['matched','empty'].includes(result.status));
 $('parseStatus').classList.toggle('matched',result.status==='matched');
}
function remainingReview(brief){
 if(mode!=='parked')return '정차 화면에서만 확인할 수 있습니다.';
 if(brief.status==='source_conflict')return '공식 주차 안내가 달라 확정을 보류합니다.';
 if(brief.status==='blocked')return '높이 제한을 초과해 이 주차 동선을 사용할 수 없습니다.';
 if(!['ready','needs_height'].includes(brief.status))return brief.status==='empty'?'방문할 장소를 먼저 확인해주세요.':brief.reason;
 const remaining=[];
 if(brief.height===null)remaining.push('실제 높이 입력');
 if(!$('heightReviewed').checked)remaining.push('높이 확인');
 if(!$('parkedConfirmed').checked)remaining.push('정차 확인');
 return remaining.length?`남은 확인: ${remaining.join(' · ')}`:'준비가 끝났어요. 브리핑을 확정해주세요.';
}
function render(){
 syncScenarioState();
 currentBrief=buildBrief({text:$('ticket').value,destinationId:$('destination').value,height:$('height').value,mode});
 const visible=mode==='parked'?currentBrief:confirmedBrief;
 const canSave=canExport();
 const isConfirmed=!!confirmedBrief;
 if(isConfirmed&&mode==='parked'&&!canSave)$('actionStatus').textContent=PARKING_RECHECK_MESSAGE;
 else if(canSave&&$('actionStatus').textContent===PARKING_RECHECK_MESSAGE)$('actionStatus').textContent='확정한 브리핑을 저장하거나 복사할 수 있습니다.';
 $('confirm').disabled=isConfirmed||!reviewAllowsConfirmation(currentBrief,reviewState());
 $('confirm').innerHTML=isConfirmed?'브리핑 확정됨':`도착 브리핑 확정 ${icon('arrow-right')}`;
 $('confirm').classList.toggle('is-complete',isConfirmed);
 $('reviewHelp').textContent=isConfirmed?'확정했습니다. 내용을 바꾸면 다시 확인해야 합니다.':remainingReview(currentBrief);
 renderRecent();
 $('download').disabled=!canSave;$('copy').disabled=!canSave;
 $('briefBadge').textContent=isConfirmed?'확정됨':visible?.status==='blocked'?'진입 제외':visible?.status==='source_conflict'?'확정 보류':'확인 전';
 $('briefBadge').classList.toggle('confirmed',isConfirmed);
 document.querySelector('.brief-panel').classList.toggle('is-confirmed',isConfirmed);
 $('modeLabel').textContent={parked:'정차 화면 체험 중',driving:'주행 화면 체험 중',unknown:'미확인 화면 체험 중'}[mode];
 if(visible?.status==='source_conflict'){
  $('briefBody').innerHTML=`<div class="source-conflict"><div class="destination-line"><span>COEX</span><strong>${esc(visible.destination.name)}</strong><span>${esc(visible.destination.floor)}</span></div><h3>주차 안내가 서로 달라요.</h3><p>시설에 최신 안내를 확인할 때까지 진입문과 주차 구역을 확정하지 않습니다.</p><ul class="conflict-evidence">${visible.evidence.map(e=>`<li><a href="${esc(e.source.url)}" target="_blank" rel="noopener noreferrer">${esc(e.source.title)} ↗</a><strong>${esc(e.recommendation)}</strong><small>${e.source.publishedAt?`공지 ${e.source.publishedAt} · `:''}확인 ${e.source.checkedAt}</small></li>`).join('')}</ul><p class="helper">서로 다른 원문을 비교한 자료입니다. 현재 사용할 주차 안내가 아닙니다.</p></div>`;
  return;
 }
 if(!visible||!['ready','needs_height'].includes(visible.status)){
  const blocked=visible?.status==='blocked';
  const empty=visible?.status==='empty';
  $('briefBody').innerHTML=`<div class="empty ${blocked?'blocked':''}"><span class="empty-symbol" aria-hidden="true">${icon(blocked?'parking':'arrival')}</span><h3>${mode!=='parked'?'아직 확정한 안내가 없어요.':blocked?'다른 주차 계획이 필요해요.':empty?'도착할 곳부터 차근차근.':'방문 장소를 다시 확인해주세요.'}</h3><p>${esc(mode!=='parked'?'정차 화면에서 방문할 장소와 차량 높이를 확인하고 브리핑을 확정하세요.':empty?'방문 장소와 차량 높이를 확인하면 진입문과 추천 주차 구역을 한 장에 정리해드립니다.':visible?.reason||'행사 안내를 입력해주세요.')}</p><p class="empty-footnote">${mode!=='parked'?'입력·변경은 정차 화면에서만 가능합니다.':'현재 코엑스 삼성동의 회의실을 지원합니다.'}</p></div>`;
  return;
 }
 const d=visible.destination;
 if(!isConfirmed){
  const checks=[{done:true,label:`방문 장소: ${d.name}`},{done:visible.height!==null&&$('heightReviewed').checked,label:visible.height===null?'차량 전체 높이 확인':`차량 전체 높이 ${visible.height.toFixed(2)}m 확인`},{done:$('parkedConfirmed').checked,label:'안전한 주차 상태 확인'}];
  $('briefBody').innerHTML=`<div class="draft-destination"><span class="eyebrow">확인 중인 목적지</span><h3>${esc(d.name)}<span class="floor-tag">${esc(d.floor)}</span></h3><p class="draft-caption">코엑스 삼성동 · 아직 확정하지 않은 안내입니다.</p></div><ol class="draft-checklist">${checks.map((c,i)=>`<li class="${c.done?'complete':''}"><span class="check-dot" aria-hidden="true">${c.done?'✓':i+1}</span><span>${esc(c.label)}</span></li>`).join('')}</ol><p class="draft-note">두 확인 항목을 검토한 뒤 확정하세요.<br>진입문과 추천 주차 구역은 확정 후 표시됩니다.</p>`;
  return;
 }
 $('briefBody').innerHTML=`<div class="destination-line"><span>COEX</span><strong>${esc(d.name)}</strong><span>${esc(d.floor)}</span></div><div class="route-main"><div class="route-cell"><div class="route-label">${icon('door')}진입문</div><div class="gate-title">${esc(d.gate)}</div><div class="gate-sub">${esc(d.gateContext)}</div></div><div class="route-cell"><div class="route-label">${icon('parking')}추천 주차</div><div class="parking-floor">${esc(d.level)}</div><div class="parking-zone"><span class="dot ${d.color}" aria-hidden="true"></span>${esc(d.zone)}</div></div></div><div class="height-confirmed"><span>확인한 차량 높이 <strong>${visible.height.toFixed(2)}m</strong></span><span>시설 제한 <strong>2.2m</strong></span></div><div class="brief-note"><strong>현장 표지와 안내를 우선하세요.</strong><span class="note-context">${mode==='parked'?'출발 전에 확인한 도착 브리핑입니다.':'출발 전에 확정한 안내를 표시하고 있습니다.'}</span></div><ul class="warnings">${visible.warnings.map(w=>`<li>${esc(w)}</li>`).join('')}</ul><a class="source-line" href="${SOURCE.url}" target="_blank" rel="noopener noreferrer">${icon('source')}코엑스 공식 안내 확인 ↗</a>`;
}
function syncScenarioState(){document.querySelectorAll('[data-example]').forEach(b=>{const active=$('ticket').value===examples[b.dataset.example];b.setAttribute('aria-pressed',String(active));b.classList.toggle('active',active);});}
function invalidate(){confirmedBrief=null;$('heightReviewed').checked=false;$('parkedConfirmed').checked=false;$('actionStatus').textContent='';render();}
$('ticket').addEventListener('input',()=>{updateOptions();invalidate();});$('destination').addEventListener('change',()=>{if(!$('ticket').value.trim()&&$('destination').value){$('ticket').value='코엑스 삼성동 행사';$('parseStatus').textContent='지원 목록에서 직접 선택한 장소입니다. 실제 행사장과 일치하는지 확인하세요.';$('parseStatus').classList.remove('error','matched');}invalidate();});$('height').addEventListener('input',invalidate);
$('briefForm').addEventListener('submit',e=>{e.preventDefault();if(mode!=='parked')return;const b=buildBrief({text:$('ticket').value,destinationId:$('destination').value,height:$('height').value,mode});if(!reviewAllowsConfirmation(b,reviewState()))return;confirmedBrief=structuredClone(b);$('actionStatus').textContent='브리핑을 확정했어요. 정차 화면에서 저장하거나 복사할 수 있습니다.';render();$('briefTitle').focus({preventScroll:true});$('briefTitle').scrollIntoView({behavior:'auto',block:'start'});});
document.querySelectorAll('[data-example]').forEach(b=>b.addEventListener('click',()=>{if(mode!=='parked')return;$('ticket').value=examples[b.dataset.example];updateOptions();invalidate();$('briefForm').scrollIntoView({behavior:'auto',block:'start'});$('ticket').focus({preventScroll:true});}));
document.querySelectorAll('[data-mode]').forEach(b=>b.addEventListener('click',()=>{mode=b.dataset.mode;if(mode!=='parked')$('parkedConfirmed').checked=false;document.querySelectorAll('[data-mode]').forEach(x=>{x.classList.toggle('active',x===b);x.setAttribute('aria-pressed',String(x===b));});document.body.classList.toggle('driving',mode!=='parked');$('motionBanner').hidden=mode==='parked';$('motionBanner').textContent=mode==='driving'?'주행 시뮬레이션 · 출발 전에 확정한 안내만 표시합니다.':'상태 미확인 시뮬레이션 · 안전하게 정차했는지 확인할 때까지 입력을 잠급니다.';document.querySelectorAll('#briefForm input,#briefForm select,#briefForm textarea,#briefForm button,[data-example],[data-place]').forEach(x=>x.disabled=mode!=='parked');render();}));
$('download').addEventListener('click',()=>{if(!canExport())return;const u=URL.createObjectURL(new Blob([formatBrief(confirmedBrief)],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=u;a.download='arrival-brief.txt';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);$('actionStatus').textContent='브리핑 파일을 저장했습니다.';});
$('copy').addEventListener('click',async()=>{if(!canExport())return;try{await navigator.clipboard.writeText(formatBrief(confirmedBrief));$('actionStatus').textContent='브리핑을 복사했습니다.';}catch{$('actionStatus').textContent='복사 권한이 없습니다. 브리핑 저장 버튼을 사용해주세요.';}});
$('reset').addEventListener('click',()=>{if(mode!=='parked'){$('actionStatus').textContent='정차 모드에서 초기화할 수 있습니다.';return;}$('ticket').value='';$('height').value='';updateOptions();invalidate();});
$('heightReviewed').addEventListener('change',()=>{if(!$('heightReviewed').checked)confirmedBrief=null;render();});
$('parkedConfirmed').addEventListener('change',()=>{if(!$('parkedConfirmed').checked)confirmedBrief=null;render();});
$('quickSelect').addEventListener('click',()=>{if(mode!=='parked')return;$('ticket').value='코엑스 삼성동 행사';$('noticeEntry').open=false;updateOptions();invalidate();$('destination').focus();});
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
 recentMessage='기억한 장소를 확인 전 초안으로 불러왔습니다. 실제 행사 장소와 차량 전체 높이를 다시 확인하세요.';render();$('briefForm').scrollIntoView({behavior:'auto',block:'start'});
});
$('forgetRecent').addEventListener('click',()=>{if(mode!=='parked')return;try{localStorage.removeItem(RECENT_KEY);recentMessage='기억한 장소를 삭제했습니다.';}catch{recentMessage='장소 기억을 지우지 못했습니다. 브라우저 저장 설정을 확인하세요.';}render();});
window.addEventListener('storage',e=>{if(e.key===RECENT_KEY){recentMessage='';renderRecent();}});
const initialExample=new URLSearchParams(location.search).get('example');
if(Object.hasOwn(examples,initialExample)){$('ticket').value=examples[initialExample];$('noticeEntry').open=true;}
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
  updateOptions();invalidate();$('briefForm').scrollIntoView({behavior:'auto',block:'start'});$('ticket').focus({preventScroll:true});
 }));
}
renderDirectory();
