# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

For detailed information about each package, refer to the `CLAUDE.md` file located within that package.

## Application Description

This application is a backend server for an English pronunciation practice and feedback service designed for Korean learners.

## Core Domains

Domains are aligned with the controllers in `docs/API_SPEC_REFINED.md` §4. The same five-domain split is used in `docs/API_SPEC_REFINED.md` §3.

- **Member**
    - Standard sign-up / login (passwords stored with BCrypt hashing)
    - Username and email duplicate checks before sign-up
    - OAuth2 integration (currently a Google demo endpoint; provider expansion planned). When a provider-authenticated email already maps to an existing member, login methods are merged so the same account supports both standard login and OAuth2.
    - Profile read and nickname update for the authenticated user

- **Learning**
    - Preset learning catalog: tracks (collections of scripts) and scripts with multi-step learning flows (`INTRO` / `RECORD` steps)
    - Daily recommended scripts that refresh each day
    - Custom user sessions: user-supplied script text is split into sentences and managed via a CRUD API (favorite toggle, partial update)

- **Pronunciation Evaluation**
    - Recording upload: converts user audio to WAV, calls the model server, and returns per-step phoneme analysis (perceived vs canonical, peak softmax, error ops, wrong words)
    - Feedback aggregation: generates a composite feedback for one learning unit from accumulated recordings, supports weak-word retry, and finalizes rewards (exp / streak / badges) on completion
    - Feedback history: list and detail views of past feedback for the user
    - TTS: synthesizes English text to MP3 for reference-pronunciation playback

- **Statistics**
    - Per-user stats: cumulative streak (capped at 7), exp, monthly attendance calendar, weekly weak-phoneme top-N, badge inventory
    - Daily ranking: per-learning-unit leaderboard with the user's own rank and accuracy

- **System**
    - Public health check endpoint for boot verification (Spring Boot Actuator endpoints are also on the classpath but secured separately)

## Build & Test

Uses the Gradle wrapper. Java toolchain is **Java 25** (Gradle will auto-provision if missing).

- Build: `./gradlew build`
- Run app: `./gradlew bootRun`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests com.capstoneecho.echo_back.EchoBackApplicationTests`
- Single test method: `./gradlew test --tests 'com.capstoneecho.echo_back.EchoBackApplicationTests.contextLoads'`
- Generate REST Docs (runs tests first): `./gradlew asciidoctor`

## Architecture

Spring Boot 4.0.5 application (`com.capstoneecho.echo_back`). Entry point: `EchoBackApplication.java`.

Code is organized by **feature package**. Code that applies globally across the application (e.g., common exception handling, shared configuration, utilities, security configuration) must be placed under the `com.capstoneecho.echo_back.global` package.

Stack notes that affect how code should be written:
- **Spring Data JPA + MySQL** (`mysql-connector-j` runtime). DB connection is *not* yet configured in `src/main/resources/application.yaml` — only `spring.application.name` is set. Adding JPA-backed code will require populating datasource properties.
- **H2** is on the runtime classpath — intended for local/test use (e.g., in-memory DB for tests or running without a live MySQL). Profile-specific datasource config should select between H2 and MySQL.
- **Spring Security + OAuth2 client** is on the classpath. Any new HTTP endpoint will be subject to Spring Security's default chain unless explicit `SecurityFilterChain` config is added.
- **Spring Web MVC** is the HTTP layer (servlet stack, not WebFlux).
- **Bean Validation** (`spring-boot-starter-validation`) is available — use `@Valid` / `jakarta.validation` constraints on request DTOs and entities.
- **Spring Boot Actuator** is enabled — management endpoints are exposed and should be considered when configuring security.
- **Spring REST Docs (MockMvc)** is the documentation pipeline. The `test` task writes snippets to `build/generated-snippets`, and `asciidoctor` consumes them — write controller tests with REST Docs in mind so generated docs stay current.
- **Lombok** is used; ensure annotation processing is enabled in your IDE.
- **DevTools** is included for local hot reload via `bootRun`.

## Development Rules

- Commit in units of functionality, following the commit convention (@agent/COMMIT_CONVENTIONS.md)
- After writing code, always verify correct behavior and absence of regressions through unit tests
- If anything is unclear during development, always ask before proceeding
- After completing a task, provide a summary of the work done and a list of changes made