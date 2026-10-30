#!/bin/bash
#
# Background watcher for test failures.
#
# Polls the failures directory and runs the diagnosis as soon as a new bundle
# appears, so you do not have to remember to run it yourself.
#
# Diagnosis only by default. Pass --fix to also attempt a repair, which still goes
# through the STALE_LOCATOR gate and still ends at a pull request, never a merge.
#
#   ./watch-failures.sh start [--fix]   run detached
#   ./watch-failures.sh run   [--fix]   run in the foreground (Ctrl-C to stop)
#   ./watch-failures.sh stop            stop the detached watcher
#   ./watch-failures.sh status          is it running, and what has it handled
#
# Env:
#   WATCH_INTERVAL   seconds between polls (default 10)

set -uo pipefail

FAILURES_DIR="test-output/failures"
STATE_FILE=".watcher-processed"
PID_FILE=".watcher.pid"
LOG_FILE="test-output/watcher.log"
INTERVAL="${WATCH_INTERVAL:-10}"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

# One pass over the directory. Bundles already handled are skipped, so a watcher
# started after a failing run does not re-diagnose history.
poll_once() {
    local do_fix="$1"
    [ -d "$FAILURES_DIR" ] || return 0

    for bundle in "$FAILURES_DIR"/*.json; do
        [ -e "$bundle" ] || continue
        local name
        name=$(basename "$bundle")

        if grep -Fxq "$name" "$STATE_FILE" 2>/dev/null; then
            continue
        fi

        # Record before acting: a crash mid-diagnosis must not cause a retry loop
        echo "$name" >> "$STATE_FILE"
        log "New failure bundle: $name"

        if [ "$do_fix" = true ]; then
            log "Running fix-failure.sh (gated - only STALE_LOCATOR proceeds)"
            ./fix-failure.sh "$bundle" 2>&1 | sed 's/^/    /'
        else
            log "Running analyze-failure.sh"
            ./analyze-failure.sh "$bundle" > /dev/null 2>&1
            local diagnosis="${bundle%.json}_diagnosis.md"
            if [ -f "$diagnosis" ]; then
                local verdict
                verdict=$(grep -A3 '^## Verdict' "$diagnosis" | grep -v '^## Verdict' | grep -v '^\s*$' | head -1)
                log "Verdict: $verdict"
                log "Full diagnosis: $diagnosis"
            else
                log "Diagnosis failed - check that the claude CLI is authenticated"
            fi
        fi
    done
}

watch_loop() {
    local do_fix="$1"
    log "Watching $FAILURES_DIR every ${INTERVAL}s (fix=$do_fix)"
    # Seed the state file so pre-existing bundles are not re-diagnosed on startup
    if [ ! -f "$STATE_FILE" ] && [ -d "$FAILURES_DIR" ]; then
        for bundle in "$FAILURES_DIR"/*.json; do
            [ -e "$bundle" ] && basename "$bundle" >> "$STATE_FILE"
        done
        log "Ignoring $(wc -l < "$STATE_FILE" | tr -d ' ') pre-existing bundle(s)"
    fi
    while true; do
        poll_once "$do_fix"
        sleep "$INTERVAL"
    done
}

cmd="${1:-status}"
do_fix=false
[ "${2:-}" = "--fix" ] && do_fix=true

case "$cmd" in
    run)
        watch_loop "$do_fix"
        ;;
    start)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "Already running (pid $(cat "$PID_FILE"))"
            exit 1
        fi
        mkdir -p "$(dirname "$LOG_FILE")"
        nohup "$0" run "${2:-}" >> "$LOG_FILE" 2>&1 &
        echo $! > "$PID_FILE"
        echo "Watcher started (pid $!), fix=$do_fix"
        echo "Log: $LOG_FILE"
        ;;
    stop)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            kill "$(cat "$PID_FILE")" && rm -f "$PID_FILE"
            echo "Watcher stopped"
        else
            rm -f "$PID_FILE"
            echo "Not running"
        fi
        ;;
    status)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "Running (pid $(cat "$PID_FILE"))"
        else
            echo "Not running"
        fi
        echo "Handled bundles: $( [ -f "$STATE_FILE" ] && wc -l < "$STATE_FILE" | tr -d ' ' || echo 0 )"
        [ -f "$LOG_FILE" ] && echo "--- last 5 log lines ---" && tail -5 "$LOG_FILE"
        ;;
    *)
        echo "Usage: $0 {start|run|stop|status} [--fix]"
        exit 1
        ;;
esac
