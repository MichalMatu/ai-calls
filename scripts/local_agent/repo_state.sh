#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
git fetch origin --prune >/dev/null
printf 'head=%s\n' "$(git rev-parse HEAD)"
printf 'branch=%s\n' "$(git branch --show-current)"
echo 'status:'
git status --short
echo 'remote_branches:'
git branch -r
