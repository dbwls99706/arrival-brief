import {readFileSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {resolve} from 'node:path';
import {pathToFileURL, fileURLToPath} from 'node:url';

const repo = resolve(process.argv[2] || fileURLToPath(new URL('../', import.meta.url)));
const corpusPath = resolve(repo, 'tests/award-scenarios.json');
const corpusBytes = readFileSync(corpusPath);
const corpus = JSON.parse(corpusBytes);
const enginePath = resolve(repo, 'dist/arrival/engine.mjs');
const {buildBrief, formatBrief, reviewAllowsConfirmation} = await import(pathToFileURL(enginePath));
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const completeReview = {mode:'parked', venueReviewed:true, heightReviewed:true, parkedConfirmed:true};
const results = corpus.cases.map(input => {
  const brief = buildBrief({text:input.text,height:input.height,mode:'parked'});
  let exported = null;
  try { exported = formatBrief(brief); } catch { /* A refused draft must not export. */ }
  const actual = {
    status:brief.status, canConfirm:brief.canConfirm,
    destination:brief.destination?.id ?? null,
    gate:brief.destination?.gate ?? null,
    level:brief.destination?.level ?? null,
    zone:brief.destination?.zone ?? null,
    permittedWithReviews:reviewAllowsConfirmation(brief, completeReview),
    permittedWithoutReviews:reviewAllowsConfirmation(brief, {mode:'parked'}),
    exportAvailable:exported !== null,
    exportSourceUrl:exported?.includes(corpus.sources[0]) ?? false,
    exportHasHeightLimit:exported?.includes('2.2m') ?? false,
    reason:brief.reason ?? null
  };
  const checks = Object.entries(input.expected).map(([field, expected]) => ({field, expected, actual:actual[field], pass:actual[field]===expected}));
  checks.push({field:'permittedWithReviews',expected:input.expected.canConfirm,actual:actual.permittedWithReviews,pass:actual.permittedWithReviews===input.expected.canConfirm});
  checks.push({field:'permittedWithoutReviews',expected:false,actual:actual.permittedWithoutReviews,pass:actual.permittedWithoutReviews===false});
  checks.push({field:'exportAvailable',expected:input.expected.canConfirm,actual:actual.exportAvailable,pass:actual.exportAvailable===input.expected.canConfirm});
  if(input.expected.canConfirm){
    for(const field of ['exportSourceUrl','exportHasHeightLimit']) checks.push({field,expected:true,actual:actual[field],pass:actual[field]===true});
  }
  for(const mode of ['driving','unknown']){
    const limited=buildBrief({text:input.text,height:input.height,mode});
    const allowed=reviewAllowsConfirmation(limited,{...completeReview,mode});
    checks.push({field:`${mode}.permittedWithReviews`,expected:false,actual:allowed,pass:allowed===false});
  }
  return {id:input.id,label:input.label,group:input.group,input:{text:input.text,height:input.height},expected:input.expected,actual,checks,pass:checks.every(x=>x.pass)};
});
const group = name => {const rows=results.filter(x=>x.group===name);return {passed:rows.filter(x=>x.pass).length,total:rows.length};};
const sourceCommit=execFileSync('git',['-C',repo,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
const committedEngine=execFileSync('git',['-C',repo,'show',`${sourceCommit}:dist/arrival/engine.mjs`]);
const sourceWorktreeChangedFromCommit=sha256(committedEngine)!==sha256(readFileSync(enginePath));
const report = {
  generatedAt:new Date().toISOString(),corpusId:corpus.corpusId,corpusSha256:sha256(corpusBytes),
  sourceCommit,sourceWorktreeChangedFromCommit,
  engineSha256:sha256(readFileSync(enginePath)),
  interpretation:'Development-discovered, fixed-case improvement recheck, not independent holdout accuracy. Constructed-case parser/guard evaluation only. No human baseline was measured. This does not estimate real-world accuracy, time saved, customer demand, accident prevention, Pleos behavior, or award probability.',
  summary:{all:{passed:results.filter(x=>x.pass).length,total:results.length},normal:group('normal'),constraint:group('constraint')},
  results
};
if(process.argv[3]) writeFileSync(resolve(process.argv[3]),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({corpusSha256:report.corpusSha256,engineSha256:report.engineSha256,summary:report.summary,results:results.map(x=>({id:x.id,label:x.label,status:x.actual.status,pass:x.pass}))},null,2));
process.exitCode=results.every(x=>x.pass)?0:1;
