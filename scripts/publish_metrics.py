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
  JACOCO_REPORT_PATH    path to the JaCoCo XML report, defaults to
    "target/site/jacoco/jacoco.xml".
"""
import base64
import glob
import json
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


def warn(msg):
    print(f"::warning::{msg}", file=sys.stderr)


# ---------------------------------------------------------------------------
# Security findings catalog -- maintained BY HAND, update if the security
# findings catalog changes (e.g. a finding gets fixed, a new one is found).
# Source: manual security analysis of the 9 components below, already
# reviewed/approved by the team. "componente" values match the "class_path"
# labels in COMPONENTS so both tables can be grouped/joined consistently.
# ---------------------------------------------------------------------------
SECURITY_FINDINGS = [
    {"hallazgo": "Usuario.contrasena serializada en JSON", "componente": "service/UsuarioService", "subcaracteristicas": ["Confidencialidad"]},
    {"hallazgo": "Prestamo->Usuario.contrasena serializada", "componente": "service/PrestamoService", "subcaracteristicas": ["Confidencialidad"]},
    {"hallazgo": "Sin validacion de fortaleza de contrasena (registro)", "componente": "service/UsuarioService", "subcaracteristicas": ["Confidencialidad"]},
    {"hallazgo": "Sin validacion de fortaleza de contrasena (cambio)", "componente": "service/UsuarioService", "subcaracteristicas": ["Confidencialidad"]},
    {"hallazgo": "CSRF deshabilitado globalmente", "componente": "config/SecurityConfig", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "Sin Bean Validation - UsuarioRequest", "componente": "controller/Controller", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "Sin Bean Validation - LibroRequest", "componente": "controller/Controller", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "Sin Bean Validation - ResenaRequest", "componente": "controller/Controller", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "Sin Bean Validation - ComentarioResenaRequest", "componente": "controller/Controller", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "Sin Bean Validation - PrestamoRequest", "componente": "controller/Controller", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "Validacion de ISBN rota con negativos (length()==13)", "componente": "service/LibroService", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "actualizarLibroPorIsbn copia cantidad sin validar", "componente": "service/LibroService", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "renovarPrestamo decrementa stock sin chequear disponibilidad", "componente": "service/PrestamoService", "subcaracteristicas": ["Integridad"]},
    {"hallazgo": "permitAll en /api/libros/** habilita DELETE/PUT sin auth", "componente": "config/SecurityConfig", "subcaracteristicas": ["Integridad", "Autenticidad"]},
    {"hallazgo": "crearResena confia en usuarioId del body sin verificar identidad", "componente": "controller/Controller", "subcaracteristicas": ["Autenticidad"]},
    {"hallazgo": "crearComentarioResena - mismo patron", "componente": "controller/Controller", "subcaracteristicas": ["Autenticidad"]},
    {"hallazgo": "verificarAmonestacion sin Authentication (B7)", "componente": "controller/Controller", "subcaracteristicas": ["Autenticidad"]},
    {"hallazgo": "getTodasAmonestaciones sin Authentication (B8)", "componente": "controller/Controller", "subcaracteristicas": ["Autenticidad"]},
    {"hallazgo": "Login con JSON nunca llega al controller real", "componente": "config/SecurityConfig", "subcaracteristicas": ["Autenticidad"]},
    {"hallazgo": "Registro persiste rol malicioso enviado por el cliente (A3)", "componente": "service/UsuarioService", "subcaracteristicas": ["Autenticidad", "Integridad"]},
]

# Fixed list of the 9 main-code "components" used as the density denominator
# and for per-component coverage lookup. "class_path" is the JaCoCo class
# name (package/Class, slash-separated) as it appears in jacoco.xml.
# Maintained BY HAND alongside SECURITY_FINDINGS above.
COMPONENTS = [
    {"label": "controller/Controller", "class_path": "com/biblioteca/controller/Controller"},
    {"label": "service/UsuarioService", "class_path": "com/biblioteca/service/UsuarioService"},
    {"label": "service/LibroService", "class_path": "com/biblioteca/service/LibroService"},
    {"label": "service/PrestamoService", "class_path": "com/biblioteca/service/PrestamoService"},
    {"label": "service/ResenaService", "class_path": "com/biblioteca/service/ResenaService"},
    {"label": "service/ComentarioResenaService", "class_path": "com/biblioteca/service/ComentarioResenaService"},
    {"label": "service/AmonestacionService", "class_path": "com/biblioteca/service/AmonestacionService"},
    {"label": "config/SecurityConfig", "class_path": "com/biblioteca/config/SecurityConfig"},
    {"label": "security/CustomUserDetailsService", "class_path": "com/biblioteca/security/CustomUserDetailsService"},
]

# Explicit FQCNs for "unitarias" (Mockito-only, no Spring context) test classes.
# Everything under com.biblioteca.integration.*IntegrationTest is "integracion",
# everything under com.biblioteca.blackbox.** is "caja_negra" -- those two are
# matched by pattern instead of an explicit list (see categorize_testsuite).
UNIT_TEST_CLASSES = {
    "com.biblioteca.service.UsuarioServiceTest",
    "com.biblioteca.service.LibroServiceTest",
    "com.biblioteca.service.PrestamoServiceTest",
    "com.biblioteca.service.AmonestacionServiceTest",
    "com.biblioteca.security.CustomUserDetailsServiceTest",
    "com.biblioteca.controller.ControllerAuthorizationTest",
    "com.biblioteca.model.ModelSerializationTest",
}

TEST_CATEGORY_LABELS = {
    "unitarias": "Pruebas unitarias",
    "integracion": "Pruebas de integracion",
    "caja_negra": "Pruebas de caja negra",
}


def categorize_testsuite(class_name):
    """Classify a Surefire <testsuite name="..."> (a fully-qualified test
    class name) into "unitarias" / "integracion" / "caja_negra", or None if
    it doesn't belong to the taxonomy (e.g. the bare context-load smoke test).
    """
    if class_name.startswith("com.biblioteca.blackbox."):
        return "caja_negra"
    if class_name.startswith("com.biblioteca.integration.") and class_name.endswith("IntegrationTest"):
        return "integracion"
    if class_name in UNIT_TEST_CLASSES:
        return "unitarias"
    return None


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
        # Every individual <testcase>, kept flat (with pass/fail state) so
        # other functions (e.g. the security-controls confirmation rate) can
        # cross-reference specific test methods without re-parsing the XML.
        "testcases": [],
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
            "category": categorize_testsuite(class_name),
        })

        for testcase in root.findall("testcase"):
            failure_node = testcase.find("failure")
            error_node = testcase.find("error")
            node = failure_node if failure_node is not None else error_node
            testcase_failed = node is not None
            testcase_classname = testcase.attrib.get("classname", class_name)
            testcase_name = testcase.attrib.get("name", "?")
            summary["testcases"].append({
                "classname": testcase_classname,
                "name": testcase_name,
                "failed": testcase_failed,
            })
            if node is not None:
                test_name = f"{class_name}#{testcase_name}"
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


def build_category_summaries(summary):
    """Group the flat class list from parse_surefire_reports into the three
    test-taxonomy categories (unitarias / integracion / caja_negra), each
    shaped like the overall summary (tests/passed/failed/errors/skipped).
    Classes with category None (e.g. the bare context-load smoke test) are
    excluded, matching the taxonomy in use.
    """
    categories = {
        key: {"tests": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0}
        for key in TEST_CATEGORY_LABELS
    }
    for cls in summary["classes"]:
        category = cls.get("category")
        if category not in categories:
            continue
        bucket = categories[category]
        bucket["tests"] += cls["tests"]
        bucket["failed"] += cls["failures"]
        bucket["errors"] += cls["errors"]
        bucket["skipped"] += cls["skipped"]
        bucket["passed"] += max(cls["tests"] - cls["failures"] - cls["errors"] - cls["skipped"], 0)
    return categories


def parse_jacoco_report(path):
    """Read a JaCoCo XML report and return
    {"line": pct, "branch": pct, "components": {label: pct_or_None}}.

    "line"/"branch" use the root-level <counter> elements (whole-project
    totals). "components" reads per-class LINE counters (JaCoCo XML has
    <package><class name="com/biblioteca/service/Foo">...<counter
    type="LINE" .../></class></package>) matched against COMPONENTS by
    class name. Returns None for a metric/component (or the whole dict
    entry) when the file is missing, unparsable, a given counter type isn't
    present, or a component class isn't found in the report (e.g. zero
    test-relevant instructions) -- mirrors the defensive "skip with a
    warning" pattern used for missing env vars above.
    """
    result = {"line": None, "branch": None, "components": {c["label"]: None for c in COMPONENTS}}

    if not os.path.isfile(path):
        warn(f"No existe el reporte de JaCoCo '{path}', se omite la cobertura.")
        return result

    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        warn(f"No se pudo parsear el reporte de JaCoCo '{path}': {exc}")
        return result

    root = tree.getroot()
    type_to_key = {"LINE": "line", "BRANCH": "branch"}

    for counter in root.findall("counter"):
        counter_type = counter.attrib.get("type")
        key = type_to_key.get(counter_type)
        if key is None:
            continue

        missed = int(counter.attrib.get("missed", 0))
        covered = int(counter.attrib.get("covered", 0))
        total = missed + covered
        result[key] = round(100.0 * covered / total, 1) if total else 0.0

    class_path_to_label = {c["class_path"]: c["label"] for c in COMPONENTS}
    for package in root.findall("package"):
        for cls in package.findall("class"):
            class_name = cls.attrib.get("name", "")
            label = class_path_to_label.get(class_name)
            if label is None:
                continue
            for counter in cls.findall("counter"):
                if counter.attrib.get("type") != "LINE":
                    continue
                missed = int(counter.attrib.get("missed", 0))
                covered = int(counter.attrib.get("covered", 0))
                total = missed + covered
                result["components"][label] = round(100.0 * covered / total, 1) if total else 0.0

    return result


def find_security_tagged_tests(test_src_dir):
    """Scan every .java file under test_src_dir for test methods whose
    immediately preceding comment block (contiguous "//" lines directly
    above the @Test/@ParameterizedTest annotation, stopping at a blank or
    non-comment line) mentions ISO25010, ISO27001, or ASVS.

    Returns a set of (fully_qualified_class_name, method_name) tuples. Method
    name is the base name without the parameterized-test parameter list
    (e.g. "foo" not "foo(String)"), so it can be matched against Surefire
    testcase names (which append "(Type)[N]" for parameterized cases).
    """
    tags = ("ISO25010", "ISO27001", "ASVS")
    annotation_re = re.compile(r"^@(Test|ParameterizedTest)\b")
    package_re = re.compile(r"^\s*package\s+([\w.]+)\s*;")
    method_re = re.compile(r"\bvoid\s+(\w+)\s*\(")

    found = set()
    java_files = sorted(glob.glob(os.path.join(test_src_dir, "**", "*.java"), recursive=True))
    for java_path in java_files:
        try:
            with open(java_path, encoding="utf-8") as fh:
                lines = fh.read().splitlines()
        except OSError as exc:
            warn(f"No se pudo leer {java_path}: {exc}")
            continue

        package = None
        for line in lines:
            match = package_re.match(line)
            if match:
                package = match.group(1)
                break
        if not package:
            continue

        class_name = os.path.splitext(os.path.basename(java_path))[0]
        fqcn = f"{package}.{class_name}"

        for i, line in enumerate(lines):
            if not annotation_re.match(line.strip()):
                continue

            method_name = None
            for j in range(i, min(i + 6, len(lines))):
                match = method_re.search(lines[j])
                if match:
                    method_name = match.group(1)
                    break
            if method_name is None:
                continue

            comment_lines = []
            k = i - 1
            while k >= 0:
                stripped = lines[k].strip()
                if stripped.startswith("//"):
                    comment_lines.append(stripped)
                    k -= 1
                else:
                    break

            if any(tag in comment for comment in comment_lines for tag in tags):
                found.add((fqcn, method_name))

    if not found:
        warn(f"No se encontraron tests etiquetados como controles de seguridad bajo '{test_src_dir}'.")

    return found


def compute_security_controls_summary(summary, tagged_tests):
    """Cross-reference the security-tagged (class, method) pairs found by
    find_security_tagged_tests against the flat Surefire testcase results,
    to compute the "Tasa de Confirmacion de Controles": the proportion of
    security-tagged tests that pass today. Returns None if no tagged test
    was matched in the Surefire results (e.g. tagging parse issue), so the
    caller can skip rendering the table instead of dividing by zero.
    """
    if not tagged_tests:
        return None

    total = 0
    failing = 0
    for testcase in summary["testcases"]:
        base_name = testcase["name"].split("(")[0]
        if (testcase["classname"], base_name) in tagged_tests:
            total += 1
            if testcase["failed"]:
                failing += 1

    if total == 0:
        warn("Ningun testcase de Surefire coincide con los tests etiquetados como controles de seguridad.")
        return None

    rate = round(100.0 * (total - failing) / total, 1)
    return {"total": total, "failing": failing, "rate": rate}


def build_hallazgos_density():
    """Group SECURITY_FINDINGS by componente. Returns (rows, global_density)
    where rows is a list of {"componente", "hallazgos"} sorted by componente,
    and global_density is total_hallazgos / len(COMPONENTS).
    """
    counts = {c["label"]: 0 for c in COMPONENTS}
    for finding in SECURITY_FINDINGS:
        counts[finding["componente"]] = counts.get(finding["componente"], 0) + 1

    rows = [{"componente": label, "hallazgos": counts[label]} for label in sorted(counts)]
    global_density = round(len(SECURITY_FINDINGS) / len(COMPONENTS), 2)
    return rows, global_density


def build_hallazgos_distribution():
    """Group SECURITY_FINDINGS by subcaracteristica (a dual-tagged finding
    counts toward both buckets, so percentages can sum to over 100% -- this
    is intentional). Returns a list of {"subcaracteristica", "hallazgos", "pct"}.
    """
    counts = {}
    for finding in SECURITY_FINDINGS:
        for sub in finding["subcaracteristicas"]:
            counts[sub] = counts.get(sub, 0) + 1

    total = len(SECURITY_FINDINGS)
    rows = []
    for sub in sorted(counts):
        count = counts[sub]
        pct = round(100.0 * count / total, 1) if total else 0.0
        rows.append({"subcaracteristica": sub, "hallazgos": count, "pct": pct})
    return rows


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


# Markers wrapping the whole generated fragment inside the Confluence page
# body. On each run: if both markers are already present, the content
# between them is REPLACED (page stays a fixed size); otherwise the marked
# block is appended once (first run). Anything the team writes above/below
# the markers is preserved untouched. See apply_confluence_fragment below.
CONFLUENCE_FRAGMENT_START = "<!-- CI-METRICS-START -->"
CONFLUENCE_FRAGMENT_END = "<!-- CI-METRICS-END -->"


def _category_summary_table_html(title, cat_summary):
    rate = pass_rate(cat_summary)
    return (
        f"<h3>{title}</h3>"
        "<table><tbody>"
        "<tr><th>Total</th><th>Passed</th><th>Failed</th><th>Errors</th>"
        "<th>Skipped</th><th>Pass rate</th></tr>"
        f"<tr><td>{cat_summary['tests']}</td><td>{cat_summary['passed']}</td>"
        f"<td>{cat_summary['failed']}</td><td>{cat_summary['errors']}</td>"
        f"<td>{cat_summary['skipped']}</td><td>{rate}%</td></tr>"
        "</tbody></table>"
    )


def build_confluence_fragment_html(
    summary,
    meta,
    coverage=None,
    category_summaries=None,
    security_controls=None,
    hallazgos_density=None,
    hallazgos_distribution=None,
):
    rate = pass_rate(summary)
    if summary["failed_tests"]:
        rows = "".join(
            f"<tr><td>{t['test']}</td><td>{t['message']}</td></tr>"
            for t in summary["failed_tests"]
        )
    else:
        rows = "<tr><td colspan=\"2\">Sin fallos</td></tr>"

    coverage = coverage or {}
    line_pct = coverage.get("line")
    branch_pct = coverage.get("branch")
    line_cell = f"{line_pct}%" if line_pct is not None else "N/A"
    branch_cell = f"{branch_pct}%" if branch_pct is not None else "N/A"

    category_html = ""
    for key, title in TEST_CATEGORY_LABELS.items():
        cat_summary = (category_summaries or {}).get(key)
        if cat_summary is not None:
            category_html += _category_summary_table_html(title, cat_summary)

    component_rows = "".join(
        f"<tr><td>{label}</td><td>{f'{pct}%' if pct is not None else 'N/A'}</td></tr>"
        for label, pct in (coverage.get("components") or {}).items()
    )
    coverage_by_component_html = (
        "<h3>Cobertura por componente</h3>"
        "<table><tbody>"
        "<tr><th>Componente</th><th>Cobertura de lineas</th></tr>"
        f"{component_rows}"
        "</tbody></table>"
    )

    security_controls_html = ""
    if security_controls is not None:
        security_controls_html = (
            "<h3>Tasa de Confirmacion de Controles</h3>"
            "<table><tbody>"
            "<tr><th>Total controles evaluados</th><th>Fallando</th><th>Tasa de confirmacion</th></tr>"
            f"<tr><td>{security_controls['total']}</td><td>{security_controls['failing']}</td>"
            f"<td>{security_controls['rate']}%</td></tr>"
            "</tbody></table>"
        )

    density_html = ""
    if hallazgos_density is not None:
        density_rows, global_density = hallazgos_density
        # There's no natural per-component denominator (e.g. LOC) to divide
        # by, so the per-row figure is just the hallazgo count; the divided
        # global density is reported separately below the table.
        rows_html = "".join(
            f"<tr><td>{row['componente']}</td><td>{row['hallazgos']}</td></tr>"
            for row in density_rows
        )
        density_html = (
            "<h3>Densidad de Hallazgos por componente</h3>"
            "<table><tbody>"
            "<tr><th>Componente</th><th>Hallazgos</th></tr>"
            f"{rows_html}"
            "</tbody></table>"
            f"<p>Densidad global = {len(SECURITY_FINDINGS)} hallazgos &divide; {len(COMPONENTS)} "
            f"componentes = {global_density}</p>"
        )

    distribution_html = ""
    if hallazgos_distribution is not None:
        rows_html = "".join(
            f"<tr><td>{row['subcaracteristica']}</td><td>{row['hallazgos']}</td><td>{row['pct']}%</td></tr>"
            for row in hallazgos_distribution
        )
        distribution_html = (
            "<h3>Distribucion de Hallazgos por Subcaracteristica</h3>"
            "<table><tbody>"
            "<tr><th>Subcaracteristica</th><th>Hallazgos</th><th>% del total</th></tr>"
            f"{rows_html}"
            "</tbody></table>"
        )

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
        f"{category_html}"
        "<h3>Code coverage</h3>"
        "<table><tbody>"
        "<tr><th>Line coverage</th><th>Branch coverage</th></tr>"
        f"<tr><td>{line_cell}</td><td>{branch_cell}</td></tr>"
        "</tbody></table>"
        f"{coverage_by_component_html}"
        f"{security_controls_html}"
        f"{density_html}"
        f"{distribution_html}"
        "<h3>Failed tests</h3>"
        "<table><tbody>"
        "<tr><th>Test</th><th>Message</th></tr>"
        f"{rows}"
        "</tbody></table>"
    )


def apply_confluence_fragment(existing_body, fragment_html):
    """Wrap fragment_html between CONFLUENCE_FRAGMENT_START/END and merge it
    into existing_body: replace the previously marked block in place if the
    markers are already present, otherwise append the marked block once
    (first run). Implemented with plain str.find (no HTML parser) per the
    existing "keep it simple" style of this script. Anything outside the
    markers is left untouched.
    """
    wrapped = f"{CONFLUENCE_FRAGMENT_START}{fragment_html}{CONFLUENCE_FRAGMENT_END}"

    start_idx = existing_body.find(CONFLUENCE_FRAGMENT_START)
    end_idx = existing_body.find(CONFLUENCE_FRAGMENT_END)

    if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
        end_idx += len(CONFLUENCE_FRAGMENT_END)
        return existing_body[:start_idx] + wrapped + existing_body[end_idx:]

    return existing_body + wrapped


def publish_to_confluence(
    summary,
    meta,
    coverage=None,
    category_summaries=None,
    security_controls=None,
    hallazgos_density=None,
    hallazgos_distribution=None,
):
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
    fragment_html = build_confluence_fragment_html(
        summary, meta, coverage, category_summaries,
        security_controls, hallazgos_density, hallazgos_distribution,
    )
    new_body = apply_confluence_fragment(existing_body, fragment_html)

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
    category_summaries = build_category_summaries(summary)

    jacoco_path = os.environ.get("JACOCO_REPORT_PATH", "target/site/jacoco/jacoco.xml")
    coverage = parse_jacoco_report(jacoco_path)

    test_src_dir = os.environ.get("TEST_SRC_DIR", "src/test/java")
    tagged_tests = find_security_tagged_tests(test_src_dir)
    security_controls = compute_security_controls_summary(summary, tagged_tests)

    hallazgos_density = build_hallazgos_density()
    hallazgos_distribution = build_hallazgos_distribution()

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
    for key, title in TEST_CATEGORY_LABELS.items():
        cat = category_summaries[key]
        print(
            f"  {title}: {cat['tests']} tests, {cat['passed']} passed, {cat['failed']} failed, "
            f"{cat['errors']} errors, {cat['skipped']} skipped ({pass_rate(cat)}% pass rate)."
        )
    print(
        f"JaCoCo summary: line={coverage['line']}%, branch={coverage['branch']}%"
        if coverage["line"] is not None or coverage["branch"] is not None
        else "JaCoCo summary: no coverage data available."
    )
    if security_controls is not None:
        print(
            f"Security controls: {security_controls['total']} evaluados, "
            f"{security_controls['failing']} fallando, {security_controls['rate']}% tasa de confirmacion."
        )

    publish_to_confluence(
        summary, meta, coverage, category_summaries,
        security_controls, hallazgos_density, hallazgos_distribution,
    )
    publish_to_jira(summary, meta)


def _dry_run():
    """Sanity-check the new parsing/grouping functions against real,
    already-generated Surefire/JaCoCo reports without calling any live
    Jira/Confluence API. Prints the computed tables to stdout.
    """
    reports_dir = os.environ.get("SUREFIRE_REPORTS_DIR", "target/surefire-reports")
    jacoco_path = os.environ.get("JACOCO_REPORT_PATH", "target/site/jacoco/jacoco.xml")
    test_src_dir = os.environ.get("TEST_SRC_DIR", "src/test/java")

    summary = parse_surefire_reports(reports_dir)
    print(f"Overall: {summary['tests']} tests, {summary['passed']} passed, "
          f"{summary['failed']} failed, {summary['errors']} errors, {summary['skipped']} skipped, "
          f"{pass_rate(summary)}% pass rate")

    category_summaries = build_category_summaries(summary)
    for key, title in TEST_CATEGORY_LABELS.items():
        cat = category_summaries[key]
        print(f"[{title}] {cat}  pass_rate={pass_rate(cat)}%")

    coverage = parse_jacoco_report(jacoco_path)
    print(f"Coverage overall: line={coverage['line']}% branch={coverage['branch']}%")
    for label, pct in coverage["components"].items():
        print(f"  {label}: {pct}%" if pct is not None else f"  {label}: N/A")

    tagged = find_security_tagged_tests(test_src_dir)
    print(f"Security-tagged tests found in source: {len(tagged)}")
    security_controls = compute_security_controls_summary(summary, tagged)
    print(f"Security controls summary: {security_controls}")

    density_rows, global_density = build_hallazgos_density()
    print(f"Hallazgos density by component: {density_rows}  global={global_density}")

    distribution_rows = build_hallazgos_distribution()
    print(f"Hallazgos distribution by subcaracteristica: {distribution_rows}")

    # Verify the append -> replace marker logic doesn't duplicate content.
    fragment = build_confluence_fragment_html(
        summary, {"timestamp": "t", "commit_short": "abc1234", "run_url": ""},
        coverage, category_summaries, security_controls, (density_rows, global_density),
        distribution_rows,
    )
    fake_page = "<p>Contenido preexistente del equipo</p>"
    once = apply_confluence_fragment(fake_page, fragment)
    twice = apply_confluence_fragment(once, fragment)
    print(f"Marker check: first-apply length={len(once)}, second-apply length={len(twice)}, "
          f"idempotent={len(once) == len(twice)}, "
          f"marker_count_after_twice={twice.count(CONFLUENCE_FRAGMENT_START)}, "
          f"preexisting_content_preserved={'Contenido preexistente del equipo' in twice}")


if __name__ == "__main__":
    if "--dry-run" in sys.argv:
        _dry_run()
    else:
        main()
