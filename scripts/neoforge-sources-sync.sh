#!/usr/bin/env bash
#
# neoforge-sources-sync.sh
#   Populates .neoforge-ref/ with reference sources for EVERY active Stonecutter
#   node's pinned versions, so the MC lines we target can be cross-referenced
#   side by side -- no re-checkout on node switch:
#
#     .neoforge-ref/sources-<neo_version>/  decompiled .java -- net/minecraft
#                                           (patched) + net/neoforged (framework)
#     .neoforge-ref/vanilla-<mc_version>/   pure vanilla MC jar (deobfuscated
#                                           bytecode; inspect with `javap`)
#     .neoforge-ref/INDEX.txt               node -> version -> dir map (generated)
#
# "Active" = the (neo_version, minecraft_version) pinned in each
# versions/<node>/gradle.properties (falling back to root gradle.properties when
# there are no node dirs). One dir-set is kept per distinct version; dir-sets for
# versions no longer pinned by ANY node are pruned. Different MC lines therefore
# coexist -- e.g. a 26.1.x node and a 1.21.11 node keep separate, clearly-named
# dirs, so you never grep one line's sources for the other's API. Grep the dir
# that matches the line you're working on (see INDEX.txt); never grep across them.
#
# Run after bumping a node's neo_version / minecraft_version or after adding a
# node (and after a Gradle sync, so ModDevGradle has regenerated its artifacts).
# Re-running is cheap: present dirs are kept unless --force is passed.
#
# Env overrides: GRADLE_USER_HOME, MINECRAFT_DIR.

set -uo pipefail

force=0
[ "${1:-}" = "--force" ] && force=1

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(dirname "$script_dir")"
ref_dir="$project_dir/.neoforge-ref"
root_props="$project_dir/gradle.properties"

die() { echo "ERROR: $*" >&2; exit 1; }
get_prop() {  # get_prop <file> <key>
    grep -E "^[[:space:]]*$2[[:space:]]*=" "$1" 2>/dev/null | head -n1 | sed 's/^[^=]*=//; s/[[:space:]]//g'
}

