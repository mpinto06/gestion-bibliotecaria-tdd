## [2026-07-19] Black-box regression test skeleton + CI pipeline for security metrics

**Risks**
- The 4 vulnerability regression tests in `AccessControlSecurityTest` are designed to FAIL until the app is fixed; anyone running `mvn test` locally or in CI will see red without context unless they read the test comments or this log.
- `publish_metrics.py` performs live HTTP calls to Jira/Confluence Cloud on every CI run once secrets are configured; a misconfigured `CONFLUENCE_PAGE_ID` will silently no-op (by design) rather than fail the build.
- Tests share one Spring context/H2 instance per JVM run (context caching); relies on globally-unique emails per test method to avoid cross-test collisions — future tests must keep following that convention.
- `.github/workflows/ci.yml` is a new workflow alongside the pre-existing `.github/workflows/tests.yml`; both will run on pushes to `testing`/`main`, doubling CI minutes until one is retired or scoped down.

**Technical debt / follow-ups**
- No cleanup (`@AfterEach`) of users created by black-box tests — acceptable for now since H2 is in-memory and recreated per JVM run, but will need review if tests move to a persistent/shared DB.
- `publish_metrics.py` has no automated tests of its own (stdlib-only script, exercised only via `py_compile` syntax check).
- Consider consolidating `ci.yml` and `tests.yml` into a single workflow to avoid duplicate `mvn test` runs.

## [2026-07-19] Merged metrics job into existing tests.yml

**Risks**
- The pre-existing `.github/workflows/tests.yml` (and its 42-test `DEFECTO` security suite) was discovered only after the first pass already added a parallel `ci.yml`; folded the `publish-metrics` steps into `tests.yml` and deleted `ci.yml` to remove the duplicate-run risk noted above. No other content from `tests.yml` was touched.

**Technical debt / follow-ups**
- None identified beyond what's already listed above.
