#!/usr/bin/env bash
#
# zcode-skills-sync.sh
#   Mirrors the tracked procedure skills (.claude/skills/) into ZCode's
#   machine-local workspace discovery root (.zcode/skills/), so ZCode sessions
#   can invoke the same add-game-test / neoforge-bump skills Claude Code reads
#   natively. ZCode does not read .claude/skills/ and Claude Code does not read
#   .zcode/skills/, so both trees must exist -- .claude/skills/ is the single
#   source of truth (tracked); .zcode/skills/ is gitignored and disposable.
#
#   Run after a fresh clone, and again after any edit to a tracked skill.
#   Skill-internal relative links (../../../src/...) resolve identically from
#   either tree, so a plain copy needs no rewriting.
#
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(dirname "$script_dir")"
src="$project_dir/.claude/skills"
dst="$project_dir/.zcode/skills"

[ -d "$src" ] || { echo "zcode-skills-sync: nothing to mirror at $src" >&2; exit 1; }

rm -rf "$dst"
mkdir -p "$dst"
cp -R "$src"/. "$dst"/

count="$(find "$dst" -name SKILL.md | wc -l)"
echo "zcode-skills-sync: mirrored $count skill(s) into .zcode/skills/"
