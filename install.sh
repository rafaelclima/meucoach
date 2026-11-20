#!/usr/bin/env bash
set -euo pipefail

# Installs the debug APK on the connected device.
#
# The Coach proxy token is requested on every run and read without echo; empty
# input installs the app without AI (deterministic fallback). The token is never
# written to the repo.
#
# Two Linux dev machines: use the SAME debug keystore on both so `adb install -r`
# preserves the app data. `--export-key`/`--import-key` copy the keystore that
# Gradle actually uses on each machine (the path can differ by XDG/ANDROID_USER_HOME).
# If signatures still differ, the script can back up the app data via `run-as`
# (debug builds only), uninstall, install and restore.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

PKG="com.meucoach.app"
COACH_PROXY_URL="${COACH_PROXY_URL:-https://meucoach-api.hojeafestaenossa.site}"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

MODE="install"
BACKUP_FILE=""
RESTORE_FILE=""
KEY_FILE=""
YES=0
JDK_DESC=""
GRADLE_PREFIX=()
KEYTOOL_CMD=()

usage() {
    cat <<'EOF'
Uso: ./install.sh [opções]

Compila o APK debug e instala no aparelho conectado via adb.
Pergunta o token do Coach a cada execução:
  - cole o token e pressione Enter para instalar com IA;
  - pressione Enter vazio para instalar sem IA (fallback determinístico).

Para preservar os dados entre as duas máquinas, use o mesmo debug keystore
(`--export-key` na máquina que fez a última instalação e `--import-key` na outra).

Opções:
  --export-key ARQUIVO  Exporta o debug keystore ativo para ARQUIVO (fora do repo).
  --import-key ARQUIVO  Importa o debug keystore de ARQUIVO (preserva o atual em .bak).
  --keystore-info       Mostra caminho e SHA-256 do debug keystore ativo.
  --backup [ARQUIVO]    Faz backup dos dados do app (databases/shared_prefs/files)
                        para ARQUIVO (padrão: ~/meucoach-backup-<timestamp>.tgz)
                        e sai.
  --restore ARQUIVO     Restaura os dados do app a partir de ARQUIVO e sai.
  --backup-file ARQUIVO Define o arquivo de backup usado no fluxo de instalação.
  -y, --yes             Assume "sim" nas confirmações (inclusive desinstalação).
  --help, -h            Mostra esta ajuda.

Variáveis opcionais:
  ADB              caminho do adb
  ANDROID_SERIAL   serial do aparelho quando há mais de um conectado
  COACH_PROXY_URL  URL do proxy (padrão: produção)
  BACKUP_FILE      arquivo de backup padrão no fluxo de instalação
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --help|-h) usage; exit 0 ;;
        --export-key)
            MODE="export-key"; shift
            if [[ $# -lt 1 ]]; then
                echo "Informe o arquivo de destino: --export-key ARQUIVO" >&2
                exit 1
            fi
            KEY_FILE="$1"; shift
            ;;
        --import-key)
            MODE="import-key"; shift
            if [[ $# -lt 1 ]]; then
                echo "Informe o arquivo de origem: --import-key ARQUIVO" >&2
                exit 1
            fi
            KEY_FILE="$1"; shift
            ;;
        --keystore-info) MODE="keystore-info"; shift ;;
        --backup)
            MODE="backup"; shift
            if [[ $# -gt 0 && ! "$1" =~ ^-- ]]; then BACKUP_FILE="$1"; shift; fi
            ;;
        --backup-file)
            shift
            if [[ $# -lt 1 ]]; then
                echo "Informe o arquivo de backup: --backup-file ARQUIVO" >&2
                exit 1
            fi
            BACKUP_FILE="$1"; shift
            ;;
        --restore)
            MODE="restore"; shift
            if [[ $# -lt 1 ]]; then
                echo "Informe o arquivo de backup: --restore ARQUIVO" >&2
                exit 1
            fi
            RESTORE_FILE="$1"; shift
            ;;
        -y|--yes) YES=1; shift ;;
        *) echo "Argumento desconhecido: $1" >&2; usage; exit 1 ;;
    esac
done

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
    for candidate in \
        "${ANDROID_HOME:-}" \
        "${ANDROID_SDK_ROOT:-}" \
        "$sdk" \
        "$HOME/Android/Sdk" \
        "$HOME/android-sdk" \
        "/opt/android-sdk" \
        "$HOME/Library/Android/sdk"; do
        if [[ -n "$candidate" && -x "$candidate/platform-tools/adb" ]]; then
            printf '%s\n' "$candidate/platform-tools/adb"
            return 0
        fi
    done
    return 1
}

is_installed() {
    "$ADB_BIN" shell pm list packages 2>/dev/null | grep -qx "package:$PKG"
}

is_debuggable() {
    "$ADB_BIN" shell run-as "$PKG" true </dev/null >/dev/null 2>&1
}

default_backup_path() {
    printf '%s/meucoach-backup-%s.tgz\n' "$HOME" "$(date +%Y%m%d-%H%M%S)"
}

confirm() {
    if [[ "$YES" == "1" ]]; then
        return 0
    fi
    local answer
    printf '%s [y/N] ' "$1"
    IFS= read -r answer
    [[ "$answer" =~ ^[Yy]$ ]]
}

refuse_inside_repo() {
    local path="$1" resolved
    resolved="$(realpath -m "$path")"
    case "$resolved" in
        "$ROOT_DIR"|"$ROOT_DIR"/*)
            echo "Escolha um caminho fora do repositório: $path" >&2
            return 1
            ;;
    esac
}

do_backup() {
    local dest="$1" tmp
    if ! refuse_inside_repo "$dest"; then
        return 1
    fi
    if ! is_installed; then
        echo "App não instalado; nada a backupar." >&2
        return 1
    fi
    if ! is_debuggable; then
        echo "App instalado não é debugável (run-as falhou); backup impossível." >&2
        return 1
    fi
    mkdir -p "$(dirname "$dest")"
    tmp="$(mktemp "${dest}.part.XXXXXX")"
    "$ADB_BIN" shell am force-stop "$PKG" </dev/null >/dev/null 2>&1 || true
    if ! "$ADB_BIN" exec-out run-as "$PKG" tar -czf - databases shared_prefs files > "$tmp"; then
        rm -f "$tmp"
        echo "Falha ao gerar backup." >&2
        return 1
    fi
    if [[ ! -s "$tmp" ]] || ! tar -tzf "$tmp" >/dev/null 2>&1; then
        rm -f "$tmp"
        echo "Backup inválido ou vazio." >&2
        return 1
    fi
    mv -f "$tmp" "$dest"
    echo "Backup salvo em: $dest"
}

do_restore() {
    local src="$1"
    if [[ ! -f "$src" ]]; then
        echo "Arquivo de backup não encontrado: $src" >&2
        return 1
    fi
    if ! is_installed; then
        echo "App não instalado; instale antes de restaurar." >&2
        return 1
    fi
    if ! is_debuggable; then
        echo "App instalado não é debugável; restore impossível." >&2
        return 1
    fi
    if ! "$ADB_BIN" push "$src" /data/local/tmp/meucoach-restore.tgz </dev/null >/dev/null; then
        echo "Falha ao enviar o backup para o aparelho." >&2
        return 1
    fi
    "$ADB_BIN" shell am force-stop "$PKG" </dev/null >/dev/null 2>&1 || true
    "$ADB_BIN" shell run-as "$PKG" rm -r -f databases shared_prefs files </dev/null >/dev/null 2>&1 || true
    if ! "$ADB_BIN" shell run-as "$PKG" tar -xzf /data/local/tmp/meucoach-restore.tgz </dev/null; then
        "$ADB_BIN" shell rm -f /data/local/tmp/meucoach-restore.tgz </dev/null 2>&1 || true
        echo "Falha ao extrair backup." >&2
        return 1
    fi
    "$ADB_BIN" shell rm -f /data/local/tmp/meucoach-restore.tgz </dev/null 2>&1 || true
    "$ADB_BIN" shell am force-stop "$PKG" </dev/null >/dev/null 2>&1 || true
    echo "Dados restaurados a partir de: $src"
}

run_gradle() {
    if [[ ${#GRADLE_PREFIX[@]} -gt 0 ]]; then
        "${GRADLE_PREFIX[@]}" ./gradlew "$@"
    else
        ./gradlew "$@"
    fi
}

run_keytool() {
    "${KEYTOOL_CMD[@]}" "$@"
}

resolve_jdk() {
    GRADLE_PREFIX=()
    KEYTOOL_CMD=()

    if command -v mise >/dev/null 2>&1 && [[ -f "$ROOT_DIR/mise.toml" ]]; then
        local mise_version
        if mise_version="$(mise exec -- java -version 2>&1)"; then
            GRADLE_PREFIX=(mise exec --)
            KEYTOOL_CMD=(mise exec -- keytool)
            JDK_DESC="$(printf '%s\n' "$mise_version" | head -n 1) (mise)"
            return 0
        fi
        echo "Aviso: mise não conseguiu preparar o JDK; tentando JAVA_HOME/PATH." >&2
    fi

    local java_bin=""
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        java_bin="$JAVA_HOME/bin/java"
    elif command -v java >/dev/null 2>&1; then
        java_bin="$(command -v java)"
    fi
    if [[ -z "$java_bin" ]]; then
        echo "JDK 17 não encontrado. Instale o JDK 17 ou use mise." >&2
        return 1
    fi

    local version major keytool_bin
    version="$("$java_bin" -version 2>&1 | head -n 1)"
    major="$(printf '%s\n' "$version" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')"
    if [[ -z "$major" ]] || [[ "$major" != "17" ]]; then
        echo "JDK 17 necessário; encontrado: $version" >&2
        return 1
    fi
    JDK_DESC="$version"

    keytool_bin="$(dirname "$java_bin")/keytool"
    if [[ -x "$keytool_bin" ]]; then
        KEYTOOL_CMD=("$keytool_bin")
    else
        KEYTOOL_CMD=(keytool)
    fi
}

debug_keystore_path() {
    local output path
    if ! output="$(run_gradle -q :app:signingReport 2>&1)"; then
        printf '%s\n' "$output" >&2
        echo "Falha ao consultar o debug keystore (./gradlew :app:signingReport)." >&2
        return 1
    fi
    path="$(
        printf '%s\n' "$output" |
            awk '/^Variant: debug$/{found=1} found && /^Store: /{sub(/^Store: /, ""); print; exit}'
    )"
    if [[ -z "$path" ]]; then
        echo "Não foi possível localizar o Store do variant debug no signingReport." >&2
        return 1
    fi
    printf '%s\n' "$path"
}

ensure_debug_keystore() {
    local path="$1"
    if [[ -f "$path" ]]; then
        if ! run_keytool -list -keystore "$path" -storepass android -alias AndroidDebugKey >/dev/null 2>&1; then
            echo "debug.keystore inválido (senha 'android'/alias 'AndroidDebugKey'): $path" >&2
            return 1
        fi
        return 0
    fi
    echo "Gerando debug.keystore em: $path"
    mkdir -p "$(dirname "$path")"
    if ! run_keytool -genkeypair \
        -keystore "$path" \
        -storepass android -keypass android \
        -alias AndroidDebugKey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1; then
        echo "Falha ao gerar o debug.keystore." >&2
        return 1
    fi
    chmod 600 "$path"
}

keystore_fingerprint() {
    run_keytool -exportcert -keystore "$1" -storepass android -alias AndroidDebugKey 2>/dev/null |
        sha256sum |
        awk '{print toupper($1)}' |
        sed 's/../&:/g; s/:$//'
}

do_export_key() {
    local dest="$1" store fingerprint
    if [[ -z "$dest" ]]; then
        echo "Informe o arquivo de destino: --export-key ARQUIVO" >&2
        return 1
    fi
    if ! refuse_inside_repo "$dest"; then
        echo "O keystore nunca deve ser commitado." >&2
        return 1
    fi
    if [[ -e "$dest" ]] && ! confirm "Sobrescrever $dest?"; then
        echo "Abortado." >&2
        return 1
    fi
    store="$(debug_keystore_path)" || return 1
    ensure_debug_keystore "$store" || return 1
    mkdir -p "$(dirname "$dest")"
    cp -f "$store" "$dest"
    chmod 600 "$dest"
    if ! fingerprint="$(keystore_fingerprint "$dest")"; then
        echo "Falha ao ler o keystore exportado." >&2
        return 1
    fi
    echo "Debug keystore ativo: $store"
    echo "Exportado para:       $dest"
    echo "SHA-256:              $fingerprint"
    echo
    echo "Leve o arquivo para a outra máquina por scp/pendrive e rode lá:"
    printf '  ./install.sh --import-key %q\n' "$dest"
    echo "Não commite o keystore."
}

do_import_key() {
    local src="$1" store backup fingerprint
    if [[ -z "$src" ]]; then
        echo "Informe o arquivo de origem: --import-key ARQUIVO" >&2
        return 1
    fi
    if [[ ! -f "$src" ]]; then
        echo "Arquivo de keystore não encontrado: $src" >&2
        return 1
    fi
    if ! run_keytool -list -keystore "$src" -storepass android -alias AndroidDebugKey >/dev/null 2>&1; then
        echo "Keystore inválido: $src" >&2
        echo "Esperado o debug keystore padrão (alias AndroidDebugKey, senha 'android')." >&2
        return 1
    fi
    store="$(debug_keystore_path)" || return 1
    if [[ "$(realpath -m "$src")" == "$(realpath -m "$store")" ]]; then
        echo "O keystore informado já é o ativo: $store"
        return 0
    fi
    mkdir -p "$(dirname "$store")"
    if [[ -f "$store" ]]; then
        backup="$store.bak-$(date +%Y%m%d-%H%M%S)"
        cp -p "$store" "$backup"
        chmod 600 "$backup"
        echo "Keystore anterior preservado em: $backup"
    fi
    cp -f "$src" "$store"
    chmod 600 "$store"
    rm -f "$APK"
    if ! fingerprint="$(keystore_fingerprint "$store")"; then
        echo "Falha ao ler o keystore importado." >&2
        return 1
    fi
    echo "Debug keystore importado para: $store"
    echo "SHA-256:                       $fingerprint"
    echo "APK anterior removido; o próximo ./install.sh assina com esta chave."
    echo "Se o app instalado usa outra assinatura, a instalação fará backup+desinstalação+restauração."
}

do_keystore_info() {
    local store fingerprint
    store="$(debug_keystore_path)" || return 1
    if [[ ! -f "$store" ]]; then
        echo "Debug keystore: $store"
        echo "Ainda não existe; será criado no primeiro build (ou por --export-key)."
        return 0
    fi
    if ! fingerprint="$(keystore_fingerprint "$store")"; then
        echo "Não foi possível ler o debug.keystore: $store" >&2
        return 1
    fi
    echo "Debug keystore: $store"
    echo "SHA-256:        $fingerprint"
}

# Comandos de keystore não precisam de aparelho conectado.
case "$MODE" in
    export-key)
        resolve_jdk || exit 1
        do_export_key "$KEY_FILE"
        exit $?
        ;;
    import-key)
        resolve_jdk || exit 1
        do_import_key "$KEY_FILE"
        exit $?
        ;;
    keystore-info)
        resolve_jdk || exit 1
        do_keystore_info
        exit $?
        ;;
esac

ADB_BIN="$(find_adb)" || {
    echo "adb não encontrado. Defina ADB ou instale o Android platform-tools." >&2
    exit 1
}

if ! "$ADB_BIN" get-state </dev/null >/dev/null 2>&1; then
    DEVICES="$("$ADB_BIN" devices </dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')"
    if [[ -n "$DEVICES" ]] && [[ "$(printf '%s\n' "$DEVICES" | wc -l)" -gt 1 ]]; then
        echo "Mais de um aparelho conectado. Escolha um com ANDROID_SERIAL=<serial>:" >&2
        printf '  %s\n' $DEVICES >&2
    else
        echo "Nenhum aparelho conectado e autorizado para depuração USB." >&2
    fi
    "$ADB_BIN" devices -l </dev/null >&2 || true
    exit 1
fi

MODEL="$("$ADB_BIN" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r')"
echo "Aparelho: ${MODEL:-desconhecido}"

# Modos manuais de dados
if [[ "$MODE" == "backup" ]]; then
    [[ -z "$BACKUP_FILE" ]] && BACKUP_FILE="$(default_backup_path)"
    do_backup "$BACKUP_FILE"
    exit $?
fi

if [[ "$MODE" == "restore" ]]; then
    if ! confirm "Restaurar $RESTORE_FILE e substituir os dados atuais do app?"; then
        echo "Abortado." >&2
        exit 1
    fi
    do_restore "$RESTORE_FILE"
    exit $?
fi

# Fluxo de instalação
printf 'Cole o token do Coach (Enter vazio = instalar sem IA): '
IFS= read -rsp '' COACH_PROXY_TOKEN
printf '\n'

if [[ -n "$COACH_PROXY_TOKEN" ]]; then
    export ORG_GRADLE_PROJECT_coachProxyUrl="$COACH_PROXY_URL"
    export ORG_GRADLE_PROJECT_coachProxyToken="$COACH_PROXY_TOKEN"
    echo "Coach: IA habilitada via $COACH_PROXY_URL"
else
    unset ORG_GRADLE_PROJECT_coachProxyUrl ORG_GRADLE_PROJECT_coachProxyToken
    echo "Coach: sem token; a aba usará o fallback determinístico."
fi

resolve_jdk || exit 1
echo "JDK: $JDK_DESC"

echo "Compilando APK debug..."
run_gradle :app:assembleDebug

if [[ ! -f "$APK" ]]; then
    echo "APK não encontrado em $APK" >&2
    exit 1
fi

# Tentativa normal (preserva dados quando a assinatura é a mesma).
INSTALL_LOG="$(mktemp)"
trap 'rm -f "$INSTALL_LOG"' EXIT
if "$ADB_BIN" install -r "$APK" </dev/null >"$INSTALL_LOG" 2>&1; then
    cat "$INSTALL_LOG"
    echo "Instalado. Abra o MeuCoach no aparelho."
    exit 0
fi
cat "$INSTALL_LOG"

if ! grep -Eqi 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|inconsistent certificates' "$INSTALL_LOG"; then
    echo "Instalação falhou (veja acima)." >&2
    exit 1
fi

echo
echo "Assinatura incompatível com a versão instalada."
echo "Para preservar os dados, use o MESMO debug keystore nas duas máquinas:"
echo "  1. Na máquina que fez a última instalação: ./install.sh --export-key ~/meucoach-debug.keystore"
echo "  2. Nesta máquina:                          ./install.sh --import-key ~/meucoach-debug.keystore"
echo
if ! confirm "Continuar mesmo assim (backup, desinstalação e restauração)?"; then
    echo "Abortado; nada foi alterado no aparelho." >&2
    exit 1
fi

BACKUP_PATH="${BACKUP_FILE:-}"
if [[ -z "$BACKUP_PATH" ]]; then
    BACKUP_PATH="$(default_backup_path)"
fi

if is_installed; then
    if is_debuggable; then
        echo "Fazendo backup dos dados do app..."
        if ! do_backup "$BACKUP_PATH"; then
            echo "Backup falhou; abortando antes de desinstalar para não perder dados." >&2
            exit 1
        fi
    else
        echo "App instalado não é debugável; backup automático impossível." >&2
        if ! confirm "Continuar e perder os dados do app?"; then
            echo "Abortado." >&2
            exit 1
        fi
    fi

    echo "Desinstalando versão anterior..."
    if ! "$ADB_BIN" uninstall "$PKG" </dev/null; then
        echo "Falha ao desinstalar a versão anterior." >&2
        exit 1
    fi
fi

echo "Instalando novo APK..."
if ! "$ADB_BIN" install "$APK" </dev/null; then
    echo "Falha ao instalar o novo APK." >&2
    if [[ -f "$BACKUP_PATH" ]]; then
        echo "Os dados estão no backup: $BACKUP_PATH" >&2
    fi
    exit 1
fi

if [[ -f "$BACKUP_PATH" ]]; then
    echo "Restaurando dados..."
    if ! do_restore "$BACKUP_PATH"; then
        echo "Restore falhou; o backup está em: $BACKUP_PATH" >&2
    fi
else
    echo "Sem backup para restaurar."
fi

echo "Instalado. Abra o MeuCoach no aparelho."
if [[ -f "$BACKUP_PATH" ]]; then
    echo "Backup mantido em: $BACKUP_PATH"
fi
