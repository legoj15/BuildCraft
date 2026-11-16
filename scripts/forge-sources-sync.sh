#!/usr/bin/env bash
# Populates .forge-ref/ (gitignored): MinecraftForge's own sources plus its vanilla patch diffs, one dir per Forge
# node, read from the ForgeGradle mavenizer cache. The Forge twin of neoforge-sources-sync.sh, and the first place to
# look for a Forge API (docs/forge-port.md). A node's sources jar exists only after that node has been configured once
# (`./gradlew :<node>:compileJava`); a node without one is reported and skipped.
# FML core and the event bus are separate jars and are not extracted: read those with javap from the same cache.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/minecraftforge/forgegradle/mavenizer/caches/maven/forge/net/minecraftforge/forge"
mkdir -p "$root/.forge-ref"
index="$root/.forge-ref/INDEX.txt"
echo "Forge's own sources and vanilla patches per node (scripts/forge-sources-sync.sh). Not patched vanilla." > "$index"
for props in "$root"/versions/*/gradle.properties; do
    grep -q '^loader=forge' "$props" || continue
    node="$(basename "$(dirname "$props")")"
    mc="$(sed -n 's/^minecraft_version=//p' "$props" | tr -d '\r')"
    forge="$(sed -n 's/^forge_version=//p' "$props" | tr -d '\r')"
    jar="$cache/$mc-$forge/forge-$mc-$forge-sources.jar"
    dir="$root/.forge-ref/sources-$mc-$forge"
    if [ ! -f "$jar" ]; then
        echo "$node -> (no sources jar for $mc-$forge yet: configure the node once)" | tee -a "$index"
        continue
    fi
    [ -d "$dir" ] || { mkdir -p "$dir" && unzip -qo "$jar" -d "$dir"; }
    echo "$node -> sources-$mc-$forge" | tee -a "$index"
done
