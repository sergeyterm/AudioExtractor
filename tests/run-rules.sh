#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac -encoding UTF-8 -d "$work" app/src/main/java/com/audioextractor/app/AudioRules.java tests/RulesSmoke.java
java -cp "$work" RulesSmoke
