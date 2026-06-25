# Static Analysis

KRender now has a root static-analysis workflow in `build.gradle.kts` for Kotlin formatting,
static analysis, JVM verification, and unit test coverage reporting for the `core` module.
The source scan includes nested game/app modules such as `games:woolboy` and `apps:woolboy-desktop`,
plus the platform desktop launcher modules.

## Commands

Run the full check workflow from the repository root:

```bash
./scripts/static-analysis.sh
```

Run safe Kotlin auto-formatting first, then rerun verification:

```bash
./scripts/static-analysis.sh --fix
```

You can also run the Gradle tasks directly:

```bash
./gradlew ktlintCheck detekt test
./gradlew unitTestCoverageReport
./gradlew ktlintFormat
./gradlew staticAnalysis
./gradlew check
```

## What Runs

- `ktlint` checks Kotlin source formatting and style for `core`, nested `games/**`, nested `apps/**`, desktop launcher modules, and repository `.kts` files, excluding `build/`, `.gradle/`, and `generated/`.
- `detekt` checks Kotlin code quality, complexity, and code smells using `config/detekt/detekt.yml` across the same repository source roots.
- `test` runs the existing JVM test suite, including architecture verification such as `BackendBoundaryTest`
  and Gradle dependency boundary checks.
- `unitTestCoverageReport` runs `core:test` and then generates JaCoCo HTML, XML, and CSV reports for the `core` JVM unit tests.

## Reports

The wrapper script always writes a summary report to:

```text
build/reports/static-analysis/summary.md
```

Primary report outputs:

- `build/reports/detekt/detekt.html`
- `build/reports/detekt/detekt.md`
- `build/reports/detekt/detekt.xml`
- `build/reports/detekt/detekt.sarif`
- `core/build/reports/tests/test/index.html`
- `build/reports/coverage/unit/html/index.html`
- `build/reports/coverage/unit/jacoco.xml`
- `build/reports/coverage/unit/jacoco.csv`
- `build/reports/static-analysis/logs/*.log`

Coverage details and future policy are documented in:

```text
docs/quality/test-coverage.md
```

## Detekt Baseline

Detekt uses:

```text
config/detekt/baseline.xml
```

The baseline is still configured in Gradle, but it should be kept in sync with the current codebase. Keep
`ignoreFailures = false` so new findings stay visible. After fixing previously baselined issues, regenerate the
baseline so resolved entries are removed instead of lingering in `baseline.xml`:

```bash
./gradlew detektBaseline
```

Review the resulting diff before committing it. The goal is to shrink the baseline over time and eventually remove it
when the suppressed backlog is gone.


## CI Readiness

This repository already has GitHub Actions workflows under `.github/workflows/`:

- `release.yml` runs pull-request validation for full Kotlin compile, JVM tests, Detekt, docs build, and desktop artifact builds.
- `pages.yml` builds and deploys the MkDocs site to GitHub Pages from `master`.

The current docs build installs dependencies from:

```text
requirements-docs.txt
```

The current CI/static-analysis entry points are:

```bash
./scripts/static-analysis.sh
./gradlew detekt
mkdocs build --strict
```

There is not currently a dedicated single `quality-checks.yml` workflow; quality validation is split across the
release and pages workflows. Keep static-analysis setup changes separate from large runtime or architecture refactors
so failures stay attributable to tooling changes.
