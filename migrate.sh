#!/usr/bin/env bash
# Migrates a warden.db (SQLite) into a MySQL database with the same V1 schema. Requires the
# `sqlite3` and `mysql` CLI clients on PATH. Not tested against a real MySQL instance - see
# PLAN.md Stage 4. Run --dry-run first.
set -euo pipefail

# FK-safe order - matches db/migration/{sqlite,mysql}/V1__init.sql exactly.
TABLES=(players name_history ip_history punish_template escalations punishments
        punish_revoke audit_log settings staff_notes reports tickets ticket_msgs)

SQLITE_FILE=""
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_DATABASE="warden"
MYSQL_USER="warden"
MYSQL_PASSWORD=""
DRY_RUN=0

usage() {
    cat <<EOF
Usage: $0 --sqlite-file PATH --mysql-database NAME [options]

Required:
  --sqlite-file PATH        Path to the source warden.db

Options:
  --mysql-host HOST         Default: localhost
  --mysql-port PORT         Default: 3306
  --mysql-database NAME     Default: warden
  --mysql-user USER         Default: warden
  --mysql-password PASS     Default: empty (or set MYSQL_PWD env var instead)
  --dry-run                 Only report row counts per table, write nothing
  -h, --help                Show this help
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --sqlite-file) SQLITE_FILE="$2"; shift 2 ;;
        --mysql-host) MYSQL_HOST="$2"; shift 2 ;;
        --mysql-port) MYSQL_PORT="$2"; shift 2 ;;
        --mysql-database) MYSQL_DATABASE="$2"; shift 2 ;;
        --mysql-user) MYSQL_USER="$2"; shift 2 ;;
        --mysql-password) MYSQL_PASSWORD="$2"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    esac
done

if [[ -z "$SQLITE_FILE" ]]; then
    echo "Error: --sqlite-file is required" >&2
    usage
    exit 1
fi
if [[ ! -f "$SQLITE_FILE" ]]; then
    echo "Error: $SQLITE_FILE does not exist" >&2
    exit 1
fi
for cmd in sqlite3 mysql; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Error: '$cmd' is not on PATH - install it first" >&2
        exit 1
    fi
done

MYSQL_ARGS=(-h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DATABASE")
if [[ -n "$MYSQL_PASSWORD" ]]; then
    export MYSQL_PWD="$MYSQL_PASSWORD"
fi

echo "Source: $SQLITE_FILE"
echo "Target: mysql://$MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE (user $MYSQL_USER)"
[[ $DRY_RUN -eq 1 ]] && echo "Mode: DRY RUN - nothing will be written"
echo

for table in "${TABLES[@]}"; do
    count=$(sqlite3 "$SQLITE_FILE" "SELECT COUNT(*) FROM $table;")
    if [[ $DRY_RUN -eq 1 ]]; then
        echo "  $table: $count row(s) would be migrated"
        continue
    fi
    if [[ "$count" -eq 0 ]]; then
        echo "  $table: 0 rows, skipping"
        continue
    fi
    echo -n "  $table: migrating $count row(s)... "
    # sqlite3's ".mode insert TABLE" emits ready-to-run INSERT INTO TABLE VALUES(...) statements.
    # Types are simple enough (INT/VARCHAR/TIMESTAMP/BOOLEAN as 0/1) that this round-trips cleanly
    # into MySQL as-is - verify timestamp formatting on your own data before trusting this blindly.
    sqlite3 "$SQLITE_FILE" ".mode insert $table" "SELECT * FROM $table;" | mysql "${MYSQL_ARGS[@]}"
    echo "done"
done

if [[ $DRY_RUN -eq 0 ]]; then
    echo
    echo "Migration complete. Verify row counts and a few spot-checked rows before pointing"
    echo "warden.storage.type: mysql at this database in production."
fi
