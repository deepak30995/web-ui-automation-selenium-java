"""
Deterministic run statistics from TestNG's results XML.

Computed here rather than left to the agent: counts, clock times and averages are
arithmetic, and an agent asked to do arithmetic over XML will occasionally get it
wrong in a way that reads perfectly plausibly. The agent is given these numbers
and spends its reasoning on *why* something failed instead.

    python3 run_stats.py <surefire-dir>
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime


def _fmt(ms):
    s = ms / 1000.0
    if s < 60:
        return f"{s:.1f}s"
    return f"{int(s // 60)}m {s % 60:.1f}s"


# RetryAnalyzer logs one of these per retry, naming the method:
#   "Retrying showsErrorForIncorrectPassword (attempt 1 of 1) after TimeoutException"
_RETRY_LINE = re.compile(r"Retrying\s+(\w+)\s+\(attempt\s+(\d+)")


def _retries(root, log_file):
    """Retries per method name.

    Two independent sources, and the larger wins per method. The log is written by
    our own RetryAnalyzer so it names every retry explicitly; the XML is a
    structural cross-check, since a retried method is recorded more than once.
    Relying on the XML alone is fragile - how TestNG represents a retried attempt
    has changed between versions - and relying on the log alone misses retries if
    the log was truncated or rolled.
    """
    counts = {}

    if log_file:
        path = pathlib.Path(log_file)
        if path.is_file():
            for line in path.read_text(errors="replace").splitlines():
                m = _RETRY_LINE.search(line)
                if m:
                    # The attempt number is authoritative: a method retried twice
                    # logs "attempt 1" then "attempt 2", so the highest wins and
                    # a duplicated log line cannot inflate the count.
                    name, attempt = m.group(1), int(m.group(2))
                    counts[name] = max(counts.get(name, 0), attempt)

    seen = {}
    for m in root.iter("test-method"):
        if m.get("is-config") != "true":
            name = m.get("name")
            seen[name] = seen.get(name, 0) + 1
    for name, runs in seen.items():
        if runs > 1:
            counts[name] = max(counts.get(name, 0), runs - 1)

    return dict(sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])))


def _parse(ts):
    """TestNG writes '2026-10-31T18:31:26 IST' - seconds resolution, tz as text."""
    if not ts:
        return None
    try:
        return datetime.strptime(ts.split(" ")[0], "%Y-%m-%dT%H:%M:%S")
    except ValueError:
        return None


def _concurrency(root):
    """Peak number of methods running at the same instant.

    TestNG's XML records no thread id, so the actual thread each method ran on
    cannot be recovered. An earlier version here reconstructed "threads" by greedy
    interval packing and named a critical path - that finds *a* valid packing, not
    the real one, and on a run where setup times were even it collapsed every test
    onto one chain and reported a path that did not add up to the wall time.

    Peak concurrency needs no such guess: it is a sweep over the start and end
    times, and it is enough to explain why the durations sum past the clock.
    """
    events = []
    for m in root.iter("test-method"):
        start, end = _parse(m.get("started-at")), _parse(m.get("finished-at"))
        if start and end:
            events.append((start, 1))
            events.append((end, -1))
    if not events:
        return 1

    # Ends before starts at the same instant: a method finishing frees its slot
    events.sort(key=lambda e: (e[0], e[1]))
    peak = running = 0
    for _, delta in events:
        running += delta
        peak = max(peak, running)
    return max(peak, 1)


def _final_status(root):
    """Final outcome per method name.

    A retried method is recorded more than once, so the entries must be collapsed:
    if any attempt passed, the method passed - that is the run's actual outcome.
    """
    status = {}
    for m in root.iter("test-method"):
        if m.get("is-config") == "true":
            continue
        name, st = m.get("name"), m.get("status")
        if status.get(name) == "PASS":
            continue
        status[name] = "PASS" if st == "PASS" else status.get(name, st)
    return status


def collect(surefire_dir, log_file=None):
    results = pathlib.Path(surefire_dir) / "testng-results.xml"
    if not results.is_file():
        return None

    root = ET.parse(results).getroot()

    # Config methods (@BeforeMethod, @AfterSuite, ...) are excluded from the test
    # counts and the average: driver setup can take longer than the test itself,
    # and averaging it in makes every suite look slow.
    tests = [m for m in root.iter("test-method") if m.get("is-config") != "true"]

    # Collapse retried attempts into one entry per method, its duration being the
    # time the method consumed in total. Without this a method retried twice counts
    # as three tests: it skews the average, and the same name can come out as both
    # the slowest and the fastest attempt.
    by_name = {}
    for m in tests:
        name = m.get("name")
        entry = by_name.setdefault(name, {"name": name, "ms": 0})
        entry["ms"] += int(m.get("duration-ms", 0))
    methods = list(by_name.values())
    configs = [m for m in root.iter("test-method") if m.get("is-config") == "true"]

    suite = root.find("suite")
    wall_ms = int(suite.get("duration-ms", 0)) if suite is not None else 0
    retries = _retries(root, log_file)
    final = _final_status(root)
    durations = [m["ms"] for m in methods]
    config_ms = sum(int(m.get("duration-ms", 0)) for m in configs)

    failures = []
    seen_failures = set()
    for m in tests:
        # A retried-and-failed method appears once per attempt; report it once
        if final.get(m.get("name")) == "PASS" or m.get("name") in seen_failures:
            continue
        if m.get("status") in ("FAIL", "SKIP"):
            seen_failures.add(m.get("name"))
            exc = m.find(".//exception")
            msg = ""
            if exc is not None:
                node = exc.find("message")
                msg = " ".join((node.text or "").split()) if node is not None else ""
                if len(msg) > 300:
                    msg = msg[:300] + " ..."
            failures.append({
                "name": m.get("name"),
                "status": m.get("status"),
                "duration": int(m.get("duration-ms", 0)),
                "exception": exc.get("class") if exc is not None else "",
                "message": msg,
                "retries": retries.get(m.get("name"), 0),
            })

    return {
        "total": int(root.get("total", 0)),
        "passed": int(root.get("passed", 0)),
        "failed": int(root.get("failed", 0)),
        "skipped": int(root.get("skipped", 0)),
        "suite_name": suite.get("name") if suite is not None else "",
        "started": suite.get("started-at") if suite is not None else "",
        "finished": suite.get("finished-at") if suite is not None else "",
        "wall_ms": wall_ms,
        "test_ms": sum(durations),
        "config_ms": config_ms,
        "count": len(methods),
        "avg_ms": (sum(durations) / len(durations)) if durations else 0,
        "slowest": max(methods, key=lambda m: m["ms"]) if methods else None,
        "fastest": min(methods, key=lambda m: m["ms"]) if methods else None,
        "failures": failures,
        # Durations summing past the clock can only mean concurrent execution
        "overlapped": (sum(durations) + config_ms) > wall_ms * 1.05,
        "peak_concurrency": _concurrency(root),
        "retries": retries,
        # Flaky means "needed a retry to go green". A method that was retried and
        # failed anyway is a failure, not a flake - calling it flaky would file a
        # real defect under the heading people skim past.
        "flaky": {n: c for n, c in retries.items() if final.get(n) == "PASS"},
        "retried_failures": {n: c for n, c in retries.items()
                             if final.get(n) not in (None, "PASS")},
    }


def _reconcile(s):
    """Explain total time, because setup + test does not always equal it.

    Sequentially the three do add up, and the note says so. In parallel they
    cannot: the clock is set by the slowest chain while other threads run
    alongside, so the sums exceed wall time and look wrong without this.
    """
    if not s["overlapped"]:
        gap = s["wall_ms"] - s["test_ms"] - s["config_ms"]
        note = (f"ran sequentially - total time = setup {_fmt(s['config_ms'])} "
                f"+ test {_fmt(s['test_ms'])}")
        return note + (f" + {_fmt(gap)} suite overhead" if gap > 500 else "")

    work = _fmt(s["test_ms"] + s["config_ms"])
    return (f"ran in parallel, up to {s['peak_concurrency']} methods at once, so "
            f"setup + test is {work} of work inside {_fmt(s['wall_ms'])} of clock. "
            f"Total time is the span from first start to last finish, not the sum "
            f"of the durations.")


def render(s):
    lines = [
        f"Tests overview       {s['total']} total, {s['passed']} passed, "
        f"{s['failed']} failed, {s['skipped']} skipped",
        # TestNG writes the timezone into these, so they are reported as-is
        f"Started at           {s['started']}",
        f"Finished at          {s['finished']}",
        f"Total time           {_fmt(s['wall_ms'])}",
        f"Setup time           {_fmt(s['config_ms'])}",
        f"Test time            {_fmt(s['test_ms'])}",
        f"Average time/test    {_fmt(s['avg_ms'])}",
    ]
    for label, key in (("Slowest test method ", "slowest"),
                       ("Fastest test method ", "fastest")):
        m = s[key]
        if m is not None:
            lines.append(f"{label} {m['name']} ({_fmt(m['ms'])})")

    lines.append(f"Total retries        {sum(s['retries'].values())}")

    # A test that passed only after a retry is a flake, not a success - the totals
    # above count it as a plain pass, so it is named here or it goes unnoticed.
    flaky = s["flaky"]
    if flaky:
        lines.append(f"Flaky test methods   {len(flaky)}")
        for name, n in flaky.items():
            lines.append(f"  - {name}() ({n} retr{'y' if n == 1 else 'ies'}, "
                         f"then passed)")
    else:
        lines.append("Flaky test methods   none")

    # Listed apart from the flaky ones: these were retried and failed anyway, so
    # they are defects, not flakes.
    if s["retried_failures"]:
        lines.append(f"Retried but failed   {len(s['retried_failures'])} "
                     f"(counted as failures, not flaky)")

    lines.append(f"Note                 {_reconcile(s)}")
    lines.append("")

    if s["failures"]:
        lines.append("Failed / skipped tests:")
        for f in s["failures"]:
            retried = (f", after {f['retries']} retr"
                       f"{'y' if f['retries'] == 1 else 'ies'}") if f["retries"] else ""
            lines.append(f"  - {f['name']}  [{f['status']}, "
                         f"{_fmt(f['duration'])}{retried}]")
            if f["exception"]:
                lines.append(f"      {f['exception']}")
            if f["message"]:
                lines.append(f"      {f['message']}")
    else:
        lines.append("Failed / skipped tests: none")

    return "\n".join(lines)


if __name__ == "__main__":
    stats = collect(sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports",
                    sys.argv[2] if len(sys.argv) > 2 else None)
    if stats is None:
        print("No testng-results.xml found - statistics unavailable.")
        sys.exit(0)
    print(render(stats))
