import { isNumber, percent, number, bytes, duration, ms, windowSamples, summarize, chartSegments } from './metrics.mjs';

const $ = id => document.getElementById(id);
const text = (id, value) => { $(id).textContent = value ?? 'N/A'; };
const time = date => new Date(date).toLocaleTimeString('ja-JP', { hour12: false });
let snapshot = null, history = [], logs = [], range = 300;
let automatic = true, inFlight = false, timer = null, lastLogId = 0, initializedLogs = false;
const SVG = 'http://www.w3.org/2000/svg';

function bar(id, value) {
  const valid = isNumber(value);
  $(id).style.width = valid ? Math.min(100, Math.max(0, value)) + '%' : '0%';
  $(id).parentElement.classList.toggle('empty-bar', !valid);
}

function showSnapshot(data) {
  const { overview: o, cpu, memory: m, jvm, network: n } = data;
  text('application-name', o.application);
  text('os-name', o.osName);
  text('java-version', 'OpenJDK ' + o.javaVersion);
  text('jvm-java', o.javaVersion);
  text('system-uptime', duration(o.systemUptimeSeconds));
  text('last-update', time(data.collectedAt));
  $('last-update').dateTime = data.collectedAt;
  text('sidebar-environment', o.environment + ' · ' + o.osName);
  text('scope-label', o.environment + ' / Linux system');
  text('footer-scope', o.environment === 'WSL2' ? 'WSL2のLinux環境を監視 · Windowsホスト全体ではありません' : o.systemScope);
  text('cpu-value', percent(cpu.usagePercent));
  text('cpu-chart-current', percent(cpu.usagePercent));
  text('cpu-cores', number(cpu.cores) + ' logical cores');
  bar('cpu-meter', cpu.usagePercent);
  text('memory-value', percent(m.usagePercent));
  text('memory-chart-current', percent(m.usagePercent));
  text('memory-ring-value', percent(m.usagePercent));
  text('memory-total-summary', bytes(m.usedBytes) + ' / ' + bytes(m.totalBytes));
  text('memory-used', bytes(m.usedBytes));
  text('memory-available', bytes(m.availableBytes));
  text('memory-total', bytes(m.totalBytes));
  text('memory-free-detail', bytes(m.availableBytes));
  text('swap', bytes(m.swapUsedBytes) + ' / ' + bytes(m.swapTotalBytes));
  bar('memory-meter', m.usagePercent);
  const p = isNumber(m.usagePercent) ? Math.min(100, Math.max(0, m.usagePercent)) : 0;
  $('memory-ring').style.background = 'conic-gradient(var(--cyan) ' + p + '%, #213045 ' + p + '%)';
  text('heap-value', bytes(jvm.heapUsedBytes));
  text('heap-max-summary', bytes(jvm.heapMaxBytes) + ' max heap');
  text('jvm-heap', bytes(jvm.heapUsedBytes) + ' / ' + bytes(jvm.heapMaxBytes));
  bar('heap-meter', isNumber(jvm.heapMaxBytes) && jvm.heapMaxBytes > 0 ? jvm.heapUsedBytes / jvm.heapMaxBytes * 100 : null);
  text('non-heap', bytes(jvm.nonHeapUsedBytes));
  text('thread-count', number(jvm.threadCount));
  text('jvm-uptime', duration(jvm.uptimeSeconds));
  text('response-value', ms(n.averageApiResponseMs));
  text('request-summary', number(n.httpRequests) + ' completed requests');
  text('network-rx', isNumber(n.receiveBytesPerSecond) ? bytes(n.receiveBytesPerSecond) + '/s' : 'N/A');
  text('network-tx', isNumber(n.sendBytesPerSecond) ? bytes(n.sendBytesPerSecond) + '/s' : 'N/A');
  text('network-rx-total', bytes(n.receivedBytes) + ' total');
  text('network-tx-total', bytes(n.sentBytes) + ' total');
  text('network-interface', n.interfaces?.join(' + ') || 'N/A');
  text('http-requests', number(n.httpRequests));
  text('http-errors', number(n.httpErrors));
  text('last-api-response', ms(n.lastApiResponseMs));
  $('http-errors').classList.toggle('cyan', n.httpErrors === 0);
}

function svgNode(tag, attrs) {
  const node = document.createElementNS(SVG, tag);
  Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, String(value)));
  return node;
}

