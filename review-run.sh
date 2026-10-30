#!/bin/bash
#
# Lead SDET review of a completed test run.
#
# Run this after `mvn test`. The Lead reads the results, the failure bundles and the
# log, works out the root cause of anything that failed, and writes a work order for
# the Senior SDET to act on.
#
# Reviews every run, not only failing ones - a suite that passed only because a test
# was retried is a finding too, and the green tally hides it.
#
#   ./review-run.sh
#
# Output: test-output/run-review.md

set -uo pipefail

SUREFIRE_DIR="target/surefire-reports"
FAILURES_DIR="test-output/failures"
LOG_FILE="test-output/logs/automation.log"
OUTPUT="test-output/run-review.md"

if [ ! -d "$SUREFIRE_DIR" ]; then
    echo "No test results in $SUREFIRE_DIR - run the suite first."
    exit 1
fi

PROMPT_FILE=$(mktemp)
trap 'rm -f "$PROMPT_FILE"' EXIT

{
    # Role comes from the agent definition, so it is defined in one place
    sed '1{/^---$/!q;};1,/^---$/d' .claude/agents/lead-sdet.md
    echo ""
    echo "## Review this run"
    echo ""
    echo "### Results"
    echo '```'
    cat "$SUREFIRE_DIR"/*.txt 2>/dev/null | head -60
    echo '```'
    echo ""

    echo "### Retries"
    echo "A test that passed only after a retry is a flake, not a success."
    echo '```'
    grep -E "Retrying|giving up|Not retrying" "$LOG_FILE" 2>/dev/null | tail -20 || echo "(none)"
    echo '```'
    echo ""

    BUNDLES=$(ls -t "$FAILURES_DIR"/*.json 2>/dev/null | head -5)
    if [ -n "$BUNDLES" ]; then
        echo "### Failure bundles from this run"
        for b in $BUNDLES; do echo "- $b"; done
        echo ""
        echo "Read each bundle and the page source it references. Work out the root"
        echo "cause from the evidence - do not infer it from the exception name alone."
        echo ""
    fi

    cat <<'TASK'
Produce the review in this shape:

## Run summary
Passed / failed / retried, and whether this run can be trusted.

## Findings
One block per distinct problem - group failures that share a cause:

### Finding N: <one line>
- **Category:** A STALE_LOCATOR | B TIMING | C TEST_DATA_OR_ENV | D PRODUCT_BUG | E UNKNOWN
- **Root cause:** what actually happened, quoting the field or DOM snippet
- **Evidence:** bundle field, log line or DOM you relied on
- **Who fixes it:** senior-sdet, or a human (C and D are not ours to patch)

## Work orders
For anything senior-sdet should act on, one order each:

```
TASK:
SUITE:
FILES:
STEPS:
ASSERTION:
DATA:
CONSTRAINTS:
DONE WHEN:
```

For categories C, D and E, raise no work order. State what a human must check instead.

## Verdict
One line: is this run trustworthy, and what is the single most important thing to do
next?
TASK
} > "$PROMPT_FILE"

echo "Lead SDET reviewing the run..."
echo ""
mkdir -p "$(dirname "$OUTPUT")"
claude -p "$(cat "$PROMPT_FILE")" --allowedTools "Read,Grep,Glob" | tee "$OUTPUT"

echo ""
echo "Review saved: $OUTPUT"
