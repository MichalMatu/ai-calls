# Repository layout

This document is the repository structure contract for `MichalMatu/ai-calls`.

The goal is not to freeze the project forever. The goal is to make structural changes deliberate: new code should have an obvious owner and location, and a new top-level path should be introduced only when an existing area genuinely cannot own it.

## Current tree

```text
ai-calls/
├── .github/                  # GitHub Actions and repository automation
├── app/                      # primary Android application
├── audio-bridge/             # Android audio bridge module
├── privileged-helper/        # privileged/Shizuku boundary module
├── realtime-client/          # realtime client Android module
├── service-packs/            # declarative service/task-specific data
├── scripts/                  # developer tooling and host-side automation
│   ├── aicall_tools/         # importable Python tooling
│   │   ├── benchmarks/       # benchmark runners/helpers
│   │   ├── calls/            # call orchestration and live-call tooling
│   │   ├── device/           # device/S22 inspection and control helpers
│   │   ├── orange/           # Orange-specific historical/evidence tooling
│   │   ├── realtime/         # realtime API/lab tooling
│   │   ├── relay/            # supervisor relay protocol helpers
│   │   └── text/             # text-model tooling
│   ├── tests/                # host Python tests grouped by matching domain
│   │   ├── benchmarks/
│   │   ├── calls/
│   │   ├── device/
│   │   ├── orange/
│   │   ├── realtime/
│   │   └── text/
│   ├── bin/                  # stable developer command entrypoints
│   ├── local_agent/          # small Local Agent maintenance wrappers
│   ├── README.md             # developer-tooling layout notes
│   └── verify_host.sh        # canonical host quality gate
├── benchmarks/               # versioned benchmark inputs/results/evidence
├── docs/                     # architecture, runbooks, handoffs and contracts
├── gradle/                   # Gradle wrapper support files
├── build.gradle.kts          # root Gradle build definition
├── settings.gradle.kts       # Android module registry
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md                 # product overview and source-of-truth index
└── AGENTS.md                 # repository execution/workflow contract
```

The Android module list is authoritative in `settings.gradle.kts` and currently consists of `app`, `audio-bridge`, `privileged-helper`, and `realtime-client`.

## Placement rules

Use these defaults before creating a new path:

| Change | Preferred location |
| --- | --- |
| Android product code owned by the main app | `app/src/...` |
| Reusable realtime client Android code | `realtime-client/src/...` |
| Privileged/Shizuku boundary code | `privileged-helper/src/...` |
| Audio bridge code | `audio-bridge/src/...` |
| Host-side Python implementation | `scripts/aicall_tools/<domain>/` |
| Host-side Python tests | `scripts/tests/<matching-domain>/` |
| Stable developer CLI entrypoint | `scripts/bin/` |
| Local Agent maintenance wrapper | `scripts/local_agent/` |
| Service/task declarative configuration | `service-packs/` |
| Benchmark inputs/results/evidence | `benchmarks/` |
| Durable architecture/runbook/policy documentation | `docs/` |
| CI/repository automation | `.github/` |

## Structural invariants

1. Do not add loose Python files directly under `scripts/`.
2. Do not create generic buckets such as `misc/`, `utils/`, `temp/`, `old/`, `new/`, or `helpers/` when a domain owner exists. Prefer a precise domain name.
3. Tests follow the production/tooling domain they validate. Historical evidence may live below a domain-specific `history/` directory when its versioned context is intentional.
4. Generated output, local Gradle caches, IDE state, APK/build output and temporary evidence do not belong in Git.
5. A new top-level directory is an architectural change. Before adding one, first check whether an existing Android module, `scripts/`, `service-packs/`, `benchmarks/`, or `docs/` owns the concern.
6. If a new top-level path is genuinely required, update this document and the repository-layout allowlist in `scripts/verify_host.sh` in the same change.
7. Prefer moving an existing misplaced file over adding compatibility duplicates. Keep transitional wrappers only when an external entrypoint actually requires them.

## Quality-gate contract

`scripts/verify_host.sh` validates the tracked repository root and the immediate `scripts/` layout before expensive Gradle/test work starts.

The check intentionally operates on `git ls-files`, not the raw working directory, so ignored local paths such as `.gradle/`, `build/`, `.idea/`, `local.properties`, caches and temporary files do not create false failures.

A layout failure should be fixed by placing the file in the correct existing area. Do not weaken the allowlist merely to make CI green. Extend the allowlist only when the repository architecture itself is intentionally changing, and document that change here.
