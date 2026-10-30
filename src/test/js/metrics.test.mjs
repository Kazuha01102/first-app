import test from 'node:test';
import assert from 'node:assert/strict';
import { bytes, percent, duration, summarize, chartSegments, windowSamples } from '../../main/resources/static/js/metrics.mjs';
test('missing, invalid and zero measurements remain distinguishable', () => {
  assert.equal(bytes(null), 'N/A');
  assert.equal(bytes(-1), 'N/A');
  assert.equal(bytes(0), '0 B');
  assert.equal(bytes(1024), '1.0 KiB');
  assert.equal(percent(null), 'N/A');
  assert.equal(percent(NaN), 'N/A');
  assert.equal(percent(0), '0.0%');
  assert.equal(duration(null), 'N/A');
  assert.equal(duration(3661), '1h 1m');
});
test('averages exclude unavailable values, preserving real zeroes', () => {
  assert.deepEqual(summarize([{ v: 0 }, { v: null }, { v: 10 }], 'v'), { average: 5, max: 10 });
  assert.deepEqual(summarize([{ v: null }], 'v'), { average: null, max: null });
});
test('chart gaps are not bridged or replaced with fabricated zeroes', () => {
  const t = 1700000000000;
  const sample = (offset, v) => ({ timestamp: new Date(t + offset).toISOString(), v });
  const points = [sample(0, 5), sample(1000, null), sample(2000, 10), sample(9000, 20)];
  const segments = chartSegments(points, 'v', t, t + 10000);
  assert.equal(segments.length, 3);
  assert.equal(segments.flat().length, 3);
  assert.equal(segments[0][0].y, 152);
  assert.equal(windowSamples(points, 5, t + 10000).length, 1);
});
