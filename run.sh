#!/bin/bash
# Script de conveniência para rodar o MeuCoach com JDK 17.
export JAVA_HOME=/home/rafaellima/.local/share/mise/installs/java/17.0.2
export PATH="$JAVA_HOME/bin:$PATH"

CMD="${1:-check}"
shift || true

exec ./gradlew "$CMD" "$@"
