"""Explicit opt-in, time-limited CPU experiment. Run INSIDE the limited container."""
import argparse
import hashlib
import time

parser = argparse.ArgumentParser()
parser.add_argument("--seconds", type=int, default=10)
args = parser.parse_args()
if not 1 <= args.seconds <= 30:
    parser.error("--seconds must be between 1 and 30")
deadline = time.monotonic() + args.seconds
iterations = 0
data = b"chaos-lab-only"
while time.monotonic() < deadline:
    data = hashlib.sha256(data).digest()
    iterations += 1
print(f"Completed {iterations} hash iterations in {args.seconds} seconds")
