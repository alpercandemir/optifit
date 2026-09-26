#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
mvn -f backend/pom.xml -q -DskipTests package
npm --prefix frontend ci
java -Djava.awt.headless=true -jar backend/target/optifit-0.1.0.jar --server.address=127.0.0.1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT INT TERM
npm --prefix frontend run dev
