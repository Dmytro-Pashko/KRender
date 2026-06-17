# Git Workflow

KRender uses a two-branch development and release flow with short-lived feature branches:

- `develop` - default protected development branch.
- `master` - stable release branch.
- `feature/<feature-name>` - short-lived feature branches created for isolated work.

### Feature PR Flow

Feature work should branch from `develop` and return to `develop` through pull requests.

1. Create `feature/<feature-name>` from `develop`.
2. Implement the change on the feature branch.
3. Open a pull request targeting `develop`.
4. Merge only after the pull request passes CI and review.

Feature pull requests should pass:

- static analysis;
- lint / ktlint;
- formatting check;
- compile checks;
- `core` and `engine:scene-player` unit tests.

Feature pull requests do not require a full release build.

### Release Flow

Releases are promoted from `develop` into `master`.

1. Open a release pull request from `develop` into `master`.
2. Run the full release validation set on the release pull request.
3. Merge the release pull request into `master`.
4. Create a version tag on `master` using the format `vX.Y.Z`.
5. Let the version tag trigger the GitHub Release workflow.

Release pull requests should pass:

- full compile;
- tests;
- static analysis;
- docs build;
- desktop artifacts build.

### Distribution

- GitHub Releases publish desktop binaries, zip bundles, and demo artifacts.
- GitHub Packages publish Gradle engine modules for consumption outside this repository.

### Documentation

- The documentation portal is planned to use MkDocs.
- The published docs site is intended to deploy through GitHub Pages.
- Docs source and portal structure will be created in a follow-up task.

### Repository Setup

Manual GitHub repository configuration for this workflow:

1. Create `develop` from the current `feature/v2` branch.
2. Set `develop` as the default branch.
3. Protect `develop`.
4. Protect `master`.
5. Require pull requests before merging into `develop` and `master`.
6. Require status checks before merging.
7. Disallow force pushes and branch deletion on protected branches.
8. Configure GitHub Pages to use GitHub Actions as the source.
9. Configure package publishing permissions for release workflows.
