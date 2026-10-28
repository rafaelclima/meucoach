GRADLEW ?= ./gradlew

.PHONY: help build test check fmt lint install clean coverage

help:
	@echo "MeuCoach"
	@echo "  make build     Compila o APK debug"
	@echo "  make test      Roda os testes unitários"
	@echo "  make check     Formatação + testes + lint + cobertura"
	@echo "  make fmt       Aplica formatação (Spotless/ktlint)"
	@echo "  make lint      Roda Android Lint"
	@echo "  make install   Instala no dispositivo/emulador"
	@echo "  make coverage  Gera relatório HTML de cobertura"
	@echo "  make clean     Limpa builds"

build:
	$(GRADLEW) :app:assembleDebug

test:
	$(GRADLEW) test

check:
	$(GRADLEW) spotlessCheck check :core:domain:koverVerify

fmt:
	$(GRADLEW) spotlessApply

lint:
	$(GRADLEW) :app:lintDebug

install:
	$(GRADLEW) :app:installDebug

coverage:
	$(GRADLEW) :core:domain:koverHtmlReport

clean:
	$(GRADLEW) clean
