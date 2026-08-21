#!/usr/bin/env bash
#
# neoforge-version-check.sh
#   Reports whether any Stonecutter node's pinned NeoForge build is behind the
#   latest upstream build on that node's line.
#
#   Node-aware: it enumerates every versions/<id>/gradle.properties (one node per
#   MC line) and checks each line INDEPENDENTLY. The line is derived from the
#   node's PINNED neo_version, NOT its minecraft_version, because the MC->NeoForge
#   mapping is non-uniform across the CalVer cliff:
#       MC 1.21.1  -> NeoForge 21.1.x    (drops the leading "1.")
#       MC 1.21.10 -> NeoForge 21.10.x
#       MC 26.1.2  -> NeoForge 26.1.2.x
#       MC 26.2    -> NeoForge 26.2.0.x  (inserts a ".0"; -beta suffix dropped at 26.2.0.57)
#   Deriving the line from minecraft_version only ever lines up for 26.1.2, so a
#   naive per-node loop keyed on minecraft_version would silently find "no builds"
#   for every other node and stay quiet -- the worst failure for a staleness check.
#   Stripping the trailing ".<build>" off neo_version is uniform across all lines,
#   and the resulting prefix isolates lines cleanly (^21\.1\. won't match 21.10.x).
#
#   (no args)  emits SessionStart-hook JSON on stdout, but ONLY when >=1 node is
#              behind. Silent when all current or on any error.
#   --plain    human-readable per-node status, for manual runs.
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
root_props="$project_dir/gradle.properties"

# In --plain mode, surface why a check was inconclusive; in hook mode, stay quiet.
note() { [ "$plain" = 1 ] && echo "neoforge-version-check: $*" >&2 || true; }

get_prop() {  # get_prop <file> <key>
    grep -E "^[[:space:]]*$2[[:space:]]*=" "$1" 2>/dev/null | head -n1 | sed 's/^[^=]*=//; s/[[:space:]]//g'
}

[ -f "$root_props" ] || { note "gradle.properties not found"; exit 0; }

# -- Enumerate the active (node, neo_version, mc_version) set ------------------
# Same enumeration as neoforge-sources-sync.sh: glob versions/*/gradle.properties,
# per-key fall back to root, and if there are no node dirs use a single root pin.
# minecraft_version is carried only for the human-facing "Minecraft X" label and
# changelog context -- the line comparison is driven entirely by neo_version.
declare -a node_ids node_neos node_mcs
shopt -s nullglob
node_props=("$project_dir"/versions/*/gradle.properties)
shopt -u nullglob
if [ "${#node_props[@]}" -gt 0 ]; then
    for f in "${node_props[@]}"; do
        id="$(basename "$(dirname "$f")")"
        nv="$(get_prop "$f" neo_version)";       [ -n "$nv" ] || nv="$(get_prop "$root_props" neo_version)"
        mv="$(get_prop "$f" minecraft_version)"; [ -n "$mv" ] || mv="$(get_prop "$root_props" minecraft_version)"
        [ -n "$nv" ] || { note "node $id: no neo_version; skipping"; continue; }
        node_ids+=("$id"); node_neos+=("$nv"); node_mcs+=("$mv")
    done
else
    nv="$(get_prop "$root_props" neo_version)"
    mv="$(get_prop "$root_props" minecraft_version)"
    [ -n "$nv" ] || { note "no versions/*/gradle.properties and no root neo_version"; exit 0; }
    node_ids+=("(root)"); node_neos+=("$nv"); node_mcs+=("$mv")
fi
[ "${#node_neos[@]}" -gt 0 ] || { note "no node versions found"; exit 0; }

# -- Fetch NeoForge maven-metadata ONCE (it holds every line) -----------------
meta_url="https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
xml="$(curl -fsS --max-time 10 "$meta_url" 2>/dev/null)" || { note "could not reach NeoForge maven"; exit 0; }
mapfile -t all_versions < <(
    printf '%s' "$xml" \
        | grep -oE '<version>[^<]+</version>' \
        | sed -E 's:</?version>::g'
)
[ "${#all_versions[@]}" -gt 0 ] || { note "no versions found in maven-metadata"; exit 0; }

build_of() { local v="${1##*.}"; echo "${v%-beta}"; }   # last dot-segment, minus -beta
line_of()  { echo "${1%.*}"; }                            # neo_version minus the trailing .<build>

# -- Per-node comparison ------------------------------------------------------
behind_msgs=()   # human-readable lines (--plain)
behind_ctx=()    # hook additionalContext fragments
any_behind=0

for i in "${!node_ids[@]}"; do
    id="${node_ids[$i]}"; neo="${node_neos[$i]}"; mc="${node_mcs[$i]}"
    prefix="$(line_of "$neo")"
    cur_build="$(build_of "$neo")"
    case "$cur_build" in ''|*[!0-9]*) note "node $id: unexpected neo_version '$neo'"; continue;; esac
    [ -n "$prefix" ] && [ "$prefix" != "$neo" ] || { note "node $id: could not derive a line from '$neo'"; continue; }
    prefix_re="${prefix//./\\.}"
    line_re="^${prefix_re}\.[0-9]+(-beta)?\$"

    latest="$neo"; latest_build="$cur_build"; nbehind=0; found=0
    for v in "${all_versions[@]}"; do
        [[ "$v" =~ $line_re ]] || continue
        found=1
        b="$(build_of "$v")"
        case "$b" in ''|*[!0-9]*) continue;; esac
        [ "$b" -gt "$cur_build" ] && nbehind=$((nbehind + 1))
        if [ "$b" -gt "$latest_build" ]; then latest="$v"; latest_build="$b"; fi
    done

    if [ "$found" = 0 ]; then
        note "node $id: no upstream builds on line ${prefix}.* (pinned $neo)"
        continue
    fi
    if [ "$nbehind" -eq 0 ]; then
        note "node $id ($neo): current -- latest on ${prefix}.* (Minecraft $mc)."
        continue
    fi

    any_behind=1
    changelog="https://maven.neoforged.net/releases/net/neoforged/neoforge/$latest/neoforge-$latest-changelog.txt"
    behind_msgs+=("node $id (Minecraft $mc): pinned $neo -> latest $latest ($nbehind build(s) behind); changelog $changelog")
    behind_ctx+=("$id [MC $mc]: $neo -> $latest ($nbehind behind) -- $changelog")
done

if [ "$any_behind" = 0 ]; then
    note "All nodes current."
    exit 0
fi

if [ "$plain" = 1 ]; then
    echo "NeoForge updates available:"
    for m in "${behind_msgs[@]}"; do echo "  $m"; done
    exit 0
fi

# Hook mode: one JSON object listing every behind node. Interpolated values match
# strict version/numeric regexes plus a maven URL (no JSON-special chars), and
# fragments are joined with the two-char sequence \n -- a valid JSON escape that
# renders as a newline (printf's %s does not expand it, so the backslash survives).
ctx="NeoForge version check: one or more Stonecutter nodes are behind upstream."
for frag in "${behind_ctx[@]}"; do
    ctx="$ctx\\n  - $frag"
done
ctx="$ctx\\nFor each node: read the cumulative changelog entries above the pinned build, cross-reference todos.md, classify the delta (neutral / beneficial / cautionary), and offer a per-node bump (edit that node's versions/<id>/gradle.properties -> neo_version). See CLAUDE.md -> 'NeoForge version tracking'."
printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$ctx"
exit 0
