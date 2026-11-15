"""Basic local unit/integration tests: no Docker required."""
import json
import os
import subprocess
import sys
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class LabTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import socket
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        cls.port = sock.getsockname()[1]
        sock.close()
        cls.process = subprocess.Popen(
            [sys.executable, "-u", str(ROOT / "app/server.py")],
            env={**os.environ, "PORT": str(cls.port)},
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        for _ in range(60):
            try:
                urllib.request.urlopen(f"http://127.0.0.1:{cls.port}/health", timeout=0.2).close()
                break
            except (OSError, urllib.error.URLError):
                time.sleep(0.05)
        else:
            raise RuntimeError("server did not start")

    @classmethod
    def tearDownClass(cls):
        cls.process.terminate()
        cls.process.wait(timeout=5)

    def fetch(self, path):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{self.port}{path}", timeout=5) as r:
                return r.status, json.load(r)
        except urllib.error.HTTPError as e:
            with e:
                return e.code, json.load(e)

    def test_health(self):
        code, payload = self.fetch("/health")
        self.assertEqual(code, 200)
        self.assertEqual(payload["status"], "UP")

    def test_normal(self):
        code, payload = self.fetch("/work?mode=ok")
        self.assertEqual(code, 200)
        self.assertEqual(payload["mode"], "ok")

    def test_error(self):
        code, _ = self.fetch("/work?mode=error")
        self.assertEqual(code, 503)

    def test_invalid_delay(self):
        code, _ = self.fetch("/work?mode=slow&ms=99999")
        self.assertEqual(code, 400)

    def test_metrics(self):
        code, payload = self.fetch("/metrics")
        self.assertEqual(code, 200)
        self.assertIn("requests", payload)


if __name__ == "__main__":
    unittest.main()
