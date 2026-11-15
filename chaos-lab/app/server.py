"""Bounded, local-only chaos experiment HTTP service (Python standard library)."""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

STARTED = time.monotonic()
COUNTS = {"requests": 0, "errors": 0, "slow": 0}
LOCK = threading.Lock()
MAX_DELAY_MS = 3000


class Handler(BaseHTTPRequestHandler):
    server_version = "ChaosLab/1.0"

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        path = urlsplit(self.path)
        if path.path == "/health":
            self.send_json(200, {"status": "UP", "service": "chaos-lab", "uptimeSeconds": round(time.monotonic() - STARTED, 1)})
            return
        if path.path == "/metrics":
            with LOCK:
                counts = COUNTS.copy()
            self.send_json(200, {"service": "chaos-lab", **counts, "uptimeSeconds": round(time.monotonic() - STARTED, 1)})
            return
        if path.path != "/work":
            self.send_json(404, {"error": "not found", "endpoints": ["/health", "/metrics", "/work?mode=ok", "/work?mode=slow&ms=500", "/work?mode=error"]})
            return

        params = parse_qs(path.query)
        mode = params.get("mode", ["ok"])[0]
        if mode not in ("ok", "slow", "error"):
            self.send_json(400, {"error": "mode must be ok, slow or error"})
            return
        delay = 0
        if mode == "slow":
            try:
                delay = int(params.get("ms", ["500"])[0])
            except ValueError:
                self.send_json(400, {"error": "ms must be an integer"})
                return
            if not 0 <= delay <= MAX_DELAY_MS:
                self.send_json(400, {"error": "ms must be between 0 and 3000"})
                return

        with LOCK:
            COUNTS["requests"] += 1
            if mode == "error":
                COUNTS["errors"] += 1
            elif mode == "slow":
                COUNTS["slow"] += 1

        if delay:
            time.sleep(delay / 1000)
        if mode == "error":
            self.send_json(503, {"status": "FAIL", "reason": "simulated service unavailable"})
        else:
            self.send_json(200, {"status": "OK", "mode": mode, "delayMs": delay})

    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args), flush=True)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    server.daemon_threads = True
    print("Chaos Lab listening on port", port, flush=True)
    server.serve_forever()
