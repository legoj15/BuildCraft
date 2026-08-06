# Licensing — the three-licence split and why the MMPL notices stay

Decision record, 2026-08-06. This closes the long-standing question *"should the 1.7.10 MMPL attribution
remain, or should it say MPL instead?"* — **it remains.** Player-facing summary lives in
[NOTICE.md](../NOTICE.md) and the README; this file is the reasoning, so nobody has to re-derive it.

## The verdict

Keep the MMPL headers exactly as they are on all 37 files. Do not change them to MPL, and do not rewrite a
file in order to shed one. What was genuinely broken was the *packaging* around them, not the attribution:
the files pointed at a licence text the repo did not carry, the jar shipped MPL and MIT but not MMPL, and
nothing anywhere stated that the jar is a three-licence artefact. Those are fixed.

## The four facts it turns on

**1. MMPL clause 6 forbids exactly this relicense, in words aimed at ports.** From
`upstream/7.1.x:LICENSE` (byte-identical to `8.0.x-1.12.2:buildcraft_resources/LICENSE.BUILDCRAFT`, and to
the copy now at [LICENSE.MMPL](../LICENSE.MMPL)):

> All distributions of this mod must remain licensed under the MMPL.
>
> Modified version of binaries and sources, as well as files containing sections copied from this mod,
> should be distributed under the terms of the present license.

A port that keeps the algorithm and swaps the platform vocabulary is a "modified version … containing
sections copied from this mod". Clause 5 (derivation rights) is the escape hatch, but it covers code that
*calls into* BuildCraft, not code that *is* BuildCraft with renamed types.

**2. This repo's MPL-2.0 root LICENSE is a fork-local act, not an inherited or upstream-sanctioned one.**

```
git log --follow -- LICENSE           →  6352b29bf "Repository cleanup finalization"
git merge-base --is-ancestor 6352b29bf upstream/7.1.x   →  not an ancestor
                                       upstream/8.0.x   →  not an ancestor
                                       upstream/master  →  not an ancestor
                                       8.0.x-1.12.2     →  not an ancestor
```

Every upstream ref still ships MMPL as its root LICENSE — `7.1.x`, `7.2.x`, `8.0.x`, `8.0.x-1.18.2`,
`master`, and `8.0.x-1.12.2` all begin `Minecraft Mod Public License`. So "the repository is MPL-2.0" is a
claim this fork made about itself; it cannot be evidence that upstream's files are MPL-2.0. This is the
decisive fact, and it points the opposite way from convenience.

Upstream's own relicensing project was real but **incomplete**: `8.0.x-1.12.2` carries four source trees —
`common/` and `src/` (relicensed, per-file MPL headers) beside `common_old_license/` and `src_old_license/`
(not relicensed). `license_checker/checker.bash` on that branch is the tool that sorted them: it tested each
file's contributor list against `license_checker/agreed.txt` (people who consented to the relicense) and
`unused_code.txt`. **Neither input file was ever committed to any ref** — so upstream's consent ledger is
unrecoverable. Do not spend time hunting for it. What survives is the *outcome*: the robotics code sat in
`src_old_license/`, i.e. on the side that could not be relicensed.

**3. The ported files carry upstream's expression, not merely its ideas.** `AIRobotLoad.java`'s
`waitedCycles` field, its increment and its `> 40` threshold sit on **identical line numbers** in our file
and `upstream/7.1.x:common/buildcraft/robotics/ai/AIRobotLoad.java` (32, 52, 54), with the same
`ANY_QUANTITY = -1`. The diff is imports and type names. That is not convergent reimplementation; it is one
file with its vocabulary substituted — which is precisely what CLAUDE.md's "the header tracks the FILE's
text" rule is about.

