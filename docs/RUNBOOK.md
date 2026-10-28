# RUNBOOK.md — MeuCoach

**Versão:** 1.0 · **Data:** 27/10/2026

## 1. Pré-requisitos

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | 17+ | AGP 9.4 exige ≥ 17; Gradle 9.8 roda em 17–27 |
| Android SDK Platform | **37** (`android-37.0`) | `core-ktx 1.19.1` exige compileSdk 37 |
| Build Tools | **37.0.0** | |
| Android Studio | versão atual | opcional; o build é CLI-first |
| Dispositivo/emulador | API 26+ | Health Connect completo em Android 14+ |
| Docker | 24+ | apenas Fase 2 (backend) |

### 1.1 JDK

O repositório não fixa o caminho do JDK. Use um JDK 17+ no `JAVA_HOME`:

```bash
export JAVA_HOME="$HOME/.local/share/mise/installs/java/17.0.2"
java -version
```

Android Studio: `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK = 17`.

### 1.2 Android SDK

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

sdkmanager --install "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
```

Crie `local.properties` na raiz (ignorado pelo git):

```properties
sdk.dir=/home/SEU_USUARIO/Android/Sdk
```

Há um modelo em `local.properties.example`.

## 2. Primeiro clone

```bash
git clone https://github.com/rafaelclima/meucoach.git
cd meucoach
cp local.properties.example local.properties
# edite sdk.dir

./gradlew :app:assembleDebug
./gradlew check
```

O primeiro build baixa Gradle 9.8.2 e dependências; pode levar alguns minutos.

## 3. Comandos do dia a dia

```bash
./gradlew :app:installDebug          # instala no device/emulador conectado
./gradlew :app:assembleDebug         # APK debug
./gradlew test                       # testes unitários
./gradlew check                      # testes + lint + verificações
./gradlew spotlessApply              # formata Kotlin/Gradle
./gradlew spotlessCheck              # valida formatação
./gradlew koverHtmlReport            # relatório de cobertura
./gradlew koverVerify                # gate de cobertura
./gradlew clean                      # limpa builds
```

Atalhos com `make`:

```bash
make build
make test
make check
make fmt
make install
```

## 4. Emulador

```bash
sdkmanager --install "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n meucoach_api35 -k "system-images;android-35;google_apis;x86_64"
emulator -avd meucoach_api35
```

- Health Connect: em Android 14+ já vem no sistema. Em API 26–33, instale o app **Health Connect** da Play Store e conceda permissões.
- Para testar sono/HRV no emulador, use o app Health Connect ou um fake no código (Fase 1).

### 4.1 Dispositivo físico (usuário 0)

1. Ative opções de desenvolvedor e depuração USB.
2. Conecte e autorize o computador.
3. `adb devices` deve listar o aparelho.
4. `./gradlew :app:installDebug`.
5. No app, conceda permissões do Health Connect.
6. Sincronize o Zepp → Health Connect antes de abrir o MeuCoach.

## 5. Dados privados e fixtures

- **Nunca** commite arquivos de saúde reais.
- Exportações do Zepp: `~/Documents/Rafa/Zepp/` (fora do repo).
- Os 3 TCX antigos ficam em `~/Documents/Rafa/` (fora do repo) e serão usados no P0-06.
- Cópia privada para testes locais: `fixtures/private/` (ignorado).
- Fixtures commitadas: sanitizadas ou sintéticas.
- Antes de commitar, rode:

```bash
git status --short
git diff --stat
# procure por .fit/.tcx/.gpx/.csv e nomes pessoais
```

Script de sanitização (Fase 0): `tools/sanitize_fit.py` — invalida posições, limpa serial/IDs e `source`, recalcula CRC e valida que o arquivo abre com `fitdecode`.

## 6. Health Connect — checklist manual

- [ ] App Health Connect instalado/atualizado (Android < 14).
- [ ] Permissões: Sleep, Resting heart rate, HRV, SpO2, Exercise, Steps.
- [ ] Zepp autorizado a escrever no Health Connect.
- [ ] Sono aparece no Health Connect (testar com o app oficial).
- [ ] Abrir MeuCoach → Diagnóstico → ver último registro por tipo.
- [ ] Se um tipo não sincronizar, o app mostra "sem dados" e não bloqueia.

## 7. Release (Fase 2)

1. Criar keystore fora do repo:

```bash
keytool -genkeypair -v -keystore ~/.android/meucoach-release.jks \
  -alias meucoach -keyalg RSA -keysize 4096 -validity 10000
