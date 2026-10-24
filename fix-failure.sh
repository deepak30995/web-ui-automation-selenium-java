#!/bin/bash
#
# Propose a fix for the most recent test failure.
#
# Runs the diagnosis first and acts ONLY on a STALE_LOCATOR verdict - the single
# category where changing our code is the right answer. Anything else exits without
# touching the repository and tells you what to look at instead.
#
# When it does act it fixes, re-runs the failing test, and retries up to
# FIX_MAX_ATTEMPTS times if the test still fails. A pull request is opened only once
# the test passes. Output is always a pull request, never a merge.
#
# Usage:  ./fix-failure.sh [path/to/bundle.json]
# Env:    FIX_MAX_ATTEMPTS (default 3)

set -uo pipefail

FAILURES_DIR="test-output/failures"
MAX_ATTEMPTS="${FIX_MAX_ATTEMPTS:-3}"

BUNDLE="${1:-}"
if [ -z "$BUNDLE" ]; then
    BUNDLE=$(ls -t "$FAILURES_DIR"/*.json 2>/dev/null | head -1 || true)
fi

if [ -z "$BUNDLE" ] || [ ! -f "$BUNDLE" ]; then
    echo "No failure bundle found in $FAILURES_DIR"
    echo "Run the tests first - a bundle is written only when a test fails."
    exit 1
fi

DIAGNOSIS="${BUNDLE%.json}_diagnosis.md"

# --- 1. Diagnose (read-only) -------------------------------------------------
if [ ! -f "$DIAGNOSIS" ]; then
    echo "No diagnosis yet - running analyze-failure.sh"
    ./analyze-failure.sh "$BUNDLE" > /dev/null
fi

if [ ! -f "$DIAGNOSIS" ]; then
    echo "Diagnosis could not be produced - aborting."
    exit 1
fi

VERDICT=$(grep -A3 '^## Verdict' "$DIAGNOSIS" | grep -v '^## Verdict' | grep -v '^\s*$' | head -1)
echo "Verdict: $VERDICT"
echo ""

# --- 2. Gate -----------------------------------------------------------------
# Only a stale locator justifies an automated code change. Timing, environment,
# test data and product bugs all need a human decision; "fixing" them in the test
# produces a passing test that verifies the wrong thing.
if ! echo "$VERDICT" | grep -qi "STALE_LOCATOR"; then
    echo "No automated fix attempted."
    echo "Only a STALE_LOCATOR verdict is auto-fixable; this one needs a human."
    echo ""
    echo "Full diagnosis: $DIAGNOSIS"
    exit 0
fi

# --- 3. Guard the working tree ----------------------------------------------
if [ -n "$(git status --porcelain -- src)" ]; then
    echo "Uncommitted changes under src/ - commit or stash them first so the"
    echo "proposed fix lands on a clean tree and is easy to review."
    exit 1
fi

# Which test to re-run. Taken from the bundle so an unrelated failure elsewhere
# cannot block a good fix.
test_target_from() {
    python3 - "$1" <<'PYEOF'
import json, sys, re
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(0)
for frame in d.get("failure", {}).get("projectStackFrames", []):
    m = re.match(r'(tests\.[A-Za-z0-9_]+)\.([A-Za-z0-9_]+)\(', frame)
    if m:
        print("%s#%s" % (m.group(1).split('.')[-1], m.group(2)))
        break
PYEOF
}

TEST_TARGET=$(test_target_from "$BUNDLE")
if [ -n "$TEST_TARGET" ]; then
    VERIFY_CMD=(mvn -B clean test "-Dtest=$TEST_TARGET")
    echo "Will verify by re-running $TEST_TARGET"
else
    VERIFY_CMD=(mvn -B clean test)
    echo "Could not identify the failing test - will verify with the full suite"
fi

BRANCH="autofix/$(basename "${BUNDLE%.json}" | tr '[:upper:]' '[:lower:]')"
git checkout -b "$BRANCH" >/dev/null 2>&1 || git checkout "$BRANCH" >/dev/null 2>&1
echo "Working on branch $BRANCH"

PROMPT_FILE=$(mktemp)
trap 'rm -f "$PROMPT_FILE" verify.log' EXIT

build_prompt() {
    # $1 = attempt number, $2 = evidence bundle, $3 = previous failure output (may be empty)
    cat > "$PROMPT_FILE" <<'PROMPT_END'
A UI test failed and the failure has been diagnosed as a stale locator.

Diagnosis: __DIAGNOSIS__
Failure bundle: __BUNDLE__
Attempt __ATTEMPT__ of __MAX__.

__RETRY_CONTEXT__

Apply ONLY the locator change the evidence supports. Specifically:

- Change the single @FindBy locator identified, in the page object it belongs to.
- Verify the replacement element exists in the captured page source referenced by
  the bundle, and that it carries the same meaning as the original.
- Prefer stable locators: id, then data-* attributes, then a semantic class. Never
  a Tailwind utility string, a hashed CSS-module class, or a deep positional XPath.

Do NOT:
- change any assertion, expected value or test data
- change waits, timeouts or configuration
- touch more than the one locator
- refactor anything, however tempting

If the captured DOM does not clearly contain a replacement element with the same
meaning, make NO change at all and say so. A wrong locator produces a passing test
that verifies the wrong thing, which is worse than a failing one.

Finish with a one-paragraph summary of exactly what you changed and why.
PROMPT_END

    local retry_context=""
    if [ -n "$3" ]; then
        retry_context="Your previous attempt did NOT make the test pass. The test was re-run and
failed again. Read the newer failure bundle above - it reflects the state after
your change, so the DOM it captured is current. Reconsider: if your replacement
locator was wrong, correct it; if the evidence does not support any locator
change, revert to the original and say so rather than guessing again.

Tail of the failed verification run:
$3"
    fi

    python3 - "$PROMPT_FILE" "$DIAGNOSIS" "$2" "$1" "$MAX_ATTEMPTS" "$retry_context" <<'PYEOF'
import sys, io
path, diagnosis, bundle, attempt, mx, retry = sys.argv[1:7]
s = io.open(path, encoding='utf-8').read()
s = (s.replace('__DIAGNOSIS__', diagnosis)
      .replace('__BUNDLE__', bundle)
      .replace('__ATTEMPT__', attempt)
      .replace('__MAX__', mx)
      .replace('__RETRY_CONTEXT__', retry))
io.open(path, 'w', encoding='utf-8').write(s)
PYEOF
}

# --- 4. Fix, verify, retry ---------------------------------------------------
EVIDENCE="$BUNDLE"
PREV_FAILURE=""
PASSED=false

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    echo ""
    echo "=============================================="
    echo " Attempt $attempt of $MAX_ATTEMPTS"
    echo "=============================================="

    build_prompt "$attempt" "$EVIDENCE" "$PREV_FAILURE"
    claude -p "$(cat "$PROMPT_FILE")" --allowedTools "Read,Grep,Glob,Edit,Bash(mvn:*)" | tee autofix-summary.md

    if [ -z "$(git status --porcelain -- src)" ]; then
        echo ""
        echo "No change was made - the captured DOM did not support a confident fix."
        break
    fi

    echo ""
    echo "Verifying: ${VERIFY_CMD[*]}"
    if "${VERIFY_CMD[@]}" > verify.log 2>&1; then
        echo "Verified: the test now passes."
        PASSED=true
        break
    fi

    echo "Still failing after attempt $attempt."
    PREV_FAILURE=$(tail -25 verify.log)

    # The failed run wrote a fresh bundle reflecting the current state - use it
    NEWEST=$(ls -t "$FAILURES_DIR"/*.json 2>/dev/null | head -1 || true)
    if [ -n "$NEWEST" ] && [ "$NEWEST" != "$EVIDENCE" ]; then
        EVIDENCE="$NEWEST"
        echo "Newer evidence captured: $(basename "$EVIDENCE")"
    fi
done

# --- 5. Give up cleanly if it never passed -----------------------------------
if [ "$PASSED" != true ]; then
    echo ""
    echo "No pull request opened - the test does not pass after $MAX_ATTEMPTS attempt(s)."
    if [ -n "$(git status --porcelain -- src)" ]; then
        echo "The attempted changes are left on branch $BRANCH for inspection:"
        git --no-pager diff --stat -- src | sed 's/^/    /'
        echo ""
        echo "Re-run the test yourself (the site is sometimes simply slow):"
        echo "    ${VERIFY_CMD[*]}"
        echo "Or discard:"
        echo "    git checkout - && git branch -D $BRANCH"
    else
        git checkout - >/dev/null 2>&1
        git branch -D "$BRANCH" >/dev/null 2>&1
    fi
    rm -f autofix-summary.md
    exit 1
fi

# --- 6. Open a pull request for review ---------------------------------------
git add src
git commit -q -F - <<COMMIT
fix: update stale locator identified by failure diagnosis

$(head -c 400 autofix-summary.md)

Proposed automatically from $(basename "$BUNDLE"), verified by re-running
$TEST_TARGET. Review before merging.
COMMIT

git push -u origin "$BRANCH"
gh pr create --base main \
    --title "Auto-fix: stale locator in $(basename "${BUNDLE%.json}")" \
    --body-file autofix-summary.md

rm -f autofix-summary.md
echo ""
echo "Pull request opened. Review the diff before merging."
