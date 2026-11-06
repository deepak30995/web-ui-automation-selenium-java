"""
Builds and sends the end-of-run email: the Lead SDET's summary in the body, the
Extent report and a zip of the logs attached.

A standalone module rather than a heredoc inside the shell script, so the message
assembly can be tested without sending anything - that is where the bugs live
(a missing attachment, a mis-typed part), and it is the part that does not need
credentials to check.

Plain SMTP on purpose, not a CI-provider mail action: the same code then runs from
GitHub Actions, Jenkins, cron or a laptop.
"""

import html
import os
import pathlib
import re
import smtplib
from email.message import EmailMessage

REQUIRED = ("SMTP_HOST", "SMTP_USER", "SMTP_PASSWORD", "EMAIL_TO")


def missing_settings(env=None):
    """Names of the required settings that are unset or blank."""
    env = os.environ if env is None else env
    return [k for k in REQUIRED if not env.get(k)]


def _read(run_dir, name, default=""):
    f = run_dir / name
    return f.read_text(errors="replace") if f.is_file() else default


_PR_URL = re.compile(r"https://github\.com/[\w.-]+/[\w.-]+/pull/\d+")


def _autofix(run_dir):
    """What the Senior SDET did, and the pull request if one was opened.

    Reported because a run that ends in a PR is the one result a reader has to act
    on, and it was the only part of the pipeline the email said nothing about - the
    URL lived solely in logs/autofix.log inside the archive. The outcome is
    summarised rather than quoted: the full log is attached anyway, and the Lead's
    reasoning is already in the review section above.

    Returns (status, pr_url) or None when auto-fix did not run at all.
    """
    log = _read(run_dir, "logs/autofix.log")
    if not log.strip():
        return None

    found = _PR_URL.search(log)
    if found:
        return ("A stale locator was repaired. The fix was verified by re-running "
                "the failing test, and the Lead SDET approved the change. Nothing "
                "has been merged - review and merge it yourself.", found.group(0))

    if "Only a STALE_LOCATOR verdict is auto-fixable" in log:
        return ("No fix attempted - this failure is not a stale locator, so the "
                "repository was left untouched.", None)

    return ("A fix was attempted but no pull request was opened. The branch has "
            "been left for a human - see logs/autofix.log in the attached archive.",
            None)


def build_message(run_dir, result, env=None):
    """Assemble the email. Does no I/O beyond reading the run directory."""
    env = os.environ if env is None else env
    run_dir = pathlib.Path(run_dir)

    summary = _read(run_dir, "summary.txt", "(no summary)")
    review = _read(run_dir, "review.md",
                   "(no agent review - review step disabled or unavailable)")
    run_id = run_dir.name
    prefix = env.get("EMAIL_SUBJECT_PREFIX", "[UI Automation]")
    colour = "#1a7f37" if result == "PASSED" else "#b42318"

    # Placed above the review rather than below it: a pull request is the one thing
    # in this mail that needs the reader to do something.
    fix = _autofix(run_dir)
    fix_html = fix_text = ""
    if fix:
        status, pr_url = fix
        link = (f'<p style="margin:8px 0 0"><a href="{html.escape(pr_url)}">'
                f'{html.escape(pr_url)}</a></p>') if pr_url else ""
        fix_html = (f'<h3 style="margin:20px 0 6px">Auto-fix</h3>'
                    f'<p style="margin:0">{html.escape(status)}</p>{link}')
        fix_text = f"\n\nAuto-fix\n\n{status}" + (f"\n{pr_url}" if pr_url else "")

    body = f"""<html><body style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;
  font-size:14px;color:#1f2328;line-height:1.5">
<h2 style="margin:0 0 4px">UI Automation —
  <span style="color:{colour}">{html.escape(result)}</span></h2>
<p style="margin:0 0 16px;color:#656d76">Run {html.escape(run_id)}</p>
<pre style="background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;
  padding:12px;white-space:pre-wrap">{html.escape(summary)}</pre>
{fix_html}
<h3 style="margin:20px 0 6px">Agent review</h3>
<pre style="background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;
  padding:12px;white-space:pre-wrap">{html.escape(review)}</pre>
<p style="color:#656d76;margin-top:20px">Full Extent report and logs are attached.</p>
</body></html>"""

    msg = EmailMessage()
    msg["Subject"] = f"{prefix} {result} — {run_id}"
    msg["From"] = env.get("EMAIL_FROM") or env.get("SMTP_USER", "")
    msg["To"] = env.get("EMAIL_TO", "")
    msg.set_content(
        f"UI Automation {result} — run {run_id}\n\n{summary}{fix_text}"
        f"\n\nAgent review\n\n{review}")
    msg.add_alternative(body, subtype="html")

    for path, maintype, subtype in (
        (run_dir / "report" / "report.html", "text", "html"),
        (run_dir / "logs.zip", "application", "zip"),
    ):
        if path.is_file():
            msg.add_attachment(path.read_bytes(), maintype=maintype,
                               subtype=subtype, filename=path.name)
    return msg


def send(msg, env=None):
    env = os.environ if env is None else env
    host = env["SMTP_HOST"]
    port = int(env.get("SMTP_PORT") or 587)

    # 465 is implicit TLS; 587 and 25 negotiate upward with STARTTLS
    if port == 465:
        server = smtplib.SMTP_SSL(host, port, timeout=30)
    else:
        server = smtplib.SMTP(host, port, timeout=30)
        server.starttls()

    # Google displays an app password as four space-separated groups and people
    # paste it that way; the spaces are presentation, not part of the secret.
    password = "".join(env["SMTP_PASSWORD"].split())

    with server:
        server.login(env["SMTP_USER"], password)
        server.send_message(msg)


def main():
    run_dir = pathlib.Path(os.environ["RUN_DIR"])
    result = os.environ.get("RESULT", "UNKNOWN")

    gaps = missing_settings()
    if gaps:
        raise SystemExit("notify-email: not configured, missing: " + " ".join(gaps))

    msg = build_message(run_dir, result)
    names = [p.get_filename() for p in msg.iter_attachments()]
    send(msg)
    print(f"sent to {msg['To']} with {len(names)} attachment(s): {', '.join(names)}")


if __name__ == "__main__":
    main()
