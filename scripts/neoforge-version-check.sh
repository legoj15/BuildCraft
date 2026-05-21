#!/usr/bin/env bash
#
# neoforge-version-check.sh
#   Reports whether the NeoForge version pinned in gradle.properties is behind
#   the latest upstream release on the same Minecraft line.
#
#   (no args)  emits SessionStart-hook JSON on stdout, but ONLY when behind.
#              Silent when current or on any error, so the hook stays quiet.
#   --plain    human-readable status on stdout, for manual runs.
#
# Wired to a SessionStart hook via .claude/settings.json (see CLAUDE.md ->
# "NeoForge version tracking"). The hook must never block a session, so every
# exit path returns 0.

plain=0
[ "${1:-}" = "--plain" ] && plain=1

# A hook must never fail the session: guarantee exit 0 no matter what.
trap 'exit 0' EXIT

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(dirname "$script_dir")"
props="$project_dir/gradle.properties"

# In --plain mode, surface why a check was inconclusive; in hook mode, stay quiet.
note() { [ "$plain" = 1 ] && echo "neoforge-version-check: $*" >&2 || true; }

[ -f "$props" ] || { note "gradle.properties not found"; exit 0; }

get_prop() {
    grep -E "^[[:space:]]*$1[[:space:]]*=" "$props" | head -n1 | sed 's/^[^=]*=//; s/[[:space:]]//g'
}
neo_version="$(get_prop neo_version)"
mc_version="$(get_prop minecraft_version)"
[ -n "$neo_version" ] && [ -n "$mc_version" ] || { note "could not read versions from gradle.properties"; exit 0; }

meta_url="https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
xml="$(curl -fsS --max-time 10 "$meta_url" 2>/dev/null)" || { note "could not reach NeoForge maven"; exit 0; }

# NeoForge builds for this Minecraft line look like <minecraft_version>.<build>[-beta].
# Filtering on the minecraft_version prefix excludes other branches (e.g. the 21.1.x LTS line).
mc_re="${mc_version//./\\.}"
mapfile -t line_versions < <(
    printf '%s' "$xml" \
        | grep -oE '<version>[^<]+</version>' \
        | sed -E 's:</?version>::g' \
        | grep -E "^${mc_re}\.[0-9]+(-beta)?$"
)
[ "${#line_versions[@]}" -gt 0 ] || { note "no NeoForge builds found for Minecraft $mc_version"; exit 0; }

build_of() { local v="${1##*.}"; echo "${v%-beta}"; }
cur_build="$(build_of "$neo_version")"
case "$cur_build" in ''|*[!0-9]*) note "unexpected neo_version format: $neo_version"; exit 0;; esac

latest="$neo_version"
latest_build="$cur_build"
behind=0
for v in "${line_versions[@]}"; do
    b="$(build_of "$v")"
    case "$b" in ''|*[!0-9]*) continue;; esac
    [ "$b" -gt "$cur_build" ] && behind=$((behind + 1))
    if [ "$b" -gt "$latest_build" ]; then
        latest="$v"
        latest_build="$b"
    fi
done

if [ "$behind" -eq 0 ]; then
    note "NeoForge $neo_version is current (latest for Minecraft $mc_version)."
    exit 0
fi

changelog="https://maven.neoforged.net/releases/net/neoforged/neoforge/$latest/neoforge-$latest-changelog.txt"

if [ "$plain" = 1 ]; then
    echo "NeoForge update available:"
    echo "  pinned: $neo_version"
    echo "  latest: $latest  ($behind build(s) behind, Minecraft $mc_version)"
    echo "  changelog: $changelog"
    exit 0
fi

# Hook mode: inject one actionable context line. The interpolated values match
# strict numeric/version regexes above, so they contain no JSON-special chars.
ctx="NeoForge version check: this project pins neo_version=$neo_version, but $latest is the latest upstream build for Minecraft $mc_version ($behind build(s) behind). Cumulative changelog: $changelog -- read the entries above $neo_version, cross-reference todos.md, classify the delta as neutral / beneficial / cautionary, and offer the user a version bump. See CLAUDE.md -> 'NeoForge version tracking'."
printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$ctx"
exit 0
