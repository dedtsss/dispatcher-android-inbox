#!/usr/bin/env bash
set -euo pipefail
# Только выделенный одноразовый Android runner приложения, после основных тестов.
[ "${GITHUB_ACTIONS:-}" = true ] && [ "${GITHUB_REPOSITORY:-}" = dedtsss/dispatcher-android-inbox ]
mkdir -p visual
# Снимки не должны расходовать второй запуск эмулятора; опубликованный baseline
# используется только при его наличии после install-over в этом же build job.
export CATCHING_VISUAL_BASELINE="${CATCHING_VISUAL_BASELINE:-history-apk/v0610/app-release.apk}"
python3 scripts/ci/capture-release-ui.py
