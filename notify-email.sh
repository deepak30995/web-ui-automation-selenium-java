#!/bin/bash
#
# Emails the result of one archived run. Zips the logs, then hands off to
# notify_email.py, which assembles and sends the message.
#
#   RUN_DIR=test-output/runs/<id> RESULT=PASSED ./notify-email.sh
#
# Credentials come from the environment only, never from a file in the repo:
#   SMTP_HOST  SMTP_PORT  SMTP_USER  SMTP_PASSWORD
# Recipients come from run.config or the environment:
#   EMAIL_TO  EMAIL_FROM  EMAIL_SUBJECT_PREFIX

set -uo pipefail
cd "$(dirname "$0")" || exit 1
. ./run.config

RUN_DIR="${RUN_DIR:-}"
if [ -z "$RUN_DIR" ] || [ ! -d "$RUN_DIR" ]; then
    echo "notify-email: RUN_DIR not set or missing" >&2; exit 1
fi

# One zip rather than a dozen separate attachments
rm -f "$RUN_DIR/logs.zip"
( cd "$RUN_DIR" && zip -qr logs.zip logs failures 2>/dev/null )

export RUN_DIR RESULT="${RESULT:-UNKNOWN}"
export EMAIL_TO EMAIL_FROM EMAIL_SUBJECT_PREFIX
exec python3 -I notify_email.py
