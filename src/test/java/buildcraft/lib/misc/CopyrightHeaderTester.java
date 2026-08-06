/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Guards the copyright headers on every source file in the tree.
 *
 * <p>This exists because of a failure mode no instruction file fully prevents: an agent writing a new
 * file opens a neighbouring one as a style reference and clones its header, sometimes "helpfully"
 * bumping the year. That is backwards on both counts. The 2026-07-26 audit found 278 files created
 * for this fork wearing a {@code SpaceToad and the BuildCraft team} notice — asserting authorship of
 * work he has never seen — and 149 genuinely ported files whose notice had been dropped or had its
 * year rewritten, which MPL-2.0 section 3.4 forbids.
 *
 * <p>The rule the headers follow is that a copyright notice tracks the FILE's text, not the feature's
 * idea. Copyright protects expression, never concepts, so:
 * <ul>
 * <li>a ported file keeps its upstream notice verbatim — names, years, even the old
 *     {@code mod-buildcraft.com} URL — and the year in particular is never bumped;</li>
 * <li>a file written for this fork carries {@link #OURS} and nothing else;</li>
 * <li>a new file that absorbed real code from a ported one carries both, original first.</li>
 * </ul>
 *
 * <p>Note that a missing header is NOT an error. Upstream itself leaves roughly a fifth of
 * {@code common/} bare, and inventing a notice for a file its author shipped without one would be its
 * own small fabrication. Only files that DO carry a notice are checked.
 *
 * <p>Deliberately an allowlist of exact strings rather than a year threshold. {@code 2020 SpaceToad}
 * looks anachronistic for a mod whose 1.12.2 line wound down in 2017, but it is genuine upstream text
 * and a threshold rule kept flagging it. Adding a legitimately new notice means editing
 * {@link #ALLOWED_CLAIMS} on purpose, which is exactly the friction that stops a cloned header from
 * drifting in unnoticed. */
public class CopyrightHeaderTester {

    /** The notice for code written for this fork. */
    private static final String OURS = "Copyright (c) 2026 the BuildCraftUnofficial contributors";

    /** Every copyright claim permitted to appear in the tree, normalised by {@link #normalise}.
     *
     * <p>The SpaceToad entries are the exact strings upstream shipped, recovered from
     * {@code 8.0.x-1.12.2}, {@code upstream/7.1.x} and the BuildCraftAPI submodule at the fork-point
     * commit. They are reference data, not style choices — do not tidy the punctuation or unify the
     * capitalisation of "team"/"Team", because then they would no longer match upstream. */
    private static final Set<String> ALLOWED_CLAIMS = Set.of(
        OURS,
        "Copyright (c) 2016 SpaceToad and the BuildCraft team",
        "Copyright (c) 2017 SpaceToad and the BuildCraft team",
        "Copyright (c) 2020 SpaceToad and the BuildCraft team",
        "Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team",
        "Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team",
        "Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team"
    );

    /** Upstream BuildCraft development ended well before this; any later year paired with SpaceToad's
     *  name is necessarily fabricated, whatever else the line says. */
    private static final int LAST_UPSTREAM_YEAR = 2020;

    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    /** The phrase every MMPL notice contains, in both header shapes upstream shipped (the 2011-2015 one
     *  wraps mid-phrase as "Minecraft Mod Public License 1.0", the 2011-2017 one as "Minecraft Mod Public\n
     *  * License 1.0"). Matching only these three words survives both wraps. */
    private static final String MMPL_MARKER = "Minecraft Mod Public";

    /** A source path as NOTICE.md writes them, for the stale-entry half of the cross-check. */
    private static final Pattern NOTICE_PATH = Pattern.compile("src/(?:main|test)/java/[\\w/]+\\.java");

    /** How far into a file a copyright notice may appear before we stop looking. Wide enough to reach the
     *  SECOND claim of a mixed header: {@code ItemRedstoneBoard}'s upstream notice runs six lines, putting
     *  our own line at 9. Verified that no file in the tree carries the word "copyright" on lines 9-14 for
     *  any other reason, so widening the window cannot pick up unrelated prose. */
    private static final int HEADER_SCAN_LINES = 14;

    /** Strips the comment framing and the trailing licence prose, leaving the bare claim. */
    private static String normalise(String line) {
        String s = line.replace("\r", "");
        s = s.replaceAll("^[\\s]*/?\\*+[\\s]*", "");
        s = s.replaceAll("(?i)this source code form.*", "");
        s = s.replaceAll("(?i)the buildcraft api is distributed.*", "");
        s = s.replaceAll("https?://\\S*", "");
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[\\s,]+$", "");
        return s;
    }

    /** Walks up from the working directory to the repo root. Tests run from a Stonecutter node
     *  directory ({@code versions/<id>}), not the root, so the location cannot be assumed. */
    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/buildcraft"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "could not locate the repo root from " + Paths.get("").toAbsolutePath());
    }

    private record Header(Path file, String claim) {}

    /** Every source file that carries a copyright notice, paired with its normalised claim. */
    private static List<Header> collect() {
        Path root = repoRoot();
        List<Header> found = new ArrayList<>();
        for (String set : new String[] { "src/main/java", "src/test/java" }) {
            Path base = root.resolve(set);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        int limit = Math.min(HEADER_SCAN_LINES, lines.size());
                        // EVERY notice line, not just the first: a mixed file carries two, and stopping at
                        // the first left the second completely unchecked - which is exactly how a
                        // half-merged second notice would slip through.
                        for (int i = 0; i < limit; i++) {
                            if (lines.get(i).toLowerCase().contains("copyright")) {
                                found.add(new Header(root.relativize(p), normalise(lines.get(i))));
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Assertions.assertFalse(found.isEmpty(), "walked the tree but found no copyright headers at all");
        return found;
    }

    /** The regression that motivated the audit: a new file stamped with SpaceToad's name and a
     *  present-day year, claiming he authored something written years after he stopped. */
    @Test
    public void noSpaceToadClaimAfterUpstreamEnded() {
        List<String> bad = new ArrayList<>();
        for (Header h : collect()) {
            if (!h.claim().contains("SpaceToad")) {
                continue;
            }
            Matcher m = YEAR.matcher(h.claim());
            while (m.find()) {
                if (Integer.parseInt(m.group()) > LAST_UPSTREAM_YEAR) {
                    bad.add(h.file() + "\n      " + h.claim());
                    break;
                }
            }
        }
        Assertions.assertTrue(bad.isEmpty(),
            "SpaceToad is credited with a year after upstream development ended (" + LAST_UPSTREAM_YEAR
                + "). New files belong to this fork - use:\n      " + OURS + "\n\n    "
                + String.join("\n    ", bad));
    }

    /** Catches cloned-and-mutated headers that keep a plausible year: a typo'd name, a re-flowed
     *  claim, a half-merged notice. Anything not byte-equal to a known-good claim is suspect. */
    @Test
    public void everyClaimIsRecognised() {
        List<String> bad = new ArrayList<>();
        for (Header h : collect()) {
            if (!ALLOWED_CLAIMS.contains(h.claim())) {
                bad.add(h.file() + "\n      " + h.claim());
            }
        }
        Assertions.assertTrue(bad.isEmpty(),
            "unrecognised copyright claim. A ported file must keep its upstream notice verbatim; a new"
                + " file must use:\n      " + OURS
                + "\n    If a claim below is genuinely correct, add it to ALLOWED_CLAIMS.\n\n    "
                + String.join("\n    ", bad));
    }

    /** NOTICE.md maps files to licences for anyone reading the jar or the repo, and it is the only place
     *  the three-licence split is stated in one piece. A file whose header says MMPL but which NOTICE.md
     *  does not list is a file nobody can discover the terms of without grepping the tree - and the
     *  reverse (a listed file that has since lost its notice) is a stale legal claim. Neither is
     *  detectable by reading either artefact alone, so pin them to each other.
     *
     * <p>This is the guard the 2026-08 licensing verdict identified as missing: {@link #collect} only
     * validates notices that EXIST, never notices that SHOULD exist, which is how
     * {@code ItemRedstoneBoard} shipped with upstream's notice dropped and a green test suite. */
    @Test
    public void noticeFileListsEveryMmplFile() {
        Path root = repoRoot();
        Set<String> inTree = new java.util.TreeSet<>();
        for (String set : new String[] { "src/main/java", "src/test/java" }) {
            Path base = root.resolve(set);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        String head = String.join("\n", lines.subList(0, Math.min(HEADER_SCAN_LINES, lines.size())));
                        if (head.contains(MMPL_MARKER)) {
                            inTree.add(root.relativize(p).toString().replace('\\', '/'));
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Assertions.assertFalse(inTree.isEmpty(),
            "walked the tree but found no MMPL-noticed files at all - the marker string must have drifted");

        Path notice = root.resolve("NOTICE.md");
        Assertions.assertTrue(Files.isRegularFile(notice),
            "NOTICE.md is missing. The jar ships it; it is what tells a reader which of the three licences"
                + " covers which file.");
        String noticeText;
        try {
            noticeText = Files.readString(notice, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> unlisted = new ArrayList<>();
        for (String path : inTree) {
            if (!noticeText.contains(path)) {
                unlisted.add(path);
            }
        }
        // The reverse direction: a path NOTICE.md claims is MMPL but which no longer carries the notice.
        List<String> stale = new ArrayList<>();
        Matcher m = NOTICE_PATH.matcher(noticeText);
        while (m.find()) {
            String path = m.group();
            if (!inTree.contains(path)) {
                stale.add(path);
            }
        }

        Assertions.assertTrue(unlisted.isEmpty() && stale.isEmpty(),
            "NOTICE.md is out of sync with the copyright headers in the tree.\n"
                + (unlisted.isEmpty() ? ""
                    : "    Carry an MMPL notice but are NOT listed in NOTICE.md:\n      "
                        + String.join("\n      ", unlisted) + "\n")
                + (stale.isEmpty() ? ""
                    : "    Listed in NOTICE.md but no longer carry an MMPL notice:\n      "
                        + String.join("\n      ", stale) + "\n")
                + "    Update NOTICE.md (including the file count in its prose) to match.");
    }

    /** A file may not credit this fork and upstream on the SAME line - that is a half-applied edit.
     *  Genuinely mixed files carry two separate lines, upstream's first. */
    @Test
    public void noSingleLineCreditsBothParties() {
        List<String> bad = new ArrayList<>();
        for (Header h : collect()) {
            if (h.claim().contains("SpaceToad") && h.claim().contains("BuildCraftUnofficial")) {
                bad.add(h.file() + "\n      " + h.claim());
            }
        }
        Assertions.assertTrue(bad.isEmpty(),
            "a single copyright line credits both upstream and this fork; use two lines, upstream"
                + " first:\n\n    " + String.join("\n    ", bad));
    }
}
