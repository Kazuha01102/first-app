// Optional browser QA: supply PLAYWRIGHT_MODULE if Playwright is installed outside this repository.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const moduleName = process.env.PLAYWRIGHT_MODULE;
const { chromium } = await import(moduleName ? pathToFileURL(path.resolve(moduleName)).href : 'playwright');
const base = 'http://127.0.0.1:8081';
const output = path.resolve('target/browser-check');
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1460, height: 1040 }, reducedMotion: 'reduce' });
const page = await context.newPage();
const exceptions = [], consoleErrors = [], checks = [];
page.on('pageerror', error => exceptions.push(error.message));
page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
let polls = 0;
page.on('request', request => { if (request.url() === base + '/api/monitor') polls++; });
const ready = async () => {
  await page.waitForFunction(() => document.getElementById('connection-status')?.textContent === 'Online');
  await page.waitForFunction(() => !document.getElementById('refresh').disabled);
};
const manual = async () => { await page.locator('#refresh').click(); await ready(); };
try {
  for (const suffix of ['', '/overview', '/cpu', '/memory', '/jvm', '/network', '/health', '/history', '/logs']) {
    const response = await context.request.get(base + '/api/monitor' + suffix);
    assert.equal(response.status(), 200, suffix);
    assert.match(response.headers()['content-type'], /application\/json/);
  }
  checks.push('9 live JSON APIs');
  await page.goto(base);
  await ready();
  assert.match(await page.locator('#memory-value').innerText(), /^\d+\.\d%$/);
  assert.notEqual(await page.locator('#heap-value').innerText(), 'N/A');
  assert.match(await page.locator('#sidebar-environment').innerText(), /WSL2/);
  await page.waitForFunction(() => document.querySelectorAll('#cpu-chart path').length >= 2);
  assert.equal(consoleErrors.length, 0, 'No initial console/CSP errors');
  checks.push('actual metrics, SVG graphs and scope labels');
  const before = polls;
  const sync = await page.locator('#last-update').getAttribute('datetime');
  await page.waitForFunction(old => document.getElementById('last-update').getAttribute('datetime') !== old, sync, { timeout: 8000 });
  assert.ok(polls > before);
  checks.push('5-second automatic refresh');
  await page.locator('#auto-refresh').click();
  await page.waitForFunction(() => !document.getElementById('refresh').disabled);
  const paused = polls;
  await page.waitForTimeout(5400);
  assert.equal(polls, paused, 'Pause stops polling');
  await manual();
  assert.ok(polls > paused);
  checks.push('pause and manual refresh');
  await page.locator('[data-range="60"]').click();
  assert.equal(await page.locator('[data-range="60"]').getAttribute('aria-pressed'), 'true');
  await page.locator('#log-level').selectOption('WARN');
  await page.locator('#log-level').selectOption('ALL');
  await page.locator('#log-search').fill('unmatched-search-value');
  assert.match(await page.locator('#log-list').innerText(), /条件に一致/);
  await page.locator('#log-search').clear();
  checks.push('range selection and log filtering');
  await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo(0, 0); });
  await page.screenshot({ path: path.join(output, 'desktop.png'), fullPage: true });

  const retained = await page.locator('#cpu-value').innerText();
  await page.route(base + '/api/monitor', route => route.abort('failed'));
  await page.locator('#refresh').click();
  await page.waitForFunction(() => document.getElementById('connection-status').textContent === 'Offline');
  assert.equal(await page.locator('#cpu-value').innerText(), retained);
  assert.equal(await page.locator('#connection-error').isVisible(), true);
  await page.unroute(base + '/api/monitor');
  await manual();
  checks.push('API failure, stale values and recovery');

  await page.route(base + '/api/monitor/history?seconds=900', route => route.abort('failed'));
  await manual();
  assert.equal(await page.locator('#connection-status').innerText(), 'Online');
  assert.equal(await page.locator('#partial-error').isVisible(), true);
  await page.unroute(base + '/api/monitor/history?seconds=900');
  await manual();
  checks.push('partial history API failure keeps primary metrics online');

  const emptyPage = await context.newPage();
  await emptyPage.route(base + '/api/monitor', route => route.abort('failed'));
  await emptyPage.goto(base);
  await emptyPage.waitForFunction(() => document.getElementById('connection-status').textContent === 'Offline');
  assert.equal(await emptyPage.locator('#cpu-value').innerText(), 'N/A');
  await emptyPage.close();
  checks.push('offline first load without invented measurements');

  const real = await (await context.request.get(base + '/api/monitor')).json();
  real.cpu.usagePercent = null;
  real.memory.totalBytes = null;
  real.memory.usedBytes = null;
  real.memory.availableBytes = null;
  real.memory.usagePercent = null;
  await page.route(base + '/api/monitor', route => route.fulfill({ json: real }));
  await manual();
  assert.equal(await page.locator('#cpu-value').innerText(), 'N/A');
  assert.equal(await page.locator('#memory-value').innerText(), 'N/A');
  await page.unroute(base + '/api/monitor');
  await manual();
  checks.push('unavailable measurements render N/A');

  const actualLogs = await (await context.request.get(base + '/api/monitor/logs?limit=200')).json();
  const entry = { id: Math.max(0, ...actualLogs.map(log => log.id)) + 1, timestamp: new Date().toISOString(),
    level: 'ERROR', logger: 'myfirstapp.BrowserTest', message: '<img src=x onerror="window.__xss=true"> test-only error' };
  await page.route(base + '/api/monitor/logs?limit=200', route => route.fulfill({ json: [...actualLogs, entry] }));
  await manual();
  assert.equal(await page.locator('#log-alert').isVisible(), true);
  assert.equal(await page.locator('#log-list img').count(), 0);
  assert.equal(await page.evaluate(() => window.__xss), undefined);
  await page.unroute(base + '/api/monitor/logs?limit=200');
  checks.push('new ERROR notification and escaped log text (browser-only fixture)');
  await manual();
  // Screenshot the real application again, without the test-only log notification.
  await page.reload();
  await ready();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => window.scrollTo(0, 0));
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: path.join(output, 'mobile.png'), fullPage: true });
  checks.push('390px mobile layout without horizontal overflow');

  await page.goto(base + '/calculator.html');
  await page.locator('#a').fill('10');
  await page.locator('#b').fill('5');
  for (const [op, expected] of [['add', '15.0'], ['subtract', '5.0'], ['multiply', '50.0'], ['divide', '2.0']]) {
    await page.locator('#op').selectOption(op);
    await page.locator('button[type="submit"]').click();
    await page.waitForFunction(value => document.getElementById('result').textContent === '計算結果:' + value, expected);
  }
  await page.locator('#b').fill('0');
  await page.locator('button[type="submit"]').click();
  await page.waitForFunction(() => document.getElementById('result').textContent === '0では割れません');
  checks.push('calculator four operations and division by zero');
  assert.deepEqual(exceptions, [], 'No uncaught browser exceptions');
  const result = { passed: checks.length, checks, exceptions, screenshots: ['desktop.png', 'mobile.png'] };
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
} finally { await browser.close(); }