# -- Enumerate the active (node, neo_version, mc_version) set ------------------
declare -a node_ids node_neos node_mcs
shopt -s nullglob
node_props=("$project_dir"/versions/*/gradle.properties)
shopt -u nullglob
if [ "${#node_props[@]}" -gt 0 ]; then
    for f in "${node_props[@]}"; do
        id="$(basename "$(dirname "$f")")"
        nv="$(get_prop "$f" neo_version)";       [ -n "$nv" ] || nv="$(get_prop "$root_props" neo_version)"
        mv="$(get_prop "$f" minecraft_version)"; [ -n "$mv" ] || mv="$(get_prop "$root_props" minecraft_version)"
        if [ -z "$nv" ] || [ -z "$mv" ]; then
            echo "WARN: node $id is missing neo_version/minecraft_version; skipping" >&2
            continue
        fi
        node_ids+=("$id"); node_neos+=("$nv"); node_mcs+=("$mv")
    done
else
    # No Stonecutter nodes -> fall back to a single root pin.
    nv="$(get_prop "$root_props" neo_version)"
    mv="$(get_prop "$root_props" minecraft_version)"
    [ -n "$nv" ] && [ -n "$mv" ] || die "no versions/*/gradle.properties and no root neo_version/minecraft_version"
    node_ids+=("(root)"); node_neos+=("$nv"); node_mcs+=("$mv")
fi
[ "${#node_neos[@]}" -gt 0 ] || die "no active node versions found"

mkdir -p "$ref_dir"
echo "Active versions:"
for i in "${!node_ids[@]}"; do
    echo "  node ${node_ids[$i]} -> NeoForge=${node_neos[$i]}  Minecraft=${node_mcs[$i]}"
done

declare -A keep   # basename of a dir that must survive pruning -> 1

sync_one() {  # sync_one <neo_version> <mc_version>
    local neo_version="$1" mc_version="$2"
    local sources_dir="$ref_dir/sources-$neo_version"
    local vanilla_dir="$ref_dir/vanilla-$mc_version"
    keep["sources-$neo_version"]=1
    keep["vanilla-$mc_version"]=1

    # -- decompiled sources (patched Minecraft + NeoForge framework) --
    [ "$force" = 1 ] && rm -rf "$sources_dir"
    if [ -d "$sources_dir" ]; then
        echo "[sources] $neo_version: present (use --force to rebuild)"
    else
        local patched_jar nf_jar gradle_home nf_base
        # moddev drops the patched-MC sources jar under the node's build dir AND/OR root
        # build/moddev; search both, version-keyed by filename. The jar is named differently
        # across NeoForge lines:
        #   26.1.x -> minecraft-patched-<neo>-sources.jar   (patched MC only; NF framework
        #                                                     sources come separately from the cache)
        #   1.21.x -> neoforge-<neo>-sources.jar            (a COMBINED MC + NF sources jar)
        # Match either, preferring the explicit patched-MC name when both are present.
        patched_jar="$(find "$project_dir" -path '*moddev/artifacts*' \( -name "minecraft-patched-$neo_version-sources.jar" -o -name "neoforge-$neo_version-sources.jar" \) 2>/dev/null | head -n1)"
        gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
        nf_base="$gradle_home/caches/modules-2/files-2.1/net.neoforged/neoforge/$neo_version"
        nf_jar="$(find "$nf_base" -name "neoforge-$neo_version-sources.jar" 2>/dev/null | head -n1)"

        if [ -z "$patched_jar" ] || [ -z "$nf_jar" ]; then
            echo "[sources] $neo_version: SKIPPED -- sources jars not found (run a Gradle sync for this node first):"
            [ -z "$patched_jar" ] && echo "[sources]   missing patched-MC sources jar (./gradlew :<node>:compileJava regenerates it)"
            [ -z "$nf_jar" ]      && echo "[sources]   missing NeoForge framework sources under $nf_base"
            return
        fi

        echo "[sources] $neo_version: extracting -> $sources_dir"
        local tmp="$sources_dir.partial"
        rm -rf "$tmp"; mkdir -p "$tmp"
        # net/minecraft (patched) and net/neoforged (framework) share one tree and do not
        # collide. Extract fully, then keep only .java (the jars also bundle resources, and
        # extracting everything sidesteps unzip include-pattern quirks).
        unzip -qo "$patched_jar" -d "$tmp" || true
        unzip -qo "$nf_jar" -d "$tmp" || true
        find "$tmp" -type f ! -name '*.java' -delete
        find "$tmp" -type d -empty -delete
        local count; count="$(find "$tmp" -name '*.java' | wc -l)"
        if [ "$count" -gt 0 ]; then
            mv "$tmp" "$sources_dir"
            echo "[sources]   $count .java files"
        else
            rm -rf "$tmp"
            echo "[sources]   WARNING: extraction produced no .java files (corrupt jars?)"
        fi
    fi

    # -- pure vanilla Minecraft jar (deobfuscated bytecode) --
    [ "$force" = 1 ] && rm -rf "$vanilla_dir"
    if [ -d "$vanilla_dir" ]; then
        echo "[vanilla] $mc_version: present (use --force to rebuild)"
    else
        local mc_root vanilla_jar
        if [ -n "${MINECRAFT_DIR:-}" ]; then
            mc_root="$MINECRAFT_DIR"
        elif [ -n "${APPDATA:-}" ]; then
            mc_root="$(cygpath -u "$APPDATA" 2>/dev/null || echo "$APPDATA")/.minecraft"
        else
            mc_root="$HOME/AppData/Roaming/.minecraft"
        fi
        vanilla_jar="$mc_root/versions/$mc_version/$mc_version.jar"
        if [ -f "$vanilla_jar" ]; then
            echo "[vanilla] $mc_version: copying -> $vanilla_dir"
            mkdir -p "$vanilla_dir"
            cp "$vanilla_jar" "$vanilla_dir/"
            echo "[vanilla]   $mc_version.jar ($(du -h "$vanilla_jar" | cut -f1))"
        else
            echo "[vanilla] $mc_version: SKIPPED -- not found at $vanilla_jar"
            echo "[vanilla]   Launch Minecraft $mc_version via the official launcher once, or set MINECRAFT_DIR."
        fi
    fi
}

for i in "${!node_neos[@]}"; do
    sync_one "${node_neos[$i]}" "${node_mcs[$i]}"
done

# -- Prune dir-sets for versions no active node pins anymore -------------------
for d in "$ref_dir"/sources-* "$ref_dir"/vanilla-*; do
    [ -d "$d" ] || continue
    name="$(basename "$d")"
    if [ -z "${keep[$name]:-}" ]; then
        echo "[prune] removing orphan $name"
        rm -rf "$d"
    fi
done

# -- Generate the node -> version -> dir map ----------------------------------
{
    echo "# .neoforge-ref/ -- decompiled reference sources, one dir-set per active node."
    echo "#"
    echo "# Grep the dir-set matching the MC line you're working on. Do NOT grep across"
    echo "# versions (e.g. a 1.21.11 dir for a 26.1.2 API) -- the APIs differ by line."
    echo "# Regenerate this file and the dirs with:  bash scripts/neoforge-sources-sync.sh"
    echo "#"
    for i in "${!node_ids[@]}"; do
        printf 'node %-10s  sources-%-18s  vanilla-%s\n' "${node_ids[$i]}" "${node_neos[$i]}/" "${node_mcs[$i]}/"
    done
} > "$ref_dir/INDEX.txt"

echo "Done. Reference sources under $ref_dir (see INDEX.txt for the node -> dir map)."
