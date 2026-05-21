# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Cel projektu

Sandbox / projekt eksperymentalny do testowania biblioteki **Koog Agents** (`ai.koog:koog-agents`) — frameworka do budowania AI agentów w Kotlinie. Projekt nie należy do produkcyjnego ekosystemu mikrousług ALA (org-wide guidance w `~/IdeaProjects/CLAUDE.md` nie ma tu zastosowania — to jest stand-alone playground, bez Spring Boot, bez Alice Framework, bez Artifactory dependencies).

## Stack

- **Kotlin** 2.2.0 na JVM toolchain 21
- **Gradle** (Kotlin DSL, single-module)
- **application plugin** — entrypoint `MainKt` (`src/main/kotlin/Main.kt`)
- **ktlint** (`org.jlleitschuh.gradle.ktlint` 14.0.1)
- **Repozytorium artefaktów**: `mavenCentral()` only — `ai.koog:koog-agents` jest publicznie dostępny, nie wymaga Artifactory credentials
- **Testy**: `kotlin("test")` z JUnit Platform (`useJUnitPlatform()`)

## Komendy

```bash
# Format
./gradlew ktlintFormat

# Build (zawsze z clean — konwencja użytkownika)
./gradlew clean build

# Uruchomienie aplikacji (main = MainKt)
./gradlew run

# Testy
./gradlew test

# Pojedyncza klasa testowa (po dodaniu pierwszych testów)
./gradlew test --tests "FullyQualifiedTestClassName"
```

## Struktura

Single-module, brak multi-modułowego layoutu znanego z `backend-*`. Source layout:

```
src/
└── main/kotlin/Main.kt   # entrypoint (MainKt)
```

Brak `src/test/` na ten moment — przy dodawaniu testów użyj standardowego `src/test/kotlin/`.

## Koog API reference

**Lokalne źródła Koog 0.8.0** są wypakowane w `.koog-api/sources/` (47 modułów,
~600 plików `.kt`). To **jedyne** miejsce do szukania API Koog — **NIE** ekstrahuj
ponownie jarów z `~/.gradle/caches/...` ani nie wypakowuj do `/tmp`.

```bash
# Szukanie API:
grep -rln "Structured" .koog-api/sources/
grep -rln "fun subgraph" .koog-api/sources/

# Czytanie konkretnego pliku:
cat .koog-api/sources/agents-core-jvm/commonMain/ai/koog/agents/core/dsl/builder/AIAgentSubgraphBuilder.kt
```

Po bumpie wersji Koog w `build.gradle.kts` → uruchom `.koog-api/update.sh <new-version>`
(skrypt wycina starą `sources/` i wypakowuje nowe jary z gradle cache).

`.koog-api/` nie jest commitowane — to lokalne tooling, regenerowalne w sekundę
ze gradle cache.

## Notes for Development

- Aktualna wersja Koog w projekcie: **0.8.0** (zmień w `build.gradle.kts` + odpal `.koog-api/update.sh`).
- Biblioteka Koog definiuje DSL do budowania agentów, narzędzi (tools), promptów, strategii grafowych z subgrafami i structured output.
- Brak konfiguracji deployment / CI/CD — to playground.
- Brak integracji z Alice Framework, Spring AI ALA, ani innymi serwisami z `IdeaProjects/`.