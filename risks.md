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

## [2026-07-19] Added JaCoCo coverage reporting to build and CI metrics pipeline

**Risks**
- Current coverage is low (line 66.0%, branch 51.9% observed locally) and no threshold gate was added on purpose — this is a course project still building out its suite; a hard gate would make CI red for reasons unrelated to what's being tested right now.
- `mvn -B test` fails the build outright due to the intentional DEFECTO tests, which aborts before the phase-bound `jacoco:report` execution runs. Worked around by adding a separate `if: always()` step in `tests.yml` that runs `mvn -B jacoco:report` standalone, reusing the `target/jacoco.exec` the agent already wrote during the (failed) test run.
- `parse_jacoco_report` reads only root-level `<counter>` totals (whole-project); per-package/per-class breakdown is not surfaced in Confluence.

**Technical debt / follow-ups**
- Consider adding a coverage-threshold gate once the suite matures past its current bootstrap phase.
- No automated test covers `parse_jacoco_report` itself (matches the pre-existing pattern of `publish_metrics.py` having no unit tests, only a manual/dry-run check).

## [2026-07-19] Extended CI metrics: category breakdown, per-component coverage, security-control confirmation rate, hallazgo density/distribution

**Risks**
- `SECURITY_FINDINGS` (20 rows) and `COMPONENTS` (9 rows) in `scripts/publish_metrics.py` are hardcoded, hand-maintained constants; if a finding gets fixed or a new one is discovered, someone must remember to update the catalog manually or the density/distribution tables silently go stale.
- The Confluence page body now uses `<!-- CI-METRICS-START -->`/`<!-- CI-METRICS-END -->` markers with append-once/replace-in-place semantics (`apply_confluence_fragment`); if a human ever manually edits text between those exact markers on the live page, the next CI run will silently overwrite it.
- `target/surefire-reports` can contain stale XML from pre-rename test classes if `mvn test` runs without `clean` first (discovered locally: old `*Test.java` reports lingered next to renamed `*IntegrationTest.java` ones and would have double-counted); CI's `mvn -B test` step doesn't run `clean`, so a similar stale-report situation could recur in CI's own workspace between cache-restored runs.
- Security-tagged test detection (`find_security_tagged_tests`) relies on a fragile textual convention (contiguous `//` comment lines directly above `@Test`/`@ParameterizedTest` mentioning ISO25010/ISO27001/ASVS); reformatting comments (e.g. adding a blank line, using `/* */`) silently drops a test from the security-controls rate with no error.

**Technical debt / follow-ups**
- Consider generating `SECURITY_FINDINGS`/`COMPONENTS` from a shared source (e.g. a YAML file) instead of Python literals, so non-engineers can update the catalog.
- Consider having the CI workflow run `mvn -B clean test` instead of `mvn -B test` to avoid stale Surefire reports across cached `target/` directories.
- Still no automated tests for `publish_metrics.py` itself; verification remains manual (`--dry-run` flag added this session, exercised against real local Surefire/JaCoCo output).
