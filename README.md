# jloom

A CLI that generates and evolves production-ready Spring Boot **and Micronaut** backends from
a composable module catalog. Built on **Spring Boot 4.1** + **Spring Shell 4.0** with Java 25.

## Status

**v0.2 — Spring Shell migration complete.** All 7 commands (`new`, `add`, `list`, `info`,
`status`, `upgrade`, `config`) are wired declaratively via Spring Shell's
`@Command` / `@Option` / `@Argument` annotations. Generated projects compile + test green.
See `verification/dev-roadmap.md` for v0.2 → v1.0 work.

## Install

```bash
git clone <repo>
cd jloom
./gradlew installDist
export PATH="$PWD/build/install/jloom/bin:$PATH"
jloom list
```

## Usage

`jloom new` is the single entry point for creating a project. It generates immediately — no
extra flag needed to make it actually write anything — and prompts interactively for whatever
you didn't pass on the command line (run it with no flags at all in a real terminal to be
walked through it: project name, then a picker for a curated service type or a bare base
project). Pass `--dry-run` to preview instead of generating.

### Fully interactive

```bash
jloom new
```

### Scaffold a specialized service in one shot

```bash
jloom new --name notification-service --service notification-service --base-package com.acme.notify

cd notification-service
./gradlew test
```

### Compose from individual modules

```bash
jloom new --name my-service --base-package com.acme.myservice
cd my-service
jloom add postgres flyway --set postgres.db_name=notifs
jloom add problem-details
```

### Evolve an existing project

```bash
jloom add outbox-pattern                # apply a capability to a hand-edited project
jloom info --module kafka-consumer     # see what a module changes before applying
jloom list                              # what modules are available
jloom list --what services              # what service generators are available
jloom status                            # what modules are applied + upgradeable
jloom upgrade --module postgres         # pull a newer version of one module
jloom upgrade                           # pull every newer version
```

## Service catalog (`jloom new --service`)

| Service              | Frameworks                | Modules                                                                                                |
|----------------------|---------------------------|--------------------------------------------------------------------------------------------------------|
| notification-service | spring-boot, micronaut    | base / postgres / flyway / jwt-auth / otel-tracing / problem-details / notification-service (+ Micronaut variant) |
| user-service         | spring-boot, micronaut    | base / postgres / flyway / jwt-auth / otel-tracing / problem-details / user-service (+ Micronaut variant)     |
| identity-service     | spring-boot               | base / postgres / flyway / otel-tracing / problem-details / identity-service                              |
| micronaut-skeleton   | micronaut                 | micronaut-base                                                                                          |

## Module catalog (`jloom add`)

13 first-party modules covering: base skeletons (Spring Boot + Micronaut), relational DBs
(`postgres`), schema migrations (`flyway`, `testcontainers`), auth (`jwt-auth`,
`identity-service`), observability (`otel-tracing`), error handling (`problem-details`),
and three opinionated service generators (`notification-service`, `user-service`,
`identity-service`) with Spring Boot + Micronaut variants.

Run `jloom list` to see the full catalog with capability `provides` / `requires`.

## Archetypes

4 bundled archetypes — `postgres-service`, `postgres-flyway-service`,
`notification-stack`, `identity-with-user` — applied via `jloom new --name <name> --archetype <id>`.

## Quality guarantees

- **Correctness over coverage**: every declared archetype generates a project whose
  `./gradlew test` passes against the bundled module combination matrix.
- **Idempotency**: re-running `jloom add` on an already-applied module is a no-op.
- **No regeneration**: `jloom add` applies incremental OpenRewrite recipes against a project's
  own `./gradlew`, never re-scaffolds hand-edited code.
- **Tier-4 framework parity**: Spring Boot and Micronaut generators produce the same
  external contract (env vars consumed, port, health endpoint shape).

## Layout

```
src/main/java/com/jloom/
├── Main.java                       Spring Boot entry point (SpringApplication.run)
├── commands/                       One @Command class per concern (Spring Shell scans)
│   ├── NewCommands.java             new (single entry point) + add
│   ├── ReadCommands.java            list, info
│   ├── UpgradeCommands.java         status, upgrade
│   ├── ConfigCommand.java           config
│   └── InteractivePrompts.java      ComponentFlow-backed prompting shared by NewCommands
├── framework/                      FrameworkSupport sealed interface (Spring Boot / Micronaut)
├── registry/                       Module / Service / Archetype YAML parsing
├── compose/                        OpenRewrite recipe-fragment composition
├── exec/                           Gradle init-script generation + subprocess invocation
├── io/
│   ├── orchestrate/                 Shared apply pipeline (ModuleApplier)
│   ├── JloomExceptionMapper.java    ExitStatusExceptionMapper bean
│   └── JloomTheming.java            ThemeActive bean (color/NO_COLOR resolution)
├── scaffold/                       File-copy + token substitution
├── state/                          .jloom/state.json read/write
└── util/                           Shared token-substitution helper

src/main/resources/
├── modules/                        13 first-party modules (see Module catalog above)
├── archetypes/                     4 bundled archetypes
├── services.yml                    Service catalog (`jloom new --service` lookup table)
└── application.properties           Spring Shell config (interactive.enabled=false)
```

## References

- `cli-design-v2.md` — full architecture, research findings, MVP scope, design rationale
- `verification/dev-roadmap.md` — phase-by-phase implementation plan, current status, open questions

## Global environment

Once set in your shell profile (`~/.bashrc`, `~/.zshrc`, etc.), these env vars apply to every
jloom invocation in that terminal — no per-command flags needed. Run `jloom config` to see the
resolved values.

| Env var | Effect |
|---|---|
| `JLOOM_FORCE_COLOR=1` / `0` | Force ANSI colors on or off, overriding TTY detection. |
| `JLOOM_NO_COLOR=1` | Same as the standard `NO_COLOR=1` but jloom-specific. |
| `NO_COLOR=1` | Standard no-color convention (also respected). |

`JLOOM_FORCE_COLOR` wins over `JLOOM_NO_COLOR` (per the [no-color spec](https://no-color.org/)) —
explicit opt-in always wins over no-color defaults.