import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';

const options = Object.fromEntries(process.argv.slice(2).map(arg => {
  const [key, ...value] = arg.replace(/^--/, '').split('=');
  return [key, value.join('=')];
}));
const samples = Number(options.samples ?? 1);
if (!Number.isInteger(samples) || samples < 1 || samples > 100) throw new Error('samples must be 1..100 per profile');
const output = resolve(options.output ?? `artifacts/model-protocol-benchmark-${Date.now()}`);
mkdirSync(output, { recursive: true });
const pkg = 'com.dealstudio.app.debug';
const testClass = 'com.offlineassistant.app.generatedapp.CanonicalGeneratedAppCloudDeviceTest';
const suites = { counter: [
  { name: 'low', method: 'sourceFreeCounterCompactGenerates', artifact: 'source-free-counter-compact-2026-09-07' },
  { name: 'none', method: 'sourceFreeCounterNoReasoningGenerates', artifact: 'source-free-counter-none-2026-09-07' }
], complex: [
  { name: 'pill-low', method: 'pillCounterCompactLowGenerates', artifact: 'pill-counter-compact-low-2026-09-07' },
  { name: 'pill-none', method: 'pillCounterCompactGenerates', artifact: 'pill-counter-compact-none-2026-09-07' },
  { name: 'tool-lending-none', method: 'heldOutToolLendingGenerates', artifact: 'held-out-tool-lending-2026-09-07' },
  { name: 'study-cards-none', method: 'heldOutStudyCardsGenerates', artifact: 'held-out-study-cards-2026-09-07' }
] };
const selectedSuite = suites[options.suite ?? 'counter'];
const variants = options.profiles ? selectedSuite?.filter(v => options.profiles.split(',').includes(v.name)) : selectedSuite;
if (!variants) throw new Error('suite must be counter or complex');
if (!variants.length) throw new Error('no matching profiles');
let seed = Number(options.seed ?? 20260907) >>> 0;
const initialSeed = seed;
function random() {
  seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
  return seed / 4294967296;
}
function adb(args, binary = false) {
  const result = spawnSync('adb', args, { encoding: binary ? undefined : 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: 900000 });
  if (result.error || result.status !== 0) throw new Error(`adb failed: ${result.error ?? result.stderr}`);
  return result.stdout;
}
const schedule = Array.from({ length: samples }, () => random() < 0.5 ? variants : [...variants].reverse()).flat();
writeFileSync(join(output, 'schedule.json'), JSON.stringify({ seed: initialSeed, samples, schedule }, null, 2));
const results = [];
for (const [index, variant] of schedule.entries()) {
  const folder = join(output, `${String(index + 1).padStart(3, '0')}-${variant.name}`);
  mkdirSync(folder);
  const previousNames = new Set(adb(['shell', 'run-as', pkg, 'ls', 'files/canonical-live']).trim().split(/\r?\n/));
  const startedAt = new Date().toISOString();
  console.log(`${index + 1}/${schedule.length}: ${variant.name}, ${startedAt}`);
  const log = adb(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', `${testClass}#${variant.method}`, `${pkg}.test/androidx.test.runner.AndroidJUnitRunner`]);
  writeFileSync(join(folder, 'instrumentation.log'), log);
  const success = /OK \(1 test\)/.test(log) && !/FAILURES!!!/.test(log);
  const names = adb(['shell', 'run-as', pkg, 'ls', '-t', 'files/canonical-live']).trim().split(/\r?\n/);
  const trace = names.find(name => name.startsWith(`${variant.artifact}-replay-`) && !previousNames.has(name));
  const capturedFailure = /--- COMPILER TOOL TRACE ---/.test(log);
  const selected = names.filter(name => name === trace || (trace && (success
    ? name.startsWith(`${variant.artifact}.`) && !name.includes('.failed.') && !name.endsWith('.failure.txt')
    : capturedFailure && (name.startsWith(`${variant.artifact}.failed.`) || name === `${variant.artifact}.failure.txt`))));
  if (selected.length) writeFileSync(join(folder, 'device-artifacts.tar'), adb([
    'exec-out', 'run-as', pkg, 'tar', '-cf', '-', ...selected.map(name => `files/canonical-live/${name}`)
  ], true));
  let metrics = null;
  if (success) {
    const timing = adb(['shell', 'run-as', pkg, 'cat', `files/canonical-live/${variant.artifact}.timings.txt`]);
    writeFileSync(join(folder, 'timings.txt'), timing);
    metrics = Object.fromEntries(timing.trim().split(/\r?\n/).map(line => {
      const [key, value] = line.split('=');
      return [key, value === 'null' ? null : Number(value)];
    }));
    adb(['pull', `/sdcard/Download/${variant.artifact}.png`, join(folder, variant.artifact.startsWith('source-free-counter') ? 'after-taps.png' : 'saved-preview.png')]);
  }
  results.push({ profile: variant.name, startedAt, success, metrics, folder });
  writeFileSync(join(output, 'results.json'), JSON.stringify(results, null, 2));
  console.log(`  ${success ? `PASS wall=${metrics.wall}ms, output=${metrics.deal_output_tokens + metrics.dealui_output_tokens}` : 'FAIL (traces retained)'}`);
}
const summaries = variants.map(variant => {
  const runs = results.filter(run => run.profile === variant.name);
  const successful = runs.filter(run => run.success);
  const times = successful.map(run => run.metrics.wall).sort((a, b) => a - b);
  const percentile = p => times.length ? times[Math.max(0, Math.ceil(times.length * p) - 1)] : null;
  return { profile: variant.name, samples: runs.length, success: successful.length,
    medianSuccessfulWallMs: percentile(0.5), p95SuccessfulWallMs: percentile(0.95),
    note: 'Failed runs are counted separately, not omitted from success rate. Small samples do not establish p95 or reliability.' };
});
writeFileSync(join(output, 'summary.json'), JSON.stringify(summaries, null, 2));
console.log(JSON.stringify(summaries, null, 2));
