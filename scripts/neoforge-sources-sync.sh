#!/usr/bin/env bash
#
# neoforge-sources-sync.sh
#   Populates .neoforge-ref/ with reference sources matching the versions
#   pinned in gradle.properties, for cross-referencing modloader/vanilla APIs:
#
#     .neoforge-ref/sources-<neo_version>/  decompiled .java -- net/minecraft
#                                           (patched) + net/neoforged (framework)
#     .neoforge-ref/vanilla-<mc_version>/   pure vanilla MC jar (deobfuscated
#                                           bytecode; inspect with `javap`)
#
# Run this after bumping neo_version / minecraft_version in gradle.properties
# (and after a Gradle sync, so ModDevGradle has regenerated its artifacts).
# Stale version dirs are pruned. Re-running is cheap: up-to-date dirs are kept
# unless --force is passed.
#
# Env overrides: GRADLE_USER_HOME, MINECRAFT_DIR.

set -uo pipefail

force=0
[ "${1:-}" = "--force" ] && force=1

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(dirname "$script_dir")"
props="$project_dir/gradle.properties"
ref_dir="$project_dir/.neoforge-ref"

die() { echo "ERROR: $*" >&2; exit 1; }

[ -f "$props" ] || die "gradle.properties not found at $props"
get_prop() {
    grep -E "^[[:space:]]*$1[[:space:]]*=" "$props" | head -n1 | sed 's/^[^=]*=//; s/[[:space:]]//g'
}
neo_version="$(get_prop neo_version)"
mc_version="$(get_prop minecraft_version)"
[ -n "$neo_version" ] || die "neo_version not found in gradle.properties"
[ -n "$mc_version" ] || die "minecraft_version not found in gradle.properties"
echo "Pinned: NeoForge=$neo_version  Minecraft=$mc_version"

sources_dir="$ref_dir/sources-$neo_version"
vanilla_dir="$ref_dir/vanilla-$mc_version"
mkdir -p "$ref_dir"

# -- Trees 1 + 2: decompiled sources (patched Minecraft + NeoForge framework) --
[ "$force" = 1 ] && rm -rf "$sources_dir"
if [ -d "$sources_dir" ]; then
    echo "[sources] sources-$neo_version/ already present (use --force to rebuild)"
else
    patched_jar="$project_dir/build/moddev/artifacts/minecraft-patched-$neo_version-sources.jar"
    gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
    nf_base="$gradle_home/caches/modules-2/files-2.1/net.neoforged/neoforge/$neo_version"
    nf_jar="$(find "$nf_base" -name "neoforge-$neo_version-sources.jar" 2>/dev/null | head -n1)"

    [ -f "$patched_jar" ] || die "patched-Minecraft sources jar missing:
    $patched_jar
  Run a Gradle sync first (./gradlew compileJava) so ModDevGradle regenerates it."
    [ -n "$nf_jar" ] || die "NeoForge framework sources jar missing under:
    $nf_base
  Run a Gradle sync first so the dependency is resolved."

    echo "[sources] extracting -> $sources_dir"
    tmp="$sources_dir.partial"
    rm -rf "$tmp"
    mkdir -p "$tmp"
    # net/minecraft (patched) and net/neoforged (framework) share one tree -- they
    # do not collide. Extract fully, then keep only .java: the jars also bundle
    # assets/data resources, and extracting everything sidesteps unzip's
    # include-pattern wildcard quirks.
    unzip -qo "$patched_jar" -d "$tmp" || true
    unzip -qo "$nf_jar" -d "$tmp" || true
    find "$tmp" -type f ! -name '*.java' -delete
    find "$tmp" -type d -empty -delete
    count="$(find "$tmp" -name '*.java' | wc -l)"
    [ "$count" -gt 0 ] || { rm -rf "$tmp"; die "extraction produced no .java files (corrupt jars?)"; }
    mv "$tmp" "$sources_dir"
    echo "[sources]   $count .java files"
fi

# -- Tree 3: pure vanilla Minecraft jar (deobfuscated bytecode) --
[ "$force" = 1 ] && rm -rf "$vanilla_dir"
if [ -d "$vanilla_dir" ]; then
    echo "[vanilla] vanilla-$mc_version/ already present (use --force to rebuild)"
else
    if [ -n "${MINECRAFT_DIR:-}" ]; then
        mc_root="$MINECRAFT_DIR"
    elif [ -n "${APPDATA:-}" ]; then
        mc_root="$(cygpath -u "$APPDATA" 2>/dev/null || echo "$APPDATA")/.minecraft"
    else
        mc_root="$HOME/AppData/Roaming/.minecraft"
    fi
    vanilla_jar="$mc_root/versions/$mc_version/$mc_version.jar"
    if [ -f "$vanilla_jar" ]; then
        echo "[vanilla] copying -> $vanilla_dir"
        mkdir -p "$vanilla_dir"
        cp "$vanilla_jar" "$vanilla_dir/"
        echo "[vanilla]   $mc_version.jar ($(du -h "$vanilla_jar" | cut -f1))"
    else
        echo "[vanilla] SKIPPED -- vanilla jar not found:"
        echo "[vanilla]   $vanilla_jar"
        echo "[vanilla]   Launch Minecraft $mc_version via the official launcher once, or set MINECRAFT_DIR."
    fi
fi

# -- Prune stale version dirs (anything not matching the current pins) --
for d in "$ref_dir"/sources-* "$ref_dir"/vanilla-*; do
    [ -d "$d" ] || continue
    name="$(basename "$d")"
    if [ "$name" != "sources-$neo_version" ] && [ "$name" != "vanilla-$mc_version" ]; then
        echo "[prune] removing stale $name"
        rm -rf "$d"
    fi
done

echo "Done. Reference sources under $ref_dir :"
echo "  decompiled .java (grep/read here): $sources_dir"
echo "  vanilla bytecode (javap here):     $vanilla_dir"