```

2. Configurar `~/.gradle/gradle.properties` (nunca no repo):

```properties
MEUCOACH_STORE_FILE=/home/SEU_USUARIO/.android/meucoach-release.jks
MEUCOACH_STORE_PASSWORD=...
MEUCOACH_KEY_ALIAS=meucoach
MEUCOACH_KEY_PASSWORD=...
```

3. `./gradlew :app:assembleRelease` (configuração de signing entra na Fase 2).
4. Publicar APK em GitHub Releases; instalar via sideload.
5. Guardar keystore e senhas em local seguro (não no git).

## 8. Backend local (Fase 2)

```bash
cd infra
cp .env.example .env
docker compose up -d
docker compose logs -f api
```

Serviços previstos:

| Serviço | Porta | Uso |
|---|---|---|
| `api` | 8080 | Ktor |
| `postgres` | 5432 | Banco |
| `minio` | 9000/9001 | Arquivos FIT/TCX/GPX |
| `traefik` | 80/443 | Proxy local (opcional) |

Migrações: `./gradlew :backend:flywayMigrate` ou entrypoint do container.

## 9. Deploy no Dokploy (Fase 2)

- Servidor: IONOS VPS (EUA), Docker + Dokploy.
- Domínio de exemplo: `dokploy.hojeafestaenossa.site`.
- App: `api.meucoach.<dominio>` (definir).
- Passos:
  1. Criar projeto no Dokploy.
  2. Conectar repositório GitHub.
  3. Configurar variáveis: `DATABASE_URL`, `JWT_SECRET`, `S3_*`, `SENTRY_DSN` (opcional).
  4. Build: Dockerfile multi-stage (JDK 17 → JRE 17).
  5. Healthcheck: `GET /health` → 200.
  6. TLS automático via Traefik/Let's Encrypt.
  7. Backup diário do Postgres para storage externo.
  8. Monitorar disco e logs.
- **Nunca** subir o backend sem TLS e sem backup.

## 10. Troubleshooting

| Sintoma | Causa provável | Ação |
|---|---|---|
| `SDK location not found` | `local.properties` ausente | criar com `sdk.dir` |
| `compileSdk 37 required` | platform 37 não instalada | `sdkmanager "platforms;android-37.0"` |
| `Unsupported class file major version` | JDK incompatível | usar JDK 17 |
| Build lento/daemon travado | cache | `./gradlew --stop && ./gradlew clean` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | assinatura/versão | desinstalar app antigo |
| Health Connect sem dados | Zepp não sincronizou | abrir Zepp → sincronizar → Health Connect |
| FIT não importa | arquivo corrompido/CRC | validar com `fitdecode`; ver detalhe do erro |
| Cobertura abaixo de 85% | branches sem teste | `koverHtmlReport` e cobrir casos |
| Screenshot test falha no CI | ambiente gráfico | fixar Robolectric/qualifiers; revisar baseline |

## 11. Backup e restauração

- O banco de saúde **não** entra no backup automático do Android (`allowBackup=false`).
- Backup explícito: exportar JSON + arquivos originais.
- Restauração: importar o ZIP exportado (Fase 1).
- Antes de atualizar o app: exportar se houver mudança de schema relevante.

## 12. Comandos úteis

```bash
adb logcat -s MeuCoach
adb shell dumpsys package com.meucoach.app | grep version
./gradlew :core:domain:test --tests "*TrainingLoad*"
./gradlew :app:lintDebug
./gradlew --scan
```

## 13. Segurança operacional

- Não colar chaves, tokens ou dados de saúde em issues/PRs.
- Não usar dados reais em screenshots.
- Não desativar TLS ou backup para "resolver rápido".
- Revisar dependências novas antes de adicionar.
- Rodar `./gradlew check` antes de qualquer commit.