**4. Rewriting the thin files to shed MMPL is blocked on substance, not effort.** `RobotManager` registers
literal legacy discriminator strings that are *persistence keys* (`"resourceIdBlock"`,
`"buildcraft.core.robots.ResourceIdBlock"`); you cannot independently re-express a serialization key without
breaking every saved world. `IFluidFilter`/`IStackFilter`/`StatementSlot` are public API surface that addons
compile against — renaming is an API break, keeping the name means nothing was re-expressed. Rewriting code
purely to change a word in its header, while keeping its behaviour, is notice-stripping with extra steps.

## What was rejected

- **Relicensing any file to MPL-2.0.** Unsupported (facts 1–3). The only argument for it rests on this
  fork's own root-LICENSE swap, which is circular.
- **Re-deriving the "thin" files** to make the question go away (fact 4).
- **Touching the six `builders/snapshot/pattern` files**, including their inconsistent comment-opening style
  (`/*\n * Copyright` on `PatternBox`/`PatternFrame`, `/* Copyright` on the other four). Upstream is
  inconsistent there; matching it is the point.
- **Bumping any year, anywhere.** Preserved notices keep `2011-2015` / `2011-2017` exactly as written.
- **An SPDX `AND` expression in `neoforge.mods.toml`.** The field's only observed consumer in the decompiled
  sources is a display string, but FML's parsing side is not in `.neoforge-ref/`, so changing a required
  loader field on an unverified assumption is not worth it. A comment plus `NOTICE.md` says the same thing.
- **Treating `mod-buildcraft.com` as dead.** It is not: `http://www.mod-buildcraft.com/MMPL-1.0.txt`
  returns HTTP 200 and serves the identical MMPL 1.0.1 text. Shipping `LICENSE.MMPL` is about offline
  readability and resilience, not a broken link — say it that way.

## Two files carry two notices

`api/robots/DockingStation.java` (the four D1 station-policy methods are ours, on top of upstream's class)
and `robotics/item/ItemRedstoneBoard.java` (a new file that absorbed upstream's `"id"`-key fallback and its
`createStack`/`getBoardNBT` shape). CLAUDE.md's **Mixed** category: both notices, upstream's first.

`ItemRedstoneBoard` shipped inside the Ph4 range with upstream's notice *missing* entirely and a green test
suite — see the guard gap below.

## The guard

[CopyrightHeaderTester](../src/test/java/buildcraft/lib/misc/CopyrightHeaderTester.java) now:

- scans 14 header lines instead of 8, and records **every** notice line rather than stopping at the first,
  so a mixed file's second notice is checked too;
- cross-checks [NOTICE.md](../NOTICE.md) against the tree **in both directions** — a file that gained an
  MMPL notice but is not listed, and a listed file that has since lost its notice, both fail the build.

**The gap that remains, stated plainly:** the guard validates notices that *exist*, never notices that
*should* exist. A headerless file ported from upstream silently inherits the root MPL-2.0 default and
nothing catches it. Closing that properly needs a maintained "this file came from upstream path X"
manifest, which does not exist. Until then it is convention only — worth knowing before the next batch of
files is ported.

## Residual risk

Not a lawyer's analysis, and two links are engineering judgment rather than legal tests: (a) "identical line
numbers and constants means the expression is upstream's" is a strong heuristic, not the legal standard for
substantial similarity; (b) whether clause 6's "modified version" reaches a port across two engine
generations has never been litigated.

The verdict is robust to being wrong about either, because every action taken is preservation (costless if
the strict reading is over-cautious) or disclosure (correct under any reading). Nothing is foreclosed: if
permission to relicense is ever obtained, the headers can change then. Un-stripping a notice after shipping
releases without it is the expensive direction — which is why every genuinely uncertain file resolved toward
KEEP.

**Worth doing, not done here:** opening a courtesy issue upstream asking whether the maintainers object to a
1.7.10 robotics port carrying preserved MMPL headers in an MPL-default fork. It would either confirm this or
hand the project an explicit grant worth more than any archaeology. Nothing above depends on the answer. A
lawyer is only worth the money if the project is ever commercialised or somebody complains — for a free,
source-available fork that ships full sources alongside every jar (independently satisfying clause 6's
source-availability duty), the practical exposure is low.
