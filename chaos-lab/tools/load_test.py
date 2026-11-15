"""Bounded load test for Chaos Lab, standard library only. Never target external hosts."""
import argparse
import concurrent.futures
import json
import statistics
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser(description="Bounded localhost-only load test")
parser.add_argument("--mode", choices=["ok", "slow", "error"], default="ok")
parser.add_argument("--requests", type=int, default=30)
parser.add_argument("--concurrency", type=int, default=3)
parser.add_argument("--delay-ms", type=int, default=500)
args = parser.parse_args()
if not 1 <= args.requests <= 200 or not 1 <= args.concurrency <= 10 or not 0 <= args.delay_ms <= 3000:
    parser.error("limits: requests 1..200, concurrency 1..10, delay-ms 0..3000")

URL = f"http://127.0.0.1:18082/work?mode={args.mode}&ms={args.delay_ms}"


def request(_):
    started = time.monotonic()
    try:
        with urllib.request.urlopen(URL, timeout=8) as response:
            code = response.status
            response.read()
    except urllib.error.HTTPError as error:
        code = error.code
        error.close()
    except (urllib.error.URLError, TimeoutError, OSError):
        code = 0
    return code, (time.monotonic() - started) * 1000


started = time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
    results = list(pool.map(request, range(args.requests)))
elapsed = time.monotonic() - started
times = sorted(ms for _, ms in results)
codes = {}
for code, _ in results:
    codes[str(code)] = codes.get(str(code), 0) + 1
p95 = times[max(0, int(len(times) * 0.95 + 0.9999) - 1)]
print(json.dumps({
    "mode": args.mode, "requests": args.requests, "concurrency": args.concurrency,
    "elapsedSeconds": round(elapsed, 2), "requestsPerSecond": round(args.requests / max(elapsed, 0.001), 2),
    "meanMs": round(statistics.mean(times), 1), "p95Ms": round(p95, 1),
    "statusCodes": codes
}, indent=2))
