#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -f backend/pom.xml -Djava.awt.headless=true verify
npm --prefix frontend ci
npm --prefix frontend run format:check
npm --prefix frontend test
npm --prefix frontend run build
