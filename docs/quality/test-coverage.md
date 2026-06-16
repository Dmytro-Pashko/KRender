# Unit Test Coverage

KRender uses Gradle JaCoCo for JVM unit test coverage reporting.

## Commands

Generate the coverage report directly from the repository root:

```bash
./gradlew unitTestCoverageReport
```

Run the full static-analysis workflow, including coverage generation:

```bash
./scripts/static-analysis.sh
```

## Scope

The current coverage report targets the `core` module JVM unit tests:

- test task: `core:test`
- sources: `core/src/main/kotlin` and `core/src/main/java`
- compiled classes: `core/build/classes/kotlin/main` and `core/build/classes/java/main`

This report does **not** currently aggregate coverage from `games:woolboy` or `apps:woolboy-desktop`.
That matches the current modularization scope: Woolboy gained standalone modules and packaging, but this change does not add a new Woolboy unit-test suite or a separate coverage threshold.

The report excludes generated outputs, test classes, and obvious bootstrap entry points such as `Main` and launcher classes. It does not exclude production engine packages to inflate the baseline.

## Reports

Coverage reports are written to:

- `build/reports/coverage/unit/html/index.html`
- `build/reports/coverage/unit/jacoco.xml`
- `build/reports/coverage/unit/jacoco.csv`

The static-analysis summary also links to the latest coverage artifacts:

- `build/reports/static-analysis/summary.md`

## Policy

No minimum coverage threshold is enforced yet.

`unitTestCoverageReport` generates reports, but it does not gate `check` on a percentage and does not require `jacocoTestCoverageVerification`.

Recommended next step after the first baseline report is reviewed:

1. Inspect low-coverage engine packages and add missing unit tests where the logic is already JVM-testable.
2. Agree on an initial line coverage threshold for `core`.
3. Add `jacocoTestCoverageVerification` only after the team accepts the baseline and the threshold.
