const videos = [...document.querySelectorAll('video')];
const pair = document.querySelector('.reel-pair');
const selectors = [...document.querySelectorAll('[data-reel-target]')];
const reels = selectors.map(button => document.getElementById(button.dataset.reelTarget));
const reducedMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
const formatDuration = seconds => {
  if (!Number.isFinite(seconds) || seconds <= 0) return '시연 영상';
  const rounded = Math.round(seconds);
  return `${Math.floor(rounded / 60)}:${String(rounded % 60).padStart(2, '0')}`;
};
function selectReel(index) {
  const previous = selectors.findIndex(button => button.getAttribute('aria-pressed') === 'true');
  if (previous !== index) reels[previous]?.querySelector('video')?.pause();
  selectors.forEach((button, i) => button.setAttribute('aria-pressed', String(i === index)));
}
function revealReel(index) {
  const reel = reels[index];
  if (!reel || !pair) return;
  selectReel(index);
  const left = pair.scrollLeft + reel.getBoundingClientRect().left - pair.getBoundingClientRect().left - 3;
  pair.scrollTo({ left, behavior: reducedMotion() ? 'auto' : 'smooth' });
  selectors[index].focus({ preventScroll: true });
}
selectors.forEach((button, index) => {
  button.addEventListener('click', () => revealReel(index));
  button.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowRight') next = (index + 1) % selectors.length;
    if (event.key === 'ArrowLeft') next = (index + selectors.length - 1) % selectors.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = selectors.length - 1;
    if (next === undefined) return;
    event.preventDefault();
    revealReel(next);
  });
});
let scrollTimer;
pair?.addEventListener('scroll', () => {
  clearTimeout(scrollTimer);
  scrollTimer = setTimeout(() => {
    if (pair.scrollWidth <= pair.clientWidth + 2) return;
    const center = pair.getBoundingClientRect().left + pair.clientWidth / 2;
    const distances = reels.map(reel => {
      const rect = reel.getBoundingClientRect();
      return Math.abs(rect.left + rect.width / 2 - center);
    });
    selectReel(distances.indexOf(Math.min(...distances)));
  }, 100);
}, { passive: true });

for (const button of document.querySelectorAll('[data-browse-demos]')) {
  button.addEventListener('click', () => {
    document.getElementById('demos')?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' });
    const selected = selectors.find(item => item.getAttribute('aria-pressed') === 'true');
    const focusTarget = selected?.offsetParent ? selected : pair;
    focusTarget?.focus({ preventScroll: true });
  });
}

function showVideoMessage(video, message) {
  const error = document.querySelector(`[data-error-for="${video.id}"]`);
  if (!error) return;
  const text = error.querySelector('[data-error-message]');
  if (text) text.textContent = message;
  error.hidden = false;
}
function clearVideoMessage(video) {
  const error = document.querySelector(`[data-error-for="${video.id}"]`);
  if (error) error.hidden = true;
}
for (const video of videos) {
  const duration = document.querySelector(`[data-duration-for="${video.id}"]`);
  const updateDuration = () => { if (duration) duration.textContent = formatDuration(video.duration); };
  video.addEventListener('loadedmetadata', updateDuration);
  video.addEventListener('durationchange', updateDuration);
  video.addEventListener('play', () => {
    for (const other of videos) if (other !== video) other.pause();
    clearVideoMessage(video);
  });
  video.addEventListener('error', () => showVideoMessage(video, '영상을 불러오지 못했습니다.'));
  video.addEventListener('loadeddata', () => clearVideoMessage(video));
  if (video.readyState >= 1) updateDuration();

}
document.addEventListener('visibilitychange', () => {
  if (document.hidden) for (const video of videos) video.pause();
});
