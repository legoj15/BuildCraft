#!/usr/bin/env bash
#
# port-sweep.sh -- re-runnable javac error sweep for one Stonecutter node.
#
#   For Minecraft/NeoForge port work: compiles a node with javac's error and warning caps lifted
#   (see maxerrs.init.gradle), keeps going past the first failure, and turns the raw Gradle log into
#   numbers an agent can act on: total errors, total warnings, errors per file, and a compact
#   "file:line: message" list.
#
# Usage:
#   scripts/port-sweep/port-sweep.sh <node> <compileJava|compileTestJava> [extra gradle args, e.g. -x :26.3:compileJava]
#
# Examples:
#   scripts/port-sweep/port-sweep.sh 26.3 compileJava
#   scripts/port-sweep/port-sweep.sh 26.3 compileTestJava -x :26.3:compileJava   # test view without main
#
# Output (all under build/port-sweep/, gitignored):
#   <node>-<task>.log            full gradle output (first line is the exact command)
#   <node>-<task>-errors.txt     one "file:line: message" per error (file relative to the source root,
#                                "cannot find symbol" lines carry the missing symbol)
#   <node>-<task>-warnings.txt   same format for warnings
#   <node>-<task>-summary.txt    exactly what this script prints to stdout
#
# Exit status: 0 = compiled clean, 1 = javac diagnostics with errors, 2 = gradle failed without any
# javac error (configuration/download/toolchain trouble; read the log), 64 = bad usage.
#
# Environment: PORT_SWEEP_LINT (default "deprecation"; becomes -PbcLint=<value>, "" = off) and
# PORT_SWEEP_REPARSE=1 (skip gradle, re-parse the existing log; for iterating on this script).
#
# Rules this script bakes in (several agents build concurrently on one machine):
#   - always --no-daemon, never touches or stops other gradle daemons or java processes;
#   - never switches the Stonecutter active node (it only runs :<node>:<task>).
# Builds can exceed two minutes; run it in the background if your tool has a time limit.
#
# POSIX-ish bash + awk, runs from Git Bash on Windows. Paths stay relative to the repo root because
# gradlew is handed them by a native java.exe, which does not understand MSYS-style /e/... paths.

set -u

usage() {
    echo "usage: scripts/port-sweep/port-sweep.sh <node> <compileJava|compileTestJava> [extra gradle args, e.g. -x :<node>:compileJava]" >&2
    exit 64
}

[ $# -ge 2 ] || usage
NODE=$1
TASK=$2
shift 2

case "$TASK" in
    compileJava | compileTestJava) ;;
    *) echo "port-sweep: task must be compileJava or compileTestJava, got '$TASK'" >&2; usage ;;
esac

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT" || exit 64

if [ ! -d "versions/$NODE" ]; then
    echo "port-sweep: no such node 'versions/$NODE'. Nodes: $(ls versions | tr '\n' ' ')" >&2
    exit 64
fi

INIT=scripts/port-sweep/maxerrs.init.gradle
OUT=build/port-sweep
BASE="$OUT/$NODE-$TASK"
LOG="$BASE.log"
ERRORS="$BASE-errors.txt"
WARNINGS="$BASE-warnings.txt"
SUMMARY="$BASE-summary.txt"
mkdir -p "$OUT"

# -PbcLint (see build.gradle.kts) makes javac print each warning instead of a one-line "Note:";
# without it the warning count is always 0. Override with PORT_SWEEP_LINT=all|unchecked|...; empty disables.
LINT=${PORT_SWEEP_LINT-deprecation}
LINTARG=()
[ -z "$LINT" ] || LINTARG=("-PbcLint=$LINT")

CMD=(./gradlew --no-daemon --continue --console=plain -I "$INIT" "${LINTARG[@]}" ":$NODE:$TASK" "$@")

if [ -n "${PORT_SWEEP_REPARSE:-}" ] && [ -f "$LOG" ]; then
    echo "port-sweep: PORT_SWEEP_REPARSE set, re-parsing existing $LOG without running gradle"
else
    echo "port-sweep: ${CMD[*]}"
    echo "port-sweep: log -> $LOG (this can take a few minutes)"
    {
        echo "# ${CMD[*]}"
        "${CMD[@]}" 2>&1
        echo "# gradlew exit code: $?"
    } > "$LOG"
fi
GRADLE_RC=$(sed -n 's/^# gradlew exit code: //p' "$LOG" | tail -n 1)
[ -n "$GRADLE_RC" ] || GRADLE_RC=1

