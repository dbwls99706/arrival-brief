import {DESTINATIONS} from '../arrival/engine.mjs';
const scene=document.querySelector('.arrival-scene');
for(const button of document.querySelectorAll('[data-scene]')) {
 button.addEventListener('click',()=>{
  const d=DESTINATIONS.find(item=>item.id===button.dataset.scene);
  if(!d)return;
  for(const choice of document.querySelectorAll('[data-scene]'))choice.setAttribute('aria-pressed',String(choice===button));
  document.getElementById('scene-gate').textContent=d.gate;
  document.getElementById('scene-context').textContent=d.gateContext;
  const parking=document.getElementById('scene-parking');
  parking.replaceChildren(document.createTextNode(`${d.level} `));
  const zone=document.createElement('span');zone.className=`zone-${d.color}`;zone.textContent=d.zone;parking.append(zone);
  const link=document.getElementById('scene-try');
  link.href=`../arrival/?example=${d.id}`;
  link.setAttribute('aria-label',`${d.name} 도착 준비 체험`);
  scene.classList.remove('scene-changed');
  requestAnimationFrame(()=>requestAnimationFrame(()=>scene.classList.add('scene-changed')));
 });
}
const videos=[...document.querySelectorAll('video')];
for(const video of videos) video.addEventListener('play',()=>{for(const other of videos)if(other!==video)other.pause();});
document.addEventListener('visibilitychange',()=>{if(document.hidden)for(const video of videos)video.pause();});
