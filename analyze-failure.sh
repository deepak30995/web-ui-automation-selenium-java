#!/bin/bash
#
# Diagnose the most recent test failure using Claude Code.
#
# Reads the newest failure bundle written by utils.FailureReporter and asks Claude to
# classify the failure before proposing anything. Diagnosis only - it never edits the
# repository. Review the output, then decide what to change.
#
# Usage:  ./analyze-failure.sh [path/to/bundle.json]

set -euo pipefail

FAILURES_DIR="test-output/failures"

BUNDLE="${1:-}"
if [ -z "$BUNDLE" ]; then
    BUNDLE=$(ls -t "$FAILURES_DIR"/*.json 2>/dev/null | head -1 || true)
fi

if [ -z "$BUNDLE" ] || [ ! -f "$BUNDLE" ]; then
    echo "No failure bundle found in $FAILURES_DIR"
    echo "Run the tests first - a bundle is written only when a test fails."
    exit 1
fi

OUTPUT="${BUNDLE%.json}_diagnosis.md"

PROMPT_FILE=$(mktemp)
trap 'rm -f "$PROMPT_FILE"' EXIT

# Quoted delimiter keeps this literal; __BUNDLE__ is substituted below.
cat > "$PROMPT_FILE" <<'PROMPT_END'
You are diagnosing a failure in a Selenium + TestNG UI automation suite.

Read the failure bundle at: __BUNDLE__
Also read the page source files it references under "page" (pageSourcePath is the
top-level document; framePageSourcePath, when present, is the iframe the test was
inside when it failed).

Work through these steps IN ORDER. Do not skip ahead to a fix.

STEP 1 - Establish where the browser actually was.
Compare page.url and page.title with the page the failing step expected, using
recentLogs to see how far the flow got. If the browser was not on the expected page,
the locator is NOT the cause.

STEP 2 - Classify into exactly ONE category:
  A STALE_LOCATOR      the target element is present in the captured DOM, but under a
                       different locator than the test uses
  B TIMING             the element is present in the DOM but was not ready, visible or
                       clickable within the wait
  C TEST_DATA_OR_ENV   wrong account or role, wrong environment, unresolved config
                       placeholder, or the site was unreachable or slow
  D PRODUCT_BUG        the application genuinely misbehaved
  E UNKNOWN            the evidence does not support a confident call

STEP 3 - Justify with concrete evidence. Quote the exact bundle field or DOM snippet
you relied on. An assertion with no quoted evidence is not acceptable.

STEP 4 - Recommend.
Only for category A may you propose a replacement locator, and only when you can point
to the specific element in the captured DOM carrying the same meaning. For every other
category, state plainly that no code change is warranted and say what a human should
check instead.

HARD RULES - these exist because a wrong fix produces a passing test that verifies the
wrong thing, which is worse than a failing one:
- Never propose a locator change for categories B, C, D or E.
- Never propose changing the expected value of an assertion to match what was observed.
- If the page source shows a different application area than the test targets (for
  example an admin dashboard where a learner view was expected), that is category C,
  not a locator problem.
- Prefer stable locators (id, data-* attributes) over class lists or deep XPath.
- When evidence is thin, answer UNKNOWN. That is a useful result.

Output markdown with exactly these sections:
## Verdict
(one line: CATEGORY - one-sentence summary)
## What happened
## Evidence
## Recommendation
## Confidence
(high / medium / low, plus what extra evidence would raise it)
PROMPT_END

PROMPT=$(sed "s|__BUNDLE__|$BUNDLE|g" "$PROMPT_FILE")

echo "Diagnosing: $BUNDLE"
echo ""

claude -p "$PROMPT" --allowedTools "Read,Grep,Glob" | tee "$OUTPUT"

echo ""
echo "Diagnosis saved: $OUTPUT"
