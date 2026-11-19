#!/usr/bin/env bash
set -euo pipefail

# Installs the debug APK on the connected device. The Coach proxy token is
# requested on every run and read without echo; empty input installs the app
# without AI (deterministic fallback). The token is never written to the repo.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

COACH_PROXY_URL="${COACH_PROXY_URL:-https://meucoach-api.hojeafestaenossa.site}"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

usage() {
    cat <<'EOF'
Uso: ./install.sh [--help]

Compila o APK debug e instala no aparelho conectado via adb.
Pergunta o token do Coach a cada execução:
  - cole o token e pressione Enter para instalar com IA;
  - pressione Enter vazio para instalar sem IA (fallback determinístico).

Variáveis opcionais:
  ADB              caminho do adb
  ANDROID_SERIAL   serial do aparelho quando há mais de um conectado
  COACH_PROXY_URL  URL do proxy (padrão: produção)
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

find_adb() {
    if [[ -n "${ADB:-}" ]]; then
        printf '%s\n' "$ADB"
        return 0
    fi
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi
    local sdk=""
    if [[ -f "$ROOT_DIR/local.properties" ]]; then
        sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" | head -n 1)"
    fi
    local candidate
    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$sdk" "$HOME/Android/Sdk"; do
        if [[ -n "$candidate" && -x "$candidate/platform-tools/adb" ]]; then
            printf '%s\n' "$candidate/platform-tools/adb"
            return 0
        fi
    done
    return 1
}

ADB_BIN="$(find_adb)" || {
    echo "adb não encontrado. Defina ADB ou instale o Android platform-tools." >&2
    exit 1
}

if ! "$ADB_BIN" get-state </dev/null >/dev/null 2>&1; then
    echo "Nenhum aparelho conectado e autorizado para depuração USB." >&2
    "$ADB_BIN" devices -l </dev/null >&2 || true
    exit 1
fi

MODEL="$("$ADB_BIN" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r')"
echo "Aparelho: ${MODEL:-desconhecido}"

printf 'Cole o token do Coach (Enter vazio = instalar sem IA): '
IFS= read -rsp '' COACH_PROXY_TOKEN
printf '\n'

GRADLE_CMD=(./gradlew :app:assembleDebug)
if [[ -n "$COACH_PROXY_TOKEN" ]]; then
    export ORG_GRADLE_PROJECT_coachProxyUrl="$COACH_PROXY_URL"
    export ORG_GRADLE_PROJECT_coachProxyToken="$COACH_PROXY_TOKEN"
    echo "Coach: IA habilitada via $COACH_PROXY_URL"
else
    unset ORG_GRADLE_PROJECT_coachProxyUrl ORG_GRADLE_PROJECT_coachProxyToken
    echo "Coach: sem token; a aba usará o fallback determinístico."
fi

if command -v mise >/dev/null 2>&1 && [[ -f "$ROOT_DIR/mise.toml" ]]; then
    mise exec -- "${GRADLE_CMD[@]}"
else
    "${GRADLE_CMD[@]}"
fi

if [[ ! -f "$APK" ]]; then
    echo "APK não encontrado em $APK" >&2
    exit 1
fi

"$ADB_BIN" install -r "$APK" </dev/null
echo "Instalado. Abra o MeuCoach no aparelho."
