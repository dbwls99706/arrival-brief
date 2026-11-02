import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  DATASET_VERSION, SOURCE, NOTICE_SOURCE, SOURCE_CONFLICTS, DESTINATIONS,
  buildBrief, formatBrief, reviewAllowsConfirmation,
} from '../dist/arrival/engine.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const base = path.join(root, 'examples/organizer');
const manifestPath = path.join(base, 'cases.json');
const manifestBytes = await readFile(manifestPath);
const manifest = JSON.parse(manifestBytes);
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const exact = (actual, expected, message) => assert.deepEqual(actual, expected, message);

exact(manifest.kind, 'synthetic-organizer-examples', 'Examples must remain labelled synthetic');
exact(manifest.dataset_version, DATASET_VERSION, 'Dataset version changed; review examples');
exact(manifest.sources.general.url, SOURCE.url, 'General source URL differs');
exact(manifest.sources.general.dataset_checked_at, SOURCE.checkedAt, 'General source date differs');
exact(manifest.sources.notice.url, NOTICE_SOURCE.url, 'Notice source URL differs');
exact(manifest.sources.notice.dataset_checked_at, NOTICE_SOURCE.checkedAt, 'Notice source date differs');
exact(manifest.sources.notice.published_at, NOTICE_SOURCE.publishedAt, 'Notice publication differs');
assert.match(manifest.examples_reviewed_at, /^\d{4}-\d{2}-\d{2}$/);
const supported = ['auditorium', 'grand', 'asem', 'studio159', 'platz'];
const held = ['conferenceE', 'rooms300', 'rooms400'];
exact(Object.keys(SOURCE_CONFLICTS).sort(), [...held].sort(), 'Hold policy changed');
exact(DESTINATIONS.map(d => d.id).sort(), [...supported, ...held].sort(), 'Supported scope changed');
exact(manifest.cases.map(c => c.destination_id).sort(), [...supported, ...held].sort(), 'Expected eight examples');

const results = [];
for (const c of manifest.cases) {
  assert.match(c.file, /^[a-z0-9-]+\.txt$/, 'Example path must stay in organizer folder');
  const bytes = await readFile(path.join(base, c.file));
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  assert.ok(bytes.length <= 40000 && text.length <= 10000, 'Input exceeds import limit');
  assert.ok(text.startsWith('가상 예시 '), 'Synthetic label missing from input');
  const expectedPolicy = supported.includes(c.destination_id) ? 'guide-after-review' : 'hold-source-conflict';
  exact(c.policy, expectedPolicy, `${c.id}: policy mismatch`);
  exact(c.source_ids, expectedPolicy === 'hold-source-conflict' ? ['general', 'notice'] : ['general'], `${c.id}: evidence refs`);
  exact(c.expected.can_confirm_after_review, expectedPolicy === 'guide-after-review', `${c.id}: expected confirmation`);
  const brief = buildBrief({ text, height: '1.65', mode: 'parked' });
  exact(brief.status, c.expected.status, `${c.id}: status`);
  exact(brief.destination?.id, c.destination_id, `${c.id}: destination`);
  exact(brief.destination?.gate, c.expected.reference_gate, `${c.id}: static source gate`);
  exact(brief.destination?.level, c.expected.reference_level, `${c.id}: static source level`);
  exact(brief.destination?.zone, c.expected.reference_zone, `${c.id}: static source zone`);
  exact(brief.canConfirm, c.expected.can_confirm_after_review, `${c.id}: engine eligibility`);
  assert.ok(!brief.confirmed, `${c.id}: import must not auto-confirm`);
  exact(reviewAllowsConfirmation(brief, {}), false, `${c.id}: no user checks`);
  const flags = { mode: 'parked', heightReviewed: true, parkedConfirmed: true, venueReviewed: true };
  exact(reviewAllowsConfirmation(brief, flags), c.expected.can_confirm_after_review, `${c.id}: explicit reviews`);
  for (const flag of ['heightReviewed', 'parkedConfirmed', 'venueReviewed']) {
    exact(reviewAllowsConfirmation(brief, { ...flags, [flag]: false }), false, `${c.id}: missing ${flag}`);
  }
  for (const mode of ['driving', 'unknown']) {
    exact(buildBrief({ text, height: '1.65', mode }).canConfirm, false, `${c.id}: input lock ${mode}`);
  }
  exact(buildBrief({ text, height: '2.30', mode: 'parked' }).canConfirm, false, `${c.id}: over-height`);
  if (expectedPolicy === 'hold-source-conflict') {
    exact(brief.evidence, SOURCE_CONFLICTS[c.destination_id], `${c.id}: both conflicting sources`);
    assert.throws(() => formatBrief(brief), `${c.id}: withheld route must not format as a briefing`);
    exact(buildBrief({ text, destinationId: 'auditorium', height: '1.65', mode: 'parked' }).canConfirm, false, `${c.id}: no manual bypass`);
  } else {
    assert.ok(formatBrief(brief).includes(c.expected.reference_gate), `${c.id}: expected guide gate`);
    assert.ok(formatBrief(brief).includes(c.expected.reference_level), `${c.id}: expected guide level`);
  }
  results.push({
    id: c.id, file: `examples/organizer/${c.file}`, sha256: sha(bytes),
    destination_id: c.destination_id, policy: c.policy, status: brief.status,
    candidate_reference: { gate: brief.destination.gate, level: brief.destination.level, zone: brief.destination.zone },
    candidate_briefing_format_allowed: brief.status === 'ready',
    eligible_after_explicit_test_reviews: reviewAllowsConfirmation(brief, flags),
    auto_confirmed: false, source_ids: c.source_ids, passed: true,
  });
}
const version = (await readFile(path.join(root, 'android/app/build.gradle.kts'), 'utf8')).match(/versionName\s*=\s*"([^"]+)"/)?.[1];
assert.equal(version, '1.0.0-rc3', 'This report is scoped to RC3; update deliberately for a new release');
const engineBytes = await readFile(path.join(root, 'dist/arrival/engine.mjs'));
const scriptBytes = await readFile(fileURLToPath(import.meta.url));
const report = {
  schema_version: 1, version, generated_at: new Date().toISOString(),
  kind: 'synthetic-organizer-examples', status: 'passed',
  scope: 'Web decision-engine checks against authored synthetic notices; not human research, Android runtime, vehicle compatibility, organizer adoption or business outcomes.',
  dataset_version: DATASET_VERSION, source_metadata: manifest.sources,
  examples_reviewed_at: manifest.examples_reviewed_at,
  engine_sha256: sha(engineBytes), manifest_sha256: sha(manifestBytes), script_sha256: sha(scriptBytes),
  cases: results, summary: { total: results.length, guide_after_review: supported.length, held_source_conflict: held.length, passed: results.length },
  checks: ['source URLs and dates', 'dataset and eight destination scope', 'five guide and three hold policies', 'expected gate/level/zone references', 'no automatic confirmation', 'all explicit human-check flags required', 'driving/unknown input lock', 'over-height rejection', 'conflict evidence and manual-bypass rejection'],
};
const output = path.join(root, 'docs/verification/organizer-examples-rc3.json');
await mkdir(path.dirname(output), { recursive: true });
await writeFile(output, `${JSON.stringify(report, null, 2)}\n`);
console.log(`Organizer synthetic examples: ${results.length}/${results.length} passed (5 guide candidates, 3 held). ${path.relative(root, output)}`);
