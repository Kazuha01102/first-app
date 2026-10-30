export const isNumber = value => typeof value === 'number' && Number.isFinite(value);
export const percent = value => isNumber(value) ? `${value.toFixed(1)}%` : 'N/A';
export const number = value => isNumber(value) ? new Intl.NumberFormat('en-US').format(value) : 'N/A';
export function bytes(value, digits = 1) {
  if (!isNumber(value) || value < 0) return 'N/A';
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
  const index = value === 0 ? 0 : Math.min(4, Math.floor(Math.log(value) / Math.log(1024)));
  return (value / 1024 ** index).toFixed(index ? digits : 0) + ' ' + units[index];
}
export function duration(seconds) {
  if (!isNumber(seconds) || seconds < 0) return 'N/A';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor(seconds % 86400 / 3600);
  const minutes = Math.floor(seconds % 3600 / 60);
  if (days) return days + 'd ' + hours + 'h ' + minutes + 'm';
  if (hours) return hours + 'h ' + minutes + 'm';
  return minutes + 'm ' + Math.floor(seconds % 60) + 's';
}
export const ms = value => isNumber(value) ? value.toFixed(2) + ' ms' : 'N/A';
export function windowSamples(samples, seconds, now = Date.now()) {
  return samples.filter(sample => {
    const time = Date.parse(sample.timestamp);
    return Number.isFinite(time) && time >= now - seconds * 1000 && time <= now + 2000;
  }).sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp));
}
export function summarize(samples, key) {
  const values = samples.map(s => s[key]).filter(isNumber);
  return values.length ? { average: values.reduce((a, b) => a + b, 0) / values.length, max: Math.max(...values) }
    : { average: null, max: null };
}
/** Preserve missing values and sampling gaps instead of drawing invented zeroes. */
export function chartSegments(samples, key, from, to, width = 600, height = 160) {
  const segments = [];
  let segment = [];
  for (const sample of samples) {
    const time = Date.parse(sample.timestamp), value = sample[key];
    if (!isNumber(value) || !Number.isFinite(time)) {
      if (segment.length) segments.push(segment);
      segment = [];
      continue;
    }
    if (time < from || time > to) continue;
    if (segment.length && time - segment.at(-1).time > 3000) {
      segments.push(segment);
      segment = [];
    }
    segment.push({ x: (time - from) / Math.max(1, to - from) * width,
      y: height - Math.min(100, Math.max(0, value)) / 100 * height, time });
  }
  if (segment.length) segments.push(segment);
  return segments;
}
