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

# An archived run directory may be passed as $1. Without it the live build output
# is reviewed, which is right for an ad-hoc `./review-run.sh` after `mvn test`.
#
# run-suite.sh always passes one, because test-output/failures/ persists between
# runs: reviewing it directly hands the lead up to five bundles that belong to
# earlier runs, and it then spends the review diagnosing bugs that were fixed days
# ago. An archived run contains only its own evidence.
RUN_DIR="${1:-}"

if [ -n "$RUN_DIR" ]; then
    [ -d "$RUN_DIR" ] || { echo "No such run directory: $RUN_DIR" >&2; exit 1; }
    SUREFIRE_DIR="$RUN_DIR/surefire-reports"
    FAILURES_DIR="$RUN_DIR/failures"
    LOG_FILE="$RUN_DIR/logs/automation.log"
    SUMMARY_FILE="$RUN_DIR/summary.txt"
else
    SUREFIRE_DIR="target/surefire-reports"
    FAILURES_DIR="test-output/failures"
    LOG_FILE="test-output/logs/automation.log"
    SUMMARY_FILE=""
fi
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

    # Without this the lead cannot tell a deliberately small smoke run from a
    # regression run that silently lost most of its tests.
    if [ -n "$SUMMARY_FILE" ] && [ -f "$SUMMARY_FILE" ]; then
        echo "### Run context"
        echo '```'
        cat "$SUMMARY_FILE"
        echo '```'
        echo ""
        echo "All evidence below belongs to this run only."
        echo ""
    fi

    # Counts, clock times and averages are computed deterministically rather than
    # left to the agent - arithmetic over XML is exactly the sort of thing an LLM
    # gets subtly wrong in a way that still reads correctly.
    echo "### Run statistics (authoritative - use these numbers verbatim)"
    echo '```'
    python3 run_stats.py "$SUREFIRE_DIR" "$LOG_FILE" 2>/dev/null \
        || echo "(statistics unavailable)"
    echo '```'
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
Produce the review in EXACTLY two sections. No others - no separate verdict or
work-order section, no preamble before section 1, and no narration about reading
files or what you are about to do.

## 1. Test report

Open with one line of plain prose: what ran and whether the result can be trusted.

Then the figures, copied verbatim from the Run statistics block above - do not
recompute, reorder or round them, and do not drop a row. Use this aligned
plain-text block, NOT a markdown table: the review is shown inside a <pre> in an
email, where a markdown table arrives as raw pipes and dashes.

```
Tests overview       N total, N passed, N failed, N skipped
Started at           as given, including the timezone
Finished at          as given, including the timezone
Total time           as given
Setup time           as given
Test time            as given
Average time/test    as given
Slowest test method  name and time
Fastest test method  name and time
Total retries        as given
Flaky test methods   as given, listing each method and its retry count
Note                 as given
```

A flaky test method is one that was retried and then **passed**. Name each with its
retry count: the totals above record it as a plain pass, so it disappears unless it is
named here. If the statistics say "none", write "none".

A method that was retried and failed anyway is **not** flaky - it is a failure, and
belongs in the failure breakdown with its retry count, not under flaky. Filing a real
defect under a heading people skim past is how it gets missed. Carry the "Retried but
failed" row through when the statistics include it.

The Note row is not optional. Total time does not equal setup + test when the suite
runs parallel="methods" - the clock is set by the slowest thread while the others run
alongside - and a reader who multiplies average by test count will otherwise conclude
the figures are wrong.

Then, if anything failed or was skipped, a short subsection per failed test giving
the reason it failed - the actual root cause from the evidence, not a restatement
of the exception name - and its category:

A STALE_LOCATOR | B TIMING | C TEST_DATA_OR_ENV | D PRODUCT_BUG | E UNKNOWN

Say who fixes it: senior-sdet, or a human (C and D are not ours to patch). If
nothing failed, write "No failures." and move on.

A test that passed only after a retry is a flake, not a success - report it here
even though the totals count it as a pass.

## 2. Suggestions

Anything worth improving in the current code: coverage gaps, dead code, fragile
locators, waits that will flake, duplication, anything that will bite later.

For each one give the file and line, what to change, and why it matters. Keep each
to a few lines. Order them most useful first.

Where a suggestion is something senior-sdet should implement, follow it with a
work order in this shape so it can be handed over directly:

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

Raise no work order for categories C, D or E - state what a human must check.

If you have nothing worth suggesting, write "No suggestions." Do not pad this
section to look thorough.
TASK
} > "$PROMPT_FILE"

echo "Lead SDET reviewing the run..."
echo ""
mkdir -p "$(dirname "$OUTPUT")"
# Written to a temp file first and promoted only on success. Piping straight to
# $OUTPUT would file whatever the agent printed - an expired-login message, say -
# as though it were the review, and run-suite.sh emails that file verbatim.
TMP_OUT=$(mktemp)
trap 'rm -f "$TMP_OUT"' EXIT

if ! claude -p "$(cat "$PROMPT_FILE")" --allowedTools "Read,Grep,Glob" | tee "$TMP_OUT"; then
    echo "" >&2
    echo "Lead SDET review failed (see the agent output above)." >&2
    echo "$OUTPUT left unchanged." >&2
    exit 1
fi

# An agent that exits 0 but says almost nothing has not reviewed anything either
if [ "$(wc -c < "$TMP_OUT" | tr -d ' ')" -lt 80 ]; then
    echo "" >&2
    echo "Lead SDET returned too little to be a review - treating as failed." >&2
    exit 1
fi

# Strip anything the agent wrote before section 1. The prompt forbids a preamble,
# but a prompt is a request, not a guarantee - and the review is emailed verbatim,
# so a stray "let me check X first" line arrives as the opening sentence of the
# report. Enforced here instead of hoping.
if grep -q '^## 1\.' "$TMP_OUT"; then
    sed -n '/^## 1\./,$p' "$TMP_OUT" > "$OUTPUT"
else
    cp "$TMP_OUT" "$OUTPUT"
fi

echo ""
echo "Review saved: $OUTPUT"
