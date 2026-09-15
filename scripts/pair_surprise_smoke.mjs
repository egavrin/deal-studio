import { spawnSync } from 'node:child_process';
import { randomInt, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const output = resolve(process.argv[2] ?? `artifacts/paired-surprise-${Date.now()}`);
const requested = Number(process.argv[3] ?? 10);
if (!Number.isInteger(requested) || requested < 1 || requested > 10) throw new Error('runs must be 1..10');
mkdirSync(output, { recursive: true });

const pool = [
  ['focus-timer', 'Build a compact focus timer. Choose a work duration, start, pause, reset, show remaining time and completed sessions.'],
  ['recipe-box', 'Build a recipe box. Add a recipe name, ingredients and minutes, browse recipes, mark one as cooked and show a small cooking history. Start empty.'],
  ['garden-log', 'Build a tiny garden log. Add plants with a name and watering interval, record watering, show which plant needs attention next and a history. Start empty.'],
  ['chore-wheel', 'Build a household chore wheel. Add people and chores, assign a chore, mark it complete and show a fair completion count by person. Start empty.'],
  ['language-sprint', 'Build a small vocabulary sprint. Add a word and meaning, quiz one card at a time, mark correct or retry, and show a session score.'],
  ['mood-checkin', 'Build a private daily mood check-in. Pick a mood, add an optional note, show today\'s entry and a simple weekly count. Start with no entries.'],
  ['plant-budget', 'Build a small plant shopping budget. Set a budget, add named purchases with integer prices, show spent and remaining money, and reject purchases above the remaining amount.'],
  ['meeting-notes', 'Build an offline meeting notes tool. Add a meeting title and notes, create action items, mark them done and show open versus completed counts. Start empty.'],
  ['walking-goal', 'Build a walking goal tracker. Set an integer daily step goal, add walk increments, show progress and remaining steps, and reset the day.'],
  ['bookmark-box', 'Build a compact bookmark box. Add a title and a category, browse saved bookmarks, mark one as read, and show saved versus read counts. Start empty.']
];
const selected = [];
while (selected.length < requested) selected.push(pool.splice(randomInt(pool.length), 1)[0]);
writeFileSync(join(output, 'requests.json'), JSON.stringify(selected, null, 2));

const pkg = 'com.dealstudio.app.debug';
function adb(args, binary = false) {
  const result = spawnSync('adb', args, { encoding: binary ? undefined : 'utf8', timeout: 720000, maxBuffer: 128e6 });
  if (result.error || result.status !== 0) throw new Error(`adb ${args.join(' ')} failed: ${result.error ?? result.stderr}`);
  return result.stdout;
}
function pull(path, destination) {
  const result = spawnSync('adb', ['pull', path, destination], { encoding: 'utf8', timeout: 120000, maxBuffer: 16e6 });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
}
function timing(text) {
  return Object.fromEntries(text.trim().split(/\n/).filter(Boolean).map(line => {
    const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1)];
  }));
}
const results = [];
for (const [index, [name, prompt]] of selected.entries()) {
  const folder = join(output, `${String(index + 1).padStart(2, '0')}-${name}`);
  mkdirSync(folder);
  writeFileSync(join(folder, 'request.txt'), prompt);
  const runId = `${Date.now()}-${name}`;
  const captureId = `${Date.now()}-html5-${randomUUID()}`;
  const common = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'request_base64', Buffer.from(prompt).toString('base64')];
  const deal = { status: 'not-run' }, html5 = { status: 'not-run' };
  try {
    const started = Date.now();
    const log = adb([...common, '-e', 'run_id', runId, '-e', 'class', 'com.offlineassistant.app.generatedapp.CanonicalGeneratedAppCloudDeviceTest#randomFlashNoReasoningGenerates', `${pkg}.test/androidx.test.runner.AndroidJUnitRunner`]);
    writeFileSync(join(folder, 'deal.instrumentation.log'), log);
    deal.status = /OK \(1 test\)/.test(log) && !log.includes('FAILURES!!!') ? 'success' : 'failure';
    deal.elapsedMs = Date.now() - started;
    const artifact = `random-flash-${runId}`;
    if (deal.status === 'success') {
      const archive = adb(['exec-out', 'run-as', pkg, 'tar', '-cf', '-', `files/canonical-live/${artifact}.deal`, `files/canonical-live/${artifact}.interface.json`, `files/canonical-live/${artifact}.ir.json`, `files/canonical-live/${artifact}.timings.txt`], true);
      writeFileSync(join(folder, 'deal-artifacts.tar'), archive);
      const metrics = adb(['shell', 'run-as', pkg, 'cat', `files/canonical-live/${artifact}.timings.txt`]);
      writeFileSync(join(folder, 'deal.timings.txt'), metrics); deal.metrics = timing(metrics);
      pull(`/sdcard/Download/${artifact}.png`, join(folder, 'deal-runtime.png'));
    }
  } catch (error) { deal.status = 'error'; deal.error = String(error); }
  try {
    const started = Date.now();
    const log = adb([...common, '-e', 'capture_id', captureId, '-e', 'class', 'com.offlineassistant.app.generatedapp.CanonicalGeneratedAppCloudDeviceTest#randomHtml5FlashNoReasoningGenerates', `${pkg}.test/androidx.test.runner.AndroidJUnitRunner`]);
    writeFileSync(join(folder, 'html5.instrumentation.log'), log);
    html5.status = /OK \(1 test\)/.test(log) && !log.includes('FAILURES!!!') ? 'success' : 'failure';
    html5.elapsedMs = Date.now() - started;
    if (html5.status === 'success') {
      const archive = adb(['exec-out', 'run-as', pkg, 'tar', '-cf', '-', `files/generation-run-captures/${captureId}/app.html`, `files/generation-run-captures/${captureId}/metrics.txt`], true);
      writeFileSync(join(folder, 'html5-artifacts.tar'), archive);
      const metrics = adb(['shell', 'run-as', pkg, 'cat', `files/generation-run-captures/${captureId}/metrics.txt`]);
      writeFileSync(join(folder, 'html5.timings.txt'), metrics); html5.metrics = timing(metrics);
      pull(`/sdcard/Download/${captureId}.png`, join(folder, 'html5-runtime.png'));
    }
  } catch (error) { html5.status = 'error'; html5.error = String(error); }
  results.push({ index: index + 1, name, prompt, deal, html5 });
  writeFileSync(join(output, 'results.json'), JSON.stringify(results, null, 2));
  console.log(`${index + 1}/${selected.length} ${name}: DEAL=${deal.status}, HTML5=${html5.status}`);
}
writeFileSync(join(output, 'README.md'), '# Paired surprise run\n\nEach folder contains the exact shared prompt, instrumentation output, archived source artifacts, timing metadata, and runtime screenshots.\n');
