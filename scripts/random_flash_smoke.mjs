import { spawnSync } from 'node:child_process';
import { randomInt } from 'node:crypto';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { resolve, join } from 'node:path';

const out = resolve(process.argv[2] ?? `artifacts/random-flash-${Date.now()}`);
mkdirSync(out, { recursive: true });
const pool = [
  ['scoreboard', 'A two-player scoreboard for a tabletop game. Enter player names, add or subtract one point for either player, show the leader and reset scores.'],
  ['tip-splitter', 'A restaurant tip splitter. Enter an integer bill, tip percentage and number of people, show the tip and cost per person rounded to whole units. Prevent division by zero.'],
  ['packing-list', 'A packing checklist for a weekend trip. Start with five items, toggle each packed, add an item with a text field, and show packed and remaining counts.'],
  ['water-counter', 'A water intake counter. Start at zero ml, add 200 or 350 ml using two buttons, display progress toward an editable integer goal initially 2000 ml, and reset the day.'],
  ['flashcards', 'A small vocabulary flashcard app with three cards. Reveal an answer, move to the next question, and show current card number. Wrap around after the last card.'],
  ['reading-log', 'A reading progress tracker. Enter a book title, total pages and current page; advance one page, show progress, and prevent advancing past the total.'],
  ['clicker-game', 'A small tap-to-score game. Start a round, allow ten scoring taps, show remaining taps and score, then display the result and allow a new round.'],
  ['budget', 'An offline spending envelope. Set an integer budget, enter an integer expense amount, record it, and show total spent and remaining funds with a warning when over budget.']
];
const chosen = process.env.SURPRISE === '1'
  ? Array.from({length: 3}, (_, i) => [`surprise-${i + 1}`, 'Generated on device by SurpriseAppPromptFactory; exact prompt is in the request replay.'])
  : process.argv[3] ? JSON.parse(readFileSync(process.argv[3], 'utf8')) : [];
while (chosen.length < 3) chosen.push(pool.splice(randomInt(pool.length), 1)[0]);
const pkg = 'com.dealstudio.app.debug';
function adb(args, binary = false) {
  const r = spawnSync('adb', args, { encoding: binary ? undefined : 'utf8', timeout: 600000, maxBuffer: 64e6 });
  if (r.error || r.status !== 0) throw new Error(`adb failed: ${r.error ?? r.stderr}`);
  return r.stdout;
}
try {
  if (adb(['get-state']).trim() !== 'device') throw new Error('Device is not ready');
} catch (error) {
  writeFileSync(join(out, 'run-status.json'), JSON.stringify({ status: 'infrastructure_unavailable', error: String(error), tested: 0 }, null, 2));
  console.error('NOT RUN: Android device is unavailable. No generation results recorded.');
  process.exit(2);
}
writeFileSync(join(out, 'requests.json'), JSON.stringify(chosen, null, 2));
const results = [];
for (const [index, [name, description]] of chosen.entries()) {
  const id = `${Date.now()}-${name}`;
  const artifact = `random-flash-${id}`;
  const folder = join(out, `${index + 1}-${name}`);
  mkdirSync(folder);
  const request = process.env.EXACT_REQUESTS === '1' ? description : `Build ${description} Use a clean native adaptive English UI with clear labels and a coherent AppTheme. No network, notifications or external services.`;
  writeFileSync(join(folder, 'request.txt'), request);
  console.log(`START ${name} Flash / none`);
  const start = Date.now();
  let success = false, error = null;
  try {
    const log = adb(['shell', 'am', 'instrument', '-w', '-r', '-e', 'request_base64', Buffer.from(request).toString('base64'), '-e', 'run_id', id,
      '-e', 'class', `com.offlineassistant.app.generatedapp.CanonicalGeneratedAppCloudDeviceTest#${process.env.SURPRISE === '1' ? 'surpriseFlashNoReasoningGenerates' : 'randomFlashNoReasoningGenerates'}`,
      `${pkg}.test/androidx.test.runner.AndroidJUnitRunner`]);
    writeFileSync(join(folder, 'instrumentation.log'), log);
    success = /OK \(1 test\)/.test(log) && !log.includes('FAILURES!!!');
  } catch (e) { error = String(e); }
  let timings = null;
  try {
    const names = adb(['shell', 'run-as', pkg, 'ls', 'files/canonical-live']).trim().split(/\s+/).filter(n => n.startsWith(artifact));
    if (names.length) writeFileSync(join(folder, 'artifacts.tar'), adb(['exec-out', 'run-as', pkg, 'tar', '-cf', '-', ...names.map(n => `files/canonical-live/${n}`)], true));
    if (success) {
      timings = adb(['shell', 'run-as', pkg, 'cat', `files/canonical-live/${artifact}.timings.txt`]);
      writeFileSync(join(folder, 'timings.txt'), timings);
      adb(['pull', `/sdcard/Download/${artifact}.png`, join(folder, 'preview.png')]);
    }
  } catch (e) { error = `${error ?? ''} ${e}`; }
  const status = error ? 'infrastructure_or_evidence_failure' : success ? 'compile_launch_restore_pass' : 'generation_or_runtime_failure';
  results.push({ name, artifact, success, status, elapsedMs: Date.now() - start, error, timings, folder });
  writeFileSync(join(out, 'results.json'), JSON.stringify(results, null, 2));
  console.log(`${status} ${name} ${Date.now() - start}ms`);
  if (error) break;
}
