#!/usr/bin/env python3
"""CI metrics bridge.

Parses Maven Surefire JUnit XML reports and pushes a summary to a Confluence page,
plus (only when there are failures) creates a single aggregated Jira Bug issue.

All Jira/Confluence configuration (base URL, email, API token, project key, page id) is
read EXCLUSIVELY from environment variables populated by GitHub Actions Secrets/Variables
(see .github/workflows/ci.yml, job "publish-metrics"). Nothing here is hardcoded, and the
API token is never printed or logged. If a required variable is missing, the corresponding
integration is skipped with a warning instead of failing the whole run -- this keeps CI
green on first runs before secrets are configured.

Env vars used:
  JIRA_BASE_URL       e.g. https://yoursite.atlassian.net
  JIRA_EMAIL          Atlassian account email used for API token auth
  JIRA_API_TOKEN      Atlassian API token (secret)
  JIRA_PROJECT_KEY    Jira project key where failure issues get filed
  CONFLUENCE_PAGE_ID  Confluence page id to append the metrics table to
  GITHUB_SHA, GITHUB_RUN_ID, GITHUB_REPOSITORY, GITHUB_REF_NAME -- auto-provided by
    GitHub Actions, used only for report metadata/links.
  SUREFIRE_REPORTS_DIR  path to the Surefire XML reports, defaults to
    "target/surefire-reports".
"""
import base64
import glob
import json
import os
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


def warn(msg):
    print(f"::warning::{msg}", file=sys.stderr)


def parse_surefire_reports(reports_dir):
    files = sorted(glob.glob(os.path.join(reports_dir, "*.xml")))
    summary = {
        "tests": 0,
        "passed": 0,
        "failed": 0,
        "errors": 0,
        "skipped": 0,
        "classes": [],
        "failed_tests": [],
    }

    for path in files:
        try:
            tree = ET.parse(path)
        except ET.ParseError as exc:
            warn(f"No se pudo parsear {path}: {exc}")
            continue

        root = tree.getroot()
        if root.tag != "testsuite":
            continue

        class_name = root.attrib.get("name", os.path.basename(path))
        tests = int(root.attrib.get("tests", 0))
        failures = int(root.attrib.get("failures", 0))
        errors = int(root.attrib.get("errors", 0))
        skipped = int(root.attrib.get("skipped", 0))
        passed = max(tests - failures - errors - skipped, 0)

        summary["tests"] += tests
        summary["passed"] += passed
        summary["failed"] += failures
        summary["errors"] += errors
        summary["skipped"] += skipped
        summary["classes"].append({
            "name": class_name,
            "tests": tests,
            "failures": failures,
            "errors": errors,
            "skipped": skipped,
        })

        for testcase in root.findall("testcase"):
            failure_node = testcase.find("failure")
            error_node = testcase.find("error")
            node = failure_node if failure_node is not None else error_node
            if node is not None:
                test_name = f"{class_name}#{testcase.attrib.get('name', '?')}"
                message = (node.attrib.get("message") or node.text or "").strip()
                summary["failed_tests"].append({
                    "test": test_name,
                    "message": message[:300],
                })

    return summary


def pass_rate(summary):
    if summary["tests"] == 0:
        return 0.0
    return round(100.0 * summary["passed"] / summary["tests"], 1)


def basic_auth_header(email, token):
    raw = f"{email}:{token}".encode("utf-8")
    return "Basic " + base64.b64encode(raw).decode("ascii")


def http_request(url, method="GET", headers=None, data=None):
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw_body = resp.read().decode("utf-8")
        return resp.status, (json.loads(raw_body) if raw_body else {})


def build_confluence_fragment_html(summary, meta):
    rate = pass_rate(summary)
    if summary["failed_tests"]:
        rows = "".join(
            f"<tr><td>{t['test']}</td><td>{t['message']}</td></tr>"
            for t in summary["failed_tests"]
        )
    else:
        rows = "<tr><td colspan=\"2\">Sin fallos</td></tr>"

    return (
        "<h2>CI Metrics — latest run</h2>"
        f"<p><strong>Timestamp:</strong> {meta['timestamp']}<br/>"
        f"<strong>Commit:</strong> {meta['commit_short']}<br/>"
        f"<strong>Run:</strong> <a href=\"{meta['run_url']}\">{meta['run_url']}</a></p>"
        "<table><tbody>"
        "<tr><th>Total</th><th>Passed</th><th>Failed</th><th>Errors</th>"
        "<th>Skipped</th><th>Pass rate</th></tr>"
        f"<tr><td>{summary['tests']}</td><td>{summary['passed']}</td>"
        f"<td>{summary['failed']}</td><td>{summary['errors']}</td>"
        f"<td>{summary['skipped']}</td><td>{rate}%</td></tr>"
        "</tbody></table>"
        "<h3>Failed tests</h3>"
        "<table><tbody>"
        "<tr><th>Test</th><th>Message</th></tr>"
        f"{rows}"
        "</tbody></table>"
    )


