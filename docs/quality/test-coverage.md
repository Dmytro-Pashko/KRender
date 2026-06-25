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

## Reports

Coverage reports are written to:

- `build/reports/coverage/unit/html/index.html`
- `build/reports/coverage/unit/jacoco.xml`
- `build/reports/coverage/unit/jacoco.csv`

The static-analysis summary also links to the latest coverage artifacts:

- `build/reports/static-analysis/summary.md`