function chart(id, key, color, samples, from, to) {
  const svg = $(id + '-chart');
  svg.replaceChildren();
  const defs = svgNode('defs', {});
  const gradient = svgNode('linearGradient', { id: id + '-fill', x1: 0, x2: 0, y1: 0, y2: 1 });
  gradient.append(svgNode('stop', { offset: '0%', 'stop-color': color, 'stop-opacity': .22 }),
    svgNode('stop', { offset: '100%', 'stop-color': color, 'stop-opacity': .015 }));
  defs.append(gradient);
  svg.append(defs);
  for (let y = 0; y <= 160; y += 40) {
    svg.append(svgNode('line', { x1: 0, x2: 600, y1: y, y2: y, stroke: '#243047', 'stroke-width': .65, 'stroke-dasharray': '3 5' }));
  }
  const segments = chartSegments(samples, key, from, to);
  let count = 0;
  for (const segment of segments) {
    count += segment.length;
    const line = segment.map((point, i) => (i ? 'L' : 'M') + point.x.toFixed(2) + ',' + point.y.toFixed(2)).join(' ');
    if (segment.length > 1) {
      const area = line + ' L' + segment.at(-1).x.toFixed(2) + ',160 L' + segment[0].x.toFixed(2) + ',160 Z';
      svg.append(svgNode('path', { d: area, fill: 'url(#' + id + '-fill)', stroke: 'none' }));
      svg.append(svgNode('path', { d: line, fill: 'none', stroke: color, 'stroke-width': 2, 'vector-effect': 'non-scaling-stroke' }));
    }
    const last = segment.at(-1);
    if (last) svg.append(svgNode('circle', { cx: last.x, cy: last.y, r: 2.8, fill: color, stroke: '#111927', 'stroke-width': 1 }));
  }
  $(id + '-empty').hidden = count >= 2;
  $(id + '-empty').textContent = count ? '次の実測サンプルを待っています…' : 'データ未取得 / N/A';
  const labels = $(id + '-axis').children;
  [from, (from + to) / 2, to].forEach((value, i) => { labels[i].textContent = samples.length ? time(value) : '—'; });
  svg.setAttribute('aria-description', count + '件の実測サンプル。取得できない区間は空白です。');
}

function showCharts() {
  const anchor = snapshot ? Date.parse(snapshot.collectedAt) : Date.now();
  const samples = windowSamples(history, range, anchor);
  // Zoom to collected data while warming up. The time axis always shows actual timestamps.
  const from = samples.length ? Math.max(anchor - range * 1000, Date.parse(samples[0].timestamp)) : anchor - range * 1000;
  const to = Math.max(from + 1000, samples.length ? Date.parse(samples.at(-1).timestamp) : anchor);
  chart('cpu', 'cpuPercent', '#639cff', samples, from, to);
  chart('memory', 'memoryPercent', '#38cfcd', samples, from, to);
  const cpu = summarize(samples, 'cpuPercent');
  text('cpu-average', percent(cpu.average));
  text('cpu-peak', percent(cpu.max));
  text('sample-count', samples.length + ' samples · ' + Math.max(0, Math.floor((to - from) / 1000)) + 's');
}

function showLogs() {
  const level = $('log-level').value;
  const search = $('log-search').value.trim().toLowerCase();
  const filtered = logs.filter(log => (level === 'ALL' || log.level === level)
    && (log.message + ' ' + log.logger).toLowerCase().includes(search)).toReversed();
  const container = $('log-list');
  container.replaceChildren();
  text('logs-count', filtered.length + ' entries');
  if (!filtered.length) {
    const empty = document.createElement('div');
    empty.className = 'log-empty';
    empty.textContent = logs.length ? '条件に一致するログはありません。' : '最近のアプリケーションログはありません。';
    container.append(empty);
    return;
  }
  for (const log of filtered) {
    const row = document.createElement('div');
    row.className = 'log-row';
    const timestamp = document.createElement('time');
    timestamp.className = 'log-time';
    timestamp.dateTime = log.timestamp;
    timestamp.textContent = time(log.timestamp);
    const badge = document.createElement('span');
    badge.className = 'log-level ' + log.level.toLowerCase();
    badge.textContent = log.level;
    const message = document.createElement('div');
    message.className = 'log-message';
    const source = document.createElement('span');
    source.className = 'log-source';
    source.textContent = log.logger.split('.').at(-1);
    message.append(source, document.createTextNode(log.message));
    row.append(timestamp, badge, message);
    container.append(row);
  }
}

