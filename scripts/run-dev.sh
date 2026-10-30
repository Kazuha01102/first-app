#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
jar=target/myfirstapp-0.0.1-SNAPSHOT.jar
if [ ! -f "$jar" ]; then
  printf 'Build first: mvn clean package\n' >&2
  exit 1
fi
printf 'Starting SERVER CONTROL at http://127.0.0.1:8081/ (Ctrl+C to stop)\n'
# A native IPv4 loopback listener is also discoverable by WSL localhost forwarding.
exec java -Djava.net.preferIPv4Stack=true -jar "$jar" --server.address=127.0.0.1 --server.port=8081
