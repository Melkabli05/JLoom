# jloom

A CLI that generates and evolves production-ready backends from a composable module catalog.
Choose a service template (file storage, notifications, users, identity) or compose your own
from individual capabilities (database, auth, observability, etc.).

## Install

```bash
git clone https://github.com/Melkabli05/JLoom.git
cd JLoom
./gradlew installDist
export PATH="$PWD/build/install/jloom/bin:$PATH"
jloom list
```

Requires JDK 25 or newer.

## Usage

### Create a new project

Run `jloom new` with no flags and it walks you through prompts. Or pass everything on the
command line for a one-shot scaffold:

```bash
jloom new --name notification-service --service notification-service --base-package com.acme.notify
cd notification-service
./gradlew test
```

Pass `--dry-run` to preview without writing anything.

### Compose from individual capabilities

```bash
jloom new --name my-service --base-package com.acme.myservice
cd my-service
jloom add postgres flyway --set postgres.db_name=notifs
jloom add problem-details
```

### Evolve an existing project

```bash
jloom list                              # what modules are available
jloom info --module kafka-consumer      # see what a module changes before applying
jloom add outbox-pattern                # apply a capability to a hand-edited project
jloom status                            # what modules are applied + upgradeable
jloom upgrade --module postgres         # pull a newer version of one module
jloom upgrade                           # pull every newer version
```

### Explore

```bash
jloom list                              # all available modules
jloom list --what services              # service templates
jloom list --what modules               # capability modules
jloom list --what archetypes            # project archetypes
jloom info --module <id>                # what a module does
jloom config                            # resolved theme + log level
```

## Service templates

| Service              | What it scaffolds                                           |
|----------------------|-------------------------------------------------------------|
| `notification-service` | Email delivery (SMTP) with async, retry, and templates     |
| `user-service`         | User CRUD with email verification and password hashing    |
| `identity-service`     | JWT issuer with JWKS endpoint                              |
| `file-service`         | Object storage (local FS or S3-compatible) with pre-signed uploads |
| `micronaut-skeleton`   | Bare Micronaut 4 application skeleton                       |

## Help

Run any command with `--help` for the full flag list, or `jloom` alone for the top-level
summary.