async function getJson(url) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 4000);
  try {
    const response = await fetch(url, { signal: controller.signal, cache: 'no-store', headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    return await response.json();
  } finally { clearTimeout(timeout); }
}

function connection(online) {
  const status = $('connection-status');
  status.textContent = online ? 'Online' : 'Offline';
  status.className = 'status-badge' + (online ? '' : ' offline');
  document.body.dataset.stale = String(!online);
  $('connection-error').hidden = online;
  if (!snapshot && !online) {
    for (const el of document.querySelectorAll('.metric-value')) el.textContent = 'N/A';
    for (const el of document.querySelectorAll('#main [id]')) {
      if (el.textContent.trim() === '—') el.textContent = 'N/A';
    }
  }
}

async function refresh() {
  if (inFlight) return;
  inFlight = true;
  const started = performance.now();
  clearTimeout(timer);
  $('refresh').disabled = true;
  try {
    const results = await Promise.allSettled([
      getJson('/api/monitor'), getJson('/api/monitor/history?seconds=900'), getJson('/api/monitor/logs?limit=200')
    ]);
    const [current, series, recent] = results;
    const errors = [];
    if (current.status === 'fulfilled') {
      const data = current.value;
      if (!data.overview || !data.cpu || !data.memory || !data.jvm || !data.network
          || !Number.isFinite(Date.parse(data.collectedAt))) throw new Error('Invalid monitoring response');
      snapshot = data;
      showSnapshot(snapshot);
      const fresh = Date.now() - Date.parse(data.collectedAt) < 15000;
      connection(fresh);
      if (!fresh) errors.push('サンプラーの更新が15秒以上停止しています。最終取得値を表示しています。');
    } else connection(false);
    if (series.status === 'fulfilled' && Array.isArray(series.value.samples)) history = series.value.samples;
    else errors.push('グラフ履歴を更新できません。前回の取得データを保持しています。');
    if (recent.status === 'fulfilled' && Array.isArray(recent.value)) {
      logs = recent.value;
      const newErrors = logs.filter(log => log.level === 'ERROR' && log.id > lastLogId);
      if (newErrors.length && initializedLogs) {
        $('log-alert').textContent = newErrors.length + '件の新しいERRORログを検出しました。Application logsを確認してください。';
        $('log-alert').hidden = false;
      }
      lastLogId = Math.max(lastLogId, ...logs.map(log => log.id));
      initializedLogs = true;
      const errorCount = logs.filter(log => log.level === 'ERROR').length;
      text('error-count', number(errorCount));
      $('error-count').hidden = errorCount === 0;
      showLogs();
    } else errors.push('ログを更新できません。前回のログを保持しています。');
    text('partial-error', errors.join(' '));
    $('partial-error').hidden = errors.length === 0;
    showCharts();
  } catch {
    connection(false);
  } finally {
    inFlight = false;
    $('refresh').disabled = false;
    if (automatic) timer = setTimeout(refresh, Math.max(0, 5000 - (performance.now() - started)));
  }
}

$('refresh').addEventListener('click', refresh);
$('auto-refresh').addEventListener('click', () => {
  automatic = !automatic;
  $('auto-refresh').setAttribute('aria-pressed', String(automatic));
  text('auto-label', automatic ? 'Live · 5秒更新' : '一時停止');
  clearTimeout(timer);
  if (automatic) refresh();
});
document.querySelectorAll('[data-range]').forEach(button => button.addEventListener('click', () => {
  range = Number(button.dataset.range);
  document.querySelectorAll('[data-range]').forEach(other => {
    const selected = other === button;
    other.classList.toggle('selected', selected);
    other.setAttribute('aria-pressed', String(selected));
  });
  showCharts();
}));
$('log-search').addEventListener('input', showLogs);
$('log-level').addEventListener('change', showLogs);
const links = [...document.querySelectorAll('.nav-link[href^="#"]')];
links.forEach(link => link.addEventListener('click', () => {
  links.forEach(other => other.classList.toggle('active', other === link));
}));
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && automatic && !inFlight) refresh();
});
window.addEventListener('pagehide', () => clearTimeout(timer));
showCharts();
refresh();
