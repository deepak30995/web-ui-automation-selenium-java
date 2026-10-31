#!/bin/bash
#
# One complete scheduled run, end to end:
#
#   grid up -> tests -> archive -> prune -> agent review -> email -> exit
#
# This script is the whole pipeline. GitHub Actions, Jenkins, cron and a laptop
# all call it the same way and differ only in the environment they export, so
# moving between them is a change of caller, never a change of logic.
#
#   ./run-suite.sh                       # defaults from run.config
#   SUITE_PROFILE=smoke ./run-suite.sh   # any default can be overridden
#
# Exits with the test exit code, after every post-step has run.

set -uo pipefail
cd "$(dirname "$0")" || exit 1

# shellcheck source=run.config
. ./run.config

RUN_ID="${RUN_ID:-$(date '+%Y-%m-%d_%H-%M-%S')}"
RUN_DIR="$RUNS_DIR/$RUN_ID"
COMPOSE_FILES=(-f docker-compose.yml)
[ "$RECORD_VIDEO" = "true" ] && COMPOSE_FILES+=(-f docker-compose.video.yml)

log()  { echo "$(date '+%H:%M:%S')  $*"; }
step() { echo; echo "=== $* ==="; }

# Manage the grid only when we are the ones running it. Pointed at a shared grid
# or a cloud vendor, starting and stopping containers would be wrong.
should_manage_grid() {
    case "$MANAGE_GRID" in
        yes) return 0 ;;
        no)  return 1 ;;
        *)   [ "$TEST_ENV" = "docker" ] ;;
    esac
}

mkdir -p "$RUN_DIR"/{logs,report,videos}

log "Run $RUN_ID"
log "  profile=$SUITE_PROFILE  target=$TARGET_ENV  env=$TEST_ENV  browser=$BROWSER  headless=$HEADLESS"

# --- grid -------------------------------------------------------------------
GRID_MANAGED=false
if should_manage_grid; then
    step "Starting grid"
    GRID_MANAGED=true
    # Output is kept, not discarded: a bad image tag or an architecture mismatch
    # reports itself here, and swallowing it turns a one-line cause into a hunt.
    if ! docker compose "${COMPOSE_FILES[@]}" up -d 2>&1 | tee "$RUN_DIR/logs/grid-up.log"; then
        log "ERROR: grid failed to start - see $RUN_DIR/logs/grid-up.log"
        tail -5 "$RUN_DIR/logs/grid-up.log" | sed 's/^/    /'
        exit 1
    fi

    log "Waiting for hub (up to ${GRID_READY_TIMEOUT}s)"
    deadline=$((SECONDS + GRID_READY_TIMEOUT))
    until curl -sSf "$GRID_URL/wd/hub/status" 2>/dev/null | grep -q '"ready": *true'; do
        if [ $SECONDS -ge $deadline ]; then
            log "ERROR: hub not ready after ${GRID_READY_TIMEOUT}s"
            docker compose "${COMPOSE_FILES[@]}" logs --tail 40 > "$RUN_DIR/logs/grid.log" 2>&1
            docker compose "${COMPOSE_FILES[@]}" down >/dev/null 2>&1
            exit 1
        fi
        sleep 2
    done
    log "Hub ready"

    # Videos accumulate per session; clear first so this run's files are its own
    rm -f test-output/videos/*.mp4 2>/dev/null
fi

# --- tests ------------------------------------------------------------------
# Marker used to tell this run's failure bundles from ones already on disk
RUN_START_MARKER="$RUN_DIR/.started"
touch "$RUN_START_MARKER"

step "Running tests"
mvn -B clean test \
    "-P$SUITE_PROFILE" \
    -Dtest.env="$TEST_ENV" \
    -Dtarget.env="$TARGET_ENV" \
    -Dbrowser="$BROWSER" \
    -Dheadless="$HEADLESS" \
    2>&1 | tee "$RUN_DIR/logs/maven.log"
TEST_EXIT=${PIPESTATUS[0]}

[ "$TEST_EXIT" -eq 0 ] && RESULT=PASSED || RESULT=FAILED
log "Tests $RESULT (exit $TEST_EXIT)"

# --- archive ----------------------------------------------------------------
# Everything is kept whether the run passed or failed. A passing run is the
# baseline a later failure is read against, so discarding it loses the compare.
step "Archiving to $RUN_DIR"

if [ "$GRID_MANAGED" = true ]; then
    # Stop the recorder before copying: ffmpeg finalises the container on exit,
    # and a file copied mid-write is unplayable.
    docker compose "${COMPOSE_FILES[@]}" stop >/dev/null 2>&1
fi

cp -r target/surefire-reports "$RUN_DIR/surefire-reports" 2>/dev/null
cp test-output/logs/*.log        "$RUN_DIR/logs/"    2>/dev/null
cp test-output/videos/*.mp4      "$RUN_DIR/videos/"  2>/dev/null
# Only bundles written by THIS run. test-output/failures/ persists across runs,
# so copying it wholesale would file days-old failures under a passing run and
# send the agent and the email chasing something already fixed.
if [ -d test-output/failures ]; then
    mkdir -p "$RUN_DIR/failures"
    find test-output/failures -type f -newer "$RUN_START_MARKER" \
        -exec cp {} "$RUN_DIR/failures/" \; 2>/dev/null
    rmdir "$RUN_DIR/failures" 2>/dev/null   # leave no empty dir on a clean run
fi
rm -f "$RUN_START_MARKER"
ls -t test-output/extent-reports/*.html 2>/dev/null | head -1 \
    | xargs -I{} cp {} "$RUN_DIR/report/report.html" 2>/dev/null

TOTALS=$(grep -hoE "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+" \
         "$RUN_DIR"/surefire-reports/*.txt 2>/dev/null | tail -1)

cat > "$RUN_DIR/summary.txt" <<EOF
run id:   $RUN_ID
result:   $RESULT
profile:  $SUITE_PROFILE
target:   $TARGET_ENV
env:      $TEST_ENV
browser:  $BROWSER
totals:   ${TOTALS:-unavailable}
exit:     $TEST_EXIT
EOF

log "Archived: $(find "$RUN_DIR" -type f | wc -l | tr -d ' ') files"

if [ "$GRID_MANAGED" = true ]; then
    docker compose "${COMPOSE_FILES[@]}" down >/dev/null 2>&1
fi

# --- prune ------------------------------------------------------------------
# Per run, not per file type, so a surviving report still has its log and video.
# Ordered by modification time rather than by name, so it stays correct when a
# CI job passes its build number as RUN_ID instead of a timestamp.
COUNT=$(find "$RUNS_DIR" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')
if [ "$COUNT" -gt "$KEEP_RUNS" ]; then
    step "Pruning to newest $KEEP_RUNS runs"
    ls -dt "$RUNS_DIR"/*/ 2>/dev/null | tail -n +$((KEEP_RUNS + 1)) \
        | while read -r old; do log "  dropping $(basename "$old")"; rm -rf "$old"; done
