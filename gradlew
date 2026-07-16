#!/usr/bin/env sh
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "[Skin Powers] Gradle bulunamadı. GitHub Actions otomatik Gradle 9.5.1 kurar." >&2
exit 1
