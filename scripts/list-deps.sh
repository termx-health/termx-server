#!/usr/bin/env bash
# List the resolved compile/runtime dependency tree of the termx-app module,
# one Maven coordinate per line. Useful for licence / SBOM checks and quick
# greps over the runtime classpath.
set -euo pipefail

# Run from repo root regardless of where the script is invoked from.
cd "$(dirname "${BASH_SOURCE[0]}")/.."

./gradlew termx-app:dependencies \
  | sed -n "s/.*\+\-\- \(.*\)/\1/p" \
  | grep -v "^project" \
  | grep -v "(n)" \
  | sed 's/\(:.*\)\? \-> /:/g' \
  | sed 's/ ([\*c])$//g' \
  | sed  's/$/  /g' \
  | sort \
  | uniq