def publish_to_confluence(summary, meta):
    base_url = os.environ.get("JIRA_BASE_URL")
    email = os.environ.get("JIRA_EMAIL")
    token = os.environ.get("JIRA_API_TOKEN")
    page_id = os.environ.get("CONFLUENCE_PAGE_ID")

    missing = [name for name, val in [
        ("JIRA_BASE_URL", base_url),
        ("JIRA_EMAIL", email),
        ("JIRA_API_TOKEN", token),
        ("CONFLUENCE_PAGE_ID", page_id),
    ] if not val]
    if missing:
        warn(f"Confluence: variables faltantes {missing}, se omite la publicación.")
        return

    headers = {"Authorization": basic_auth_header(email, token), "Accept": "application/json"}
    get_url = f"{base_url}/wiki/rest/api/content/{page_id}?expand=body.storage,version"
    try:
        _, page = http_request(get_url, headers=headers)
    except (urllib.error.URLError, urllib.error.HTTPError) as exc:
        warn(f"Confluence: no se pudo leer la página {page_id}: {exc}")
        return

    existing_body = page.get("body", {}).get("storage", {}).get("value", "")
    new_body = existing_body + build_confluence_fragment_html(summary, meta)

    put_url = f"{base_url}/wiki/rest/api/content/{page_id}"
    payload = {
        "id": page_id,
        "type": page.get("type", "page"),
        "title": page.get("title"),
        "version": {"number": page.get("version", {}).get("number", 0) + 1},
        "body": {"storage": {"value": new_body, "representation": "storage"}},
    }
    try:
        http_request(put_url, method="PUT", headers=headers, data=payload)
        print(f"Confluence: página {page_id} actualizada correctamente.")
    except (urllib.error.URLError, urllib.error.HTTPError) as exc:
        warn(f"Confluence: no se pudo actualizar la página {page_id}: {exc}")


def build_jira_description_adf(summary, meta):
    lines = [f"CI run: {meta['run_url']}", f"Commit: {meta['commit_short']}", ""]
    for t in summary["failed_tests"]:
        lines.append(f"- {t['test']}: {t['message']}")
    text = "\n".join(lines)
    return {
        "type": "doc",
        "version": 1,
        "content": [{"type": "paragraph", "content": [{"type": "text", "text": text}]}],
    }


def publish_to_jira(summary, meta):
    if summary["failed"] + summary["errors"] == 0:
        print("Jira: sin fallos, no se crea ningún issue.")
        return

    base_url = os.environ.get("JIRA_BASE_URL")
    email = os.environ.get("JIRA_EMAIL")
    token = os.environ.get("JIRA_API_TOKEN")
    project_key = os.environ.get("JIRA_PROJECT_KEY")

    missing = [name for name, val in [
        ("JIRA_BASE_URL", base_url),
        ("JIRA_EMAIL", email),
        ("JIRA_API_TOKEN", token),
        ("JIRA_PROJECT_KEY", project_key),
    ] if not val]
    if missing:
        warn(f"Jira: variables faltantes {missing}, se omite la creación del issue.")
        return

    headers = {"Authorization": basic_auth_header(email, token)}
    summary_text = (
        f"CI test failures on {meta['branch']}/{meta['commit_short']} "
        f"— {summary['failed'] + summary['errors']} failed"
    )
    payload = {
        "fields": {
            "project": {"key": project_key},
            "issuetype": {"name": "Bug"},
            "summary": summary_text,
            "description": build_jira_description_adf(summary, meta),
        }
    }
    url = f"{base_url}/rest/api/3/issue"
    try:
        _, resp = http_request(url, method="POST", headers=headers, data=payload)
        print(f"Jira: issue creado -> {resp.get('key')}")
    except (urllib.error.URLError, urllib.error.HTTPError) as exc:
        warn(f"Jira: no se pudo crear el issue: {exc}")


def main():
    reports_dir = os.environ.get("SUREFIRE_REPORTS_DIR", "target/surefire-reports")
    if not os.path.isdir(reports_dir):
        warn(f"No existe el directorio de reportes '{reports_dir}', nada que publicar.")
        return

    summary = parse_surefire_reports(reports_dir)
    commit = os.environ.get("GITHUB_SHA", "unknown")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    branch = os.environ.get("GITHUB_REF_NAME", "unknown")

    meta = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "commit_short": commit[:7],
        "run_url": (
            f"https://github.com/{repository}/actions/runs/{run_id}"
            if repository and run_id else ""
        ),
        "branch": branch,
    }

    print(
        f"Surefire summary: {summary['tests']} tests, {summary['passed']} passed, "
        f"{summary['failed']} failed, {summary['errors']} errors, {summary['skipped']} skipped "
        f"({pass_rate(summary)}% pass rate)."
    )

    publish_to_confluence(summary, meta)
    publish_to_jira(summary, meta)


if __name__ == "__main__":
    main()