# Parse javac diagnostics. Only lines that START with a path or "error:/warning:" are diagnostics:
# gradle repeats the same text indented under "What went wrong", and those copies must not be counted.
# "cannot find symbol" is useless alone, so the following "symbol:" line is folded into the message.
awk -v errf="$ERRORS" -v warnf="$WARNINGS" '
function flush(   file, msg) {
    if (!have) return
    file = pfile; msg = pmsg
    if (psym != "") msg = msg ": " psym
    if (pkind == "error") { print file ":" pline ": " msg > errf; nerr++; perfile[file]++ }
    else {
        print file ":" pline ": " msg > warnf; nwarn++
        if (match(msg, /^\[[a-z-]+\]/)) cat[substr(msg, RSTART, RLENGTH)]++; else cat["[uncategorised]"]++
    }
    have = 0; psym = ""
}
function norm(p) {
    gsub(/\\/, "/", p)
    if (match(p, /^.*\/(main|test)\/java\//)) return substr(p, RLENGTH + 1)
    sub(/^.*\/versions\//, "versions/", p)
    return p
}
BEGIN { printf "" > errf; printf "" > warnf }
/^[ \t]/ {
    if (have && pmsg ~ /^cannot find symbol/ && psym == "" && $0 ~ /^  symbol: /) {
        s = $0; sub(/^  symbol: +/, "", s); psym = s
    }
    next
}
/^(> Task |FAILURE:|BUILD |[0-9][0-9,]* (errors?|warnings?)$)/ {
    if ($0 ~ /^[0-9][0-9,]* errors?$/) { split($0, a, " "); gsub(/,/, "", a[1]); jerr += a[1] }
    else if ($0 ~ /^[0-9][0-9,]* warnings?$/) { split($0, a, " "); gsub(/,/, "", a[1]); jwarn += a[1] }
    flush(); next
}
/\.java:[0-9]+: (error|warning): / {
    flush()
    match($0, /\.java:[0-9]+: (error|warning): /)
    ms = RSTART                              # norm() calls match() too and would clobber RSTART
    pfile = norm(substr($0, 1, ms + 4))
    rest = substr($0, ms + 6)               # "LINE: error: message"
    pline = rest; sub(/:.*$/, "", pline)
    if (rest ~ /: error: /) { pkind = "error"; sub(/^[0-9]+: error: /, "", rest) }
    else { pkind = "warning"; sub(/^[0-9]+: warning: /, "", rest) }
    pmsg = rest; have = 1; next
}
/^(error|warning): / {
    flush()
    pkind = ($0 ~ /^error/) ? "error" : "warning"
    pfile = "<global>"; pline = 0
    pmsg = $0; sub(/^(error|warning): /, "", pmsg); have = 1; next
}
END {
    flush()
    close(errf); close(warnf)
    printf "NERR=%d\nNWARN=%d\nJERR=%d\nJWARN=%d\n", nerr + 0, nwarn + 0, jerr + 0, jwarn + 0
    for (f in perfile) printf "FILE\t%d\t%s\n", perfile[f], f
    for (c in cat) printf "CAT\t%d\t%s\n", cat[c], c
}
' "$LOG" > "$BASE.parsed.tmp"

NERR=$(sed -n 's/^NERR=//p' "$BASE.parsed.tmp")
NWARN=$(sed -n 's/^NWARN=//p' "$BASE.parsed.tmp")
JERR=$(sed -n 's/^JERR=//p' "$BASE.parsed.tmp")
JWARN=$(sed -n 's/^JWARN=//p' "$BASE.parsed.tmp")

{
    echo "node=$NODE task=$TASK gradle-exit=$GRADLE_RC"
    echo "total errors:   $NERR"
    echo "total warnings: $NWARN"
    if [ "$NERR" != "$JERR" ] || [ "$NWARN" != "$JWARN" ]; then
        echo "note: javac's own summary said ${JERR} errors / ${JWARN} warnings (differs from the parsed count; check the log)"
    fi
    if [ "$NWARN" -gt 0 ]; then
        echo "warnings by category:"
        awk -F'\t' '$1 == "CAT" { printf "  %5d  %s\n", $2, $3 }' "$BASE.parsed.tmp" | sort -k1,1nr -k2
    fi
    if [ "$NERR" -gt 0 ]; then
        echo "errors per file:"
        awk -F'\t' '$1 == "FILE" { printf "  %5d  %s\n", $2, $3 }' "$BASE.parsed.tmp" | sort -k1,1nr -k2
    fi
    echo "files: $LOG | $ERRORS | $WARNINGS"
    if [ "$NERR" -eq 0 ] && [ "$GRADLE_RC" -ne 0 ]; then
        echo "WARNING: gradle failed (exit $GRADLE_RC) but javac reported no errors; this is a build/setup failure, see the end of $LOG:"
        tail -n 15 "$LOG" | sed 's/^/  | /'
    fi
} | tee "$SUMMARY"
rm -f "$BASE.parsed.tmp"

if [ "$NERR" -gt 0 ]; then exit 1; fi
if [ "$GRADLE_RC" -ne 0 ]; then exit 2; fi
exit 0