fi

# --- agents -----------------------------------------------------------------
if [ "$REVIEW_RUN" = "true" ] && command -v claude >/dev/null 2>&1; then
    step "Lead SDET reviewing the run"
    # Written outside the run directory, then moved in. Logging straight into
    # $RUN_DIR/logs/ puts the review's own output inside the evidence the review
    # is reading, so the lead finds a half-written file containing its own
    # preamble and remarks on it - in the review the user then receives.
    REVIEW_LOG=$(mktemp)

    if ./review-run.sh "$RUN_DIR" > "$REVIEW_LOG" 2>&1; then
        cp test-output/run-review.md "$RUN_DIR/review.md"
        log "Review written"
    else
        # The email prints review.md verbatim, so a failed review must say so
        # rather than arrive looking like the agent's verdict.
        log "WARN: review failed - see $RUN_DIR/logs/review.log"
        {
            echo "Agent review unavailable."
            echo
            echo "review-run.sh did not complete for this run."
            echo "See logs/review.log in this run directory."
        } > "$RUN_DIR/review.md"
    fi

    mv "$REVIEW_LOG" "$RUN_DIR/logs/review.log"
fi

if [ "$AUTO_FIX" = "true" ] && [ "$TEST_EXIT" -ne 0 ] && command -v claude >/dev/null 2>&1; then
    step "Senior SDET attempting a fix"
    # Gated internally: acts on a STALE_LOCATOR verdict only, and opens a pull
    # request - never a merge, never a push to main.
    ./fix-failure.sh >> "$RUN_DIR/logs/autofix.log" 2>&1
fi

# --- email ------------------------------------------------------------------
if [ "$SEND_EMAIL" = "true" ]; then
    step "Emailing the report"
    RUN_DIR="$RUN_DIR" RESULT="$RESULT" ./notify-email.sh || log "WARN: email failed"
fi

step "Done"
sed 's/^/    /' "$RUN_DIR/summary.txt"
exit "$TEST_EXIT"
