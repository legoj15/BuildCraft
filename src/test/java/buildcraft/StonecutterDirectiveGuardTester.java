/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Fails when a Stonecutter directive arm in {@code src/} is shaped so that Stonecutter mangles it on another node.
 *
 * <p><b>The rule.</b> Stonecutter 0.9.8 (the pinned version; {@code ScopeTransformer.processCondition}, in
 * BlockTransformer.kt) decides whether an arm is "commented" from its content alone: every block in it is a comment
 * (line or block) or blank text. Then, per node: an arm the node enables that is "commented" has its comment
 * delimiters stripped (a {@code //} is removed, a block comment loses its opening and closing marks); an arm the
 * node disables that is not "commented" gets wrapped in a block comment; anything else is left alone. That is right
 * for the arm Stonecutter itself wrote, a deactivated block of code inside one block comment, and wrong for prose.
 * Three shapes break it:
 * <ul>
 * <li><b>A live arm that holds only comments.</b> {@code src/} is the ACTIVE node's text, so its enabled arms are
 *     uncommented code. One made only of comments reads as "commented", and every node that enables the arm (or
 *     the next active-node switch, when only the active node does) strips the delimiters: {@code //} prose compiles
 *     as bare text, and the code in a block comment compiles as live code. The P15 switch to 26.3 hit this twice:
 *     BlueprintPipRenderer's {@code >=26.2} arm failed {@code :26.2:compileJava}, while BCEnergyJeiPlugin's
 *     {@code >=26.3} arm, which only the active node matched, was a latent break that a reviewer caught. The fix is
 *     to put the prose above the directive, or code in the arm.</li>
 * <li><b>A {@code //} comment beside an inactive arm's block comment.</b> The arm is then several comments rather
 *     than one, and the {@code //} is stripped to bare prose the moment a node enables the arm. Prose that belongs
 *     to the arm goes INSIDE the block comment (it survives as a {@code //} line once the wrapper is stripped).</li>
 * <li><b>An inactive arm that holds code outside a block comment.</b> {@code src/} is the ACTIVE node's text, and the
 *     active node compiles it as written: bare code in a disabled arm is compiled beside the live arm (a duplicate
 *     definition if it is lucky, two branches running if it is not). Stonecutter wraps what it disables in one block
 *     comment, so any code the scan sees in an inactive arm was left unwrapped by hand. A nested directive chain in
 *     the arm counts as code. A {@code //} comment beside code is not the second hazard (an arm with code is not read
 *     as commented, so the {@code //} stays), and such an arm still has its block comments re-scanned.</li>
 * </ul>
 * Several stacked block comments in an inactive arm are fine (each is stripped to code), as is any arm with real
 * code in it that is live, whatever comments sit around that code.
 *
 * <p><b>Chains inside a block comment.</b> Stonecutter re-lexes what an enabled arm held once its block comment is
 * stripped (confirmed on a scratch project: a comment-only arm of a nested chain came out as bare prose), so a
 * directive chain written inside an inactive arm's block comment obeys the same rules there. Which of its arms
 * is live depends on the node that enables the outer arm, so the guard judges the chain as the active node would
 * see it, the state the tree's one such chain (PipePlacementHighlight) is written in. Java's block comments do not
 * nest, so Stonecutter writes the block comment of a nested inactive arm, and a doc comment inside the wrapper, as
 * {@code /^ ... ^/} (and {@code /^* ... *^/}); inside a block comment's body the scanner reads that form as a comment.
 * In a file's own text it is not Java, so there the scanner does not read it as one and it counts as code.
 *
 * <p><b>What Stonecutter processes.</b> Every file in the {@code src/} source dirs, resources included, whose
 * extension has a handler in Stonecutter's {@code Defaults.kt}; everything else (json, toml, md, txt, images) is
 * copied verbatim, which was confirmed on a scratch project. Today only {@code .java} files carry directives.
 * The Java-like handlers (java, scala, sc, groovy, gradle, json5) share this guard's scanner. The line-comment
 * handlers (shaders, hash-comment formats) deactivate an arm by prefixing its lines, so a comment-only arm is their
 * NORMAL inactive form and no text rule can tell it from prose; kotlin nests block comments. This guard does not
 * judge those, and instead fails if one of them ever carries a directive, so the gap cannot open silently.
 *
 * <p><b>Which view it reads.</b> The raw tree, via {@link TestHelper#repoRoot()}, deliberately not
 * {@code MainSourceSet} (which hands back this node's compiled view). The hazard is a property of the source text
 * relative to the transform: the generated copies have already been rewritten for their node and differ per node,
 * and the active node's generated copy is not compiled at all. The raw tree is identical on every node, so the
 * verdict is too. Which arms are live is decided against the active node, read from the {@code stonecutter active}
 * line of {@code stonecutter.gradle.kts}, whose text {@code src/} always matches.
 *
 * <p><b>What it cannot see.</b> Whether an inactive arm's block comment holds code or prose. Stonecutter wraps code in
 * exactly that shape, so a block comment holding bare prose instead of code looks right to a text scan, and the first
 * node to enable the arm is what shows it. Telling the two apart takes a Java parser: any text rule (every line ends
 * in {@code ; { } ,}, say) misreads the wrapped fragments the tree does hold, such as a lone trailing
 * {@code 0x66000000);} argument in PipePlacementHighlight, so the gap stays documented rather than closed. Conditions
 * beyond version comparisons joined by {@code &&} and {@code ||} are reported rather than guessed at, and the arms of
 * such a chain are not judged. */
public class StonecutterDirectiveGuardTester {

    /** Extensions whose Stonecutter handler is the same slash scanner and block-comment wrapping as {@code .java}. */
    private static final Set<String> JAVA_LIKE = Set.of("java", "scala", "sc", "groovy", "gradle", "json5");

    /** The other extensions Stonecutter processes (see the class doc); a directive in one of them fails the guard. */
    private static final Set<String> NOT_JUDGED = Set.of(
        "kt", "kts", "fsh", "vsh", "cfg", "aw", "accesswidener", "ct", "classtweaker", "yml", "yaml");

    /** A comment opener immediately followed by the {@code ?} that makes Stonecutter parse the body as a directive:
     *  {@code //?} and {@code /*?} for the slash handlers (kotlin and the shaders take both), {@code #?} for the hash
     *  ones. The union is checked for every unjudged format; a block opener in a hash format is inert there, and a
     *  stray hit is loud and cheap to clear. */
    private static final Pattern ANY_DIRECTIVE = Pattern.compile("(?://|#|/\\*)\\?");

    private static final Pattern ACTIVE_NODE = Pattern.compile("stonecutter\\s+active\\s+\"([^\"]+)\"");
    private static final Pattern IF = Pattern.compile("^if\\s+(.+?)\\s*\\{$");
    private static final Pattern ELIF = Pattern.compile("^\\}\\s*(?:elif|else\\s+if)\\s+(.+?)\\s*\\{$");
    private static final Pattern ELSE = Pattern.compile("^\\}\\s*else\\s*\\{$");
    private static final Pattern TERM = Pattern.compile("^(>=|<=|!=|>|<|=)\\s*(\\d+(?:\\.\\d+)*)$");

    @Test
    public void noDirectiveArmIsLeftForStonecutterToMangle() throws IOException {
        Path root = TestHelper.repoRoot();
        int[] active = activeNode(root);
        Scan scan = new Scan(active);
        int files = 0;
        try (Stream<Path> walk = Files.walk(root.resolve("src"))) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString();
                String ext = name.substring(name.lastIndexOf('.') + 1);
                String where = root.relativize(file).toString().replace('\\', '/');
                if (JAVA_LIKE.contains(ext)) {
                    files++;
                    scan.file(where, Files.readString(file, StandardCharsets.UTF_8));
                } else if (NOT_JUDGED.contains(ext) && hasDirectiveOpener(Files.readString(file, StandardCharsets.UTF_8))) {
                    scan.findings.add(where + ": a ." + ext + " file carries a Stonecutter directive, which this guard does "
                        + "not judge (see its class doc); teach the guard that format before relying on the directive");
                }
            }
        }

        // Liveness: a scan whose tokenizer lost the directives passes vacuously, so prove it saw both arm kinds.
        Assertions.assertTrue(files > 0, "walked src/ but found no source files");
        Assertions.assertTrue(scan.liveArms > 0 && scan.inactiveArms > 0,
            "expected both live and inactive directive arms in src/, saw " + scan.liveArms + " live and "
                + scan.inactiveArms + " inactive - the directive scanner must have drifted");
        Assertions.assertTrue(scan.findings.isEmpty(),
            scan.findings.size() + " Stonecutter directive arm(s) would be mangled on another node (active node "
                + versionText(active) + "):\n" + String.join("\n", scan.findings));
    }

    /** The two hazards, at the top level and one level down inside an inactive arm's block comment. */
    @Test
    public void scannerFlagsTheTwoHazards() {
        int[] active = {26, 3};

        Scan liveProse = new Scan(active);
        liveProse.file("A.java", """
            class A {
                void f() {
                    //? if >=26.3 {
                    // Nothing to enumerate here.
                    //?} elif >=1.21.10 {
                    /*int x = 1;
                    */
                    //?}
                }
            }
            """);
        Assertions.assertEquals(1, liveProse.findings.size(), liveProse.findings.toString());
        Assertions.assertTrue(liveProse.findings.get(0).startsWith("A.java:3: "), liveProse.findings.get(0));

        Scan liveBlock = new Scan(active);
        liveBlock.file("B.java", "//? if >=26.2 {\n/* a block comment is a comment too */\n//?}\n");
        Assertions.assertEquals(1, liveBlock.findings.size(), liveBlock.findings.toString());

        Scan stray = new Scan(active);
        stray.file("C.java", """
            //? if >=26.3 {
            int c = 3;
            //?} else {
            // prose between the marker and the block
            /*int c = 1;
            */
            //?}
            """);
        Assertions.assertEquals(1, stray.findings.size(), stray.findings.toString());
        Assertions.assertTrue(stray.findings.get(0).startsWith("C.java:4: "), stray.findings.get(0));

        // A chain inside an inactive arm's block comment is re-lexed and transformed when a node enables that arm
        // (confirmed on a scratch project), so both hazards apply to it too, judged as the active node would see it.
        Scan nestedLive = new Scan(active);
        nestedLive.file("D.java", """
            //? if >=26.3 {
            int d = 3;
            //?} else {
            /*
            //? if >=1.21.10 {
            // prose only
            //?} else {
            /^int d = 1;^/
            //?}
            *///?}
            """);
        Assertions.assertEquals(1, nestedLive.findings.size(), nestedLive.findings.toString());
        Assertions.assertTrue(nestedLive.findings.get(0).startsWith("D.java:5: "), nestedLive.findings.get(0));

        Scan nestedStray = new Scan(active);
        nestedStray.file("E.java", """
            //? if >=26.3 {
            int e = 3;
            //?} else {
            /*
            //? if >=26.3 {
            int e = 2;
            //?} else {
            // prose only
            //?}
            *///?}
            """);
        Assertions.assertEquals(1, nestedStray.findings.size(), nestedStray.findings.toString());
        Assertions.assertTrue(nestedStray.findings.get(0).startsWith("E.java:8: "), nestedStray.findings.get(0));

        // The tree-wide close form `*///?}` and CRLF endings (text blocks normalise to LF, so build this by hand).
        Scan crlf = new Scan(active);
        crlf.file("F.java", "//? if >=26.3 {\nint f = 3;\n//?} else {\n// prose\n/*int f = 1;\n*///?}\n"
            .replace("\n", "\r\n"));
        Assertions.assertEquals(1, crlf.findings.size(), crlf.findings.toString());
        Assertions.assertTrue(crlf.findings.get(0).startsWith("F.java:4: "), crlf.findings.get(0));
    }

    /** An inactive arm holds its code inside one block comment. Code left bare there is compiled on the active node
     *  beside the live arm, so it is reported, at the top level and one level down inside a block comment. */
    @Test
    public void scannerFlagsAnInactiveArmHoldingBareCode() {
        int[] active = {26, 3};

        Scan bare = new Scan(active);
        bare.file("P.java", """
            //? if >=26.3 {
            int p = 3;
            //?} else {
            int p = 1; // a trailing note is no extra finding
            //?}
            """);
        Assertions.assertEquals(1, bare.findings.size(), bare.findings.toString());
        Assertions.assertTrue(bare.findings.get(0).startsWith("P.java:3: "), bare.findings.get(0));

        Scan nested = new Scan(active);
        nested.file("Q.java", """
            //? if >=26.3 {
            int q = 3;
            //?} else {
            /*
            //? if >=26.3 {
            int q = 2;
            //?} else {
            int q = 1;
            //?}
            *///?}
            """);
        Assertions.assertEquals(1, nested.findings.size(), nested.findings.toString());
        Assertions.assertTrue(nested.findings.get(0).startsWith("Q.java:7: "), nested.findings.get(0));

        // An unwrapped nested chain is bare content of the arm too: Stonecutter wraps the whole arm in one comment.
        Scan chain = new Scan(active);
        chain.file("R.java", """
            //? if >=26.3 {
            int r = 3;
            //?} else {
            //? if >=1.21.10 {
            int r = 2;
            //?}
            //?}
            """);
        Assertions.assertEquals(1, chain.findings.size(), chain.findings.toString());
        Assertions.assertTrue(chain.findings.get(0).startsWith("R.java:3: "), chain.findings.get(0));
    }

    /** An inactive arm that holds bare code still has its block comments re-scanned: the code is one finding, and a
     *  nested chain in a comment beside it is judged as it would be in a sound arm. */
    @Test
    public void anInactiveArmWithBareCodeStillHasItsBlockCommentsRescanned() {
        Scan mixed = new Scan(new int[] {26, 3});
        mixed.file("S.java", """
            //? if >=26.3 {
            int s = 3;
            //?} else {
            int s = 1;
            /*
            //? if >=26.3 {
            // prose only
            //?}
            */
            //?}
            """);
        Assertions.assertEquals(2, mixed.findings.size(), mixed.findings.toString());
        Assertions.assertTrue(mixed.findings.get(0).startsWith("S.java:3: "), mixed.findings.get(0));
        Assertions.assertTrue(mixed.findings.get(1).startsWith("S.java:6: "), mixed.findings.get(1));
    }

    /** The false-positive guard rails: arms that look odd to a text scan but that Stonecutter handles correctly. */
    @Test
    public void scannerLeavesSoundArmsAlone() {
        int[] active = {26, 3};

        // Code with comments around it, prose INSIDE the block comment, stacked blocks, empty arms, a nested chain,
        // a version condition spanning two nodes, and directive lookalikes that are not tokens.
        Scan sound = new Scan(active);
        sound.file("G.java", """
            class G {
                String s = "//? if >=26.3 {\\n// prose\\n//?}";
                String t = \"""
                    //? if >=26.3 {
                    // prose
                    //?}
                    \""";
                /** The {@code //? if >=26.3} branch and a // comment. */
                // The //? if >=1.21.10 gates in other files.
                void f() {
                    //? if >=26.3 {
                    // prose before code
                    int a = 3;
                    // prose after code
                    //?} else {
                    /*// prose inside the block
                    int a = 1;
                    */
                    //?}
                    //? if >=1.21.10 && <26.3 {
                    /*int b = 1;*/
                    /*int c = 2;*/
                    //?} elif >=26.3 {
                    //?} else {
                    /*int b = 0;
                    */
                    //?}
                    //? if >=26.3 {
                    //? if <26.2 {
                    /*int e = 1;
                    */
                    //?} else {
                    int e = 2;
                    //?}
                    //?}
                }
            }
            """);
        Assertions.assertEquals(List.of(), sound.findings);
        Assertions.assertTrue(sound.liveArms > 0 && sound.inactiveArms > 0);

        // The one nested shape in the tree (PipePlacementHighlight): inside the inactive arm's block comment, code in
        // the arm the active node would take and a /^ ^/ nested comment in the other.
        Scan nested = new Scan(active);
        nested.file("H.java", """
            //? if >=26.3 {
            int h = 3;
            //?} else {
            /*
            // prose in the wrapper, outside any nested arm
            //? if >=1.21.11 {
            int h = 2;
            //?} else {
            /^int h = 1;^/
            //?}
            *///?}
            """);
        Assertions.assertEquals(List.of(), nested.findings);
        Assertions.assertEquals(2, nested.liveArms, "the outer live arm and the nested one");
        Assertions.assertEquals(2, nested.inactiveArms, "the outer inactive arm and the nested one");

        // The same inside a wrapper, with the doc comment form (/^* ... *^/) beside the code form: every nested
        // block comment is a comment, not the bare code a /^ ^/ would look like to a scan that did not know it.
        Scan nestedDoc = new Scan(active);
        nestedDoc.file("H2.java", """
            //? if >=26.3 {
            int w = 3;
            //?} else {
            /*
            //? if >=1.21.11 {
            int w = 2;
            //?} else {
            /^* A doc comment. *^/
            /^int w = 1;^/
            //?}
            *///?}
            """);
        Assertions.assertEquals(List.of(), nestedDoc.findings);

        Scan crlf = new Scan(active);
        crlf.file("I.java", "//? if >=26.3 {\nint i = 3;\n//?} else {\n/*// prose inside\nint i = 1;\n*///?}\n"
            .replace("\n", "\r\n"));
        Assertions.assertEquals(List.of(), crlf.findings);
    }

    /** Everything the guard cannot judge must be reported rather than passed over. */
    @Test
    public void scannerFailsLoudOnWhatItCannotJudge() {
        int[] active = {26, 3};

        Scan block = new Scan(active);
        block.file("J.java", "/*? if >=26.3 {*/\nint j = 3;\n/*?}*/\n");
        Assertions.assertEquals(2, block.findings.size(), block.findings.toString());
        Assertions.assertTrue(block.findings.get(0).startsWith("J.java:1: a block-comment directive"),
            block.findings.get(0));

        Scan shape = new Scan(active);
        shape.file("K.java", "//? switch (x) {\n");
        Assertions.assertEquals(1, shape.findings.size(), shape.findings.toString());
        Assertions.assertTrue(shape.findings.get(0).contains("not a directive shape"), shape.findings.get(0));

        Scan orphan = new Scan(active);
        orphan.file("L.java", "int l = 1;\n//?}\n");
        Assertions.assertEquals(1, orphan.findings.size(), orphan.findings.toString());
        Assertions.assertTrue(orphan.findings.get(0).startsWith("L.java:2: "), orphan.findings.get(0));

        Scan unclosed = new Scan(active);
        unclosed.file("M.java", "//? if >=26.3 {\nint m = 3;\n");
        Assertions.assertEquals(1, unclosed.findings.size(), unclosed.findings.toString());
        Assertions.assertTrue(unclosed.findings.get(0).startsWith("M.java:1: "), unclosed.findings.get(0));
        Assertions.assertTrue(unclosed.findings.get(0).contains("never closed"), unclosed.findings.get(0));

        Scan unevaluable = new Scan(active);
        unevaluable.file("N.java", "//? if mod(\"jei\") {\nint n = 3;\n//?}\n");
        Assertions.assertEquals(1, unevaluable.findings.size(), unevaluable.findings.toString());
        Assertions.assertTrue(unevaluable.findings.get(0).contains("cannot evaluate the condition"),
            unevaluable.findings.get(0));

        // Formats Stonecutter processes but this guard does not judge: any opener form is reported, line or block
        // (kotlin and the shader handlers read `/*?` as a directive, confirmed on a scratch project).
        Assertions.assertTrue(hasDirectiveOpener("val a = 1\n//? if >=26.3 {\nval b = 2\n//?}\n"));
        Assertions.assertTrue(hasDirectiveOpener("val a = 1\n/*? if >=26.3 {*/\nval b = 2\n/*?}*/\n"));
        Assertions.assertTrue(hasDirectiveOpener("/*? if >=26.3 {*/\nvoid main() {}\n/*?}*/\n"));
        Assertions.assertTrue(hasDirectiveOpener("#? if >=26.3 {\nkey=1\n#?}\n"));
        Assertions.assertFalse(hasDirectiveOpener("val a = 1 // a plain comment\n/* and a block */\n"));
        Assertions.assertFalse(hasDirectiveOpener("# a plain comment\nkey=1\n"));
    }

    /** Whether the text carries something Stonecutter would read as a directive in a format this guard does not judge. */
    private static boolean hasDirectiveOpener(String text) {
        return ANY_DIRECTIVE.matcher(text).find();
    }

    /** What {@code stonecutter.gradle.kts} says the active node is, as numeric version parts. */
    private static int[] activeNode(Path root) throws IOException {
        Matcher m = ACTIVE_NODE.matcher(Files.readString(root.resolve("stonecutter.gradle.kts"), StandardCharsets.UTF_8));
        Assertions.assertTrue(m.find(),
            "no `stonecutter active \"<node>\"` line in stonecutter.gradle.kts - if it changed shape, update ACTIVE_NODE");
        return version(m.group(1));
    }

    private static int[] version(String text) {
        String[] parts = text.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }

    private static String versionText(int[] version) {
        StringBuilder sb = new StringBuilder();
        for (int part : version) {
            sb.append(sb.length() == 0 ? "" : ".").append(part);
        }
        return sb.toString();
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** One scanned token: a comment, or the first non-blank character of a run of code (strings count as code). */
    private record Tok(boolean comment, boolean line, int start, String body) {}

    private static final class Arm {
        final String marker;
        final int markerLine;
        final boolean live;
        /** Set when the chain's condition could not be evaluated: which arm is live is unknown, so none is judged. */
        boolean unjudged;
        /** Real content, including a nested directive: Stonecutter does not call such an arm commented. */
        boolean code;
        final List<Tok> comments = new ArrayList<>();

        Arm(String marker, int markerLine, boolean live) {
            this.marker = marker;
            this.markerLine = markerLine;
            this.live = live;
        }
    }

    private static final class Chain {
        boolean taken;
        /** A condition of this chain could not be evaluated (already reported), so its arms are not judged. */
        boolean unknown;
        Arm arm;
    }

    /** The findings and arm counts over every file fed to {@link #file}. */
    private static final class Scan {
        final int[] active;
        final List<String> findings = new ArrayList<>();
        int liveArms;
        int inactiveArms;
        private String where;
        private int[] lineStarts;

        Scan(int[] active) {
            this.active = active;
        }

        void file(String where, String text) {
            this.where = where;
            this.lineStarts = lineStarts(text);
            run(tokenize(text, 0, false));
        }

        /** One run of tokens with its own directive stack: a whole file, or the body of a block comment in an
         *  inactive arm (Stonecutter re-lexes that once a node enables the arm, so a chain inside it counts). */
        private void run(List<Tok> toks) {
            Deque<Chain> stack = new ArrayDeque<>();
            for (Tok tok : toks) {
                if (!tok.comment) {
                    if (!stack.isEmpty()) {
                        stack.peek().arm.code = true;
                    }
                    continue;
                }
                boolean directive = tok.body.startsWith("?");
                if (directive && !tok.line) {
                    report(tok, "a block-comment directive is not supported by this guard; use the // form");
                    continue;
                }
                if (!directive) {
                    if (!stack.isEmpty()) {
                        stack.peek().arm.comments.add(tok);
                    }
                    continue;
                }
                String marker = ("//" + tok.body).trim();
                String body = tok.body.substring(1).trim();
                Matcher open = IF.matcher(body);
                Matcher elif = ELIF.matcher(body);
                if (open.matches()) {
                    if (!stack.isEmpty()) {
                        stack.peek().arm.code = true;
                    }
                    Chain chain = new Chain();
                    stack.push(chain);
                    begin(chain, open.group(1), marker, tok);
                } else if (!stack.isEmpty() && (elif.matches() || ELSE.matcher(body).matches())) {
                    end(stack.peek().arm);
                    begin(stack.peek(), elif.matches() ? elif.group(1) : null, marker, tok);
                } else if (!stack.isEmpty() && body.equals("}")) {
                    end(stack.pop().arm);
                } else {
                    report(tok, "`" + marker + "` is not a directive shape this guard knows (an arm opener, "
                        + "`} else {`, `} elif <cond> {` or `}`), or it has no opener");
                }
            }
            for (Chain unclosed : stack) {
                findings.add(where + ":" + unclosed.arm.markerLine + ": `" + unclosed.arm.marker + "` is never closed");
            }
        }

        private void begin(Chain chain, String cond, String marker, Tok tok) {
            boolean live = false;
            if (!chain.taken) {
                try {
                    live = cond == null || holds(cond, active);
                } catch (IllegalArgumentException e) {
                    report(tok, e.getMessage());
                    chain.unknown = true;
                }
            }
            chain.taken |= live;
            chain.arm = new Arm(marker, lineOf(tok), live);
            chain.arm.unjudged = chain.unknown;
            if (live) {
                liveArms++;
            } else {
                inactiveArms++;
            }
        }

        private void end(Arm arm) {
            if (arm.unjudged) {
                return;
            }
            if (arm.live) {
                if (!arm.code && !arm.comments.isEmpty()) {
                    findings.add(where + ":" + arm.markerLine + ": the live arm of `" + arm.marker + "` holds only "
                        + "comments. Stonecutter reads that as a commented arm and strips the comment delimiters on "
                        + "every node that enables it: a // line is left as bare text, and what sits in a /* */ is "
                        + "compiled as live code. Move the comment above the directive, or put uncommented code in "
                        + "the arm");
                }
                return;
            }
            if (arm.code) {
                findings.add(where + ":" + arm.markerLine + ": the inactive arm of `" + arm.marker + "` holds code "
                    + "outside a block comment. Stonecutter wraps what it disables in one /* */; bare code here is "
                    + "compiled on the active node beside the live arm. Wrap the arm's content in a block comment, "
                    + "or delete it");
            }
            for (Tok tok : arm.comments) {
                if (!tok.line) {
                    // Re-lexed once a node enables the arm and its wrapper is stripped; an arm that also holds code is
                    // reported above and still has its comments judged, so fixing that finding reveals nothing new.
                    run(tokenize(tok.body, tok.start + 2, true));
                } else if (!arm.code) {
                    // With code in the arm Stonecutter does not read it as commented and leaves a // alone.
                    report(tok, "a // comment in the inactive arm of `" + arm.marker + "` sits outside the arm's block "
                        + "comment. Stonecutter strips the // when a node enables the arm, leaving bare text. Move it "
                        + "above the directive or inside the block comment");
                }
            }
        }

        private void report(Tok tok, String message) {
            findings.add(where + ":" + lineOf(tok) + ": " + message);
        }

        /** 1-based line of the token: the number of line starts at or before its offset. */
        private int lineOf(Tok tok) {
            int k = Arrays.binarySearch(lineStarts, tok.start);
            return k >= 0 ? k + 1 : -k - 1;
        }
    }

    /** Whether a version condition ({@code >=26.2}, {@code >=1.21.10 && <26.3}) holds for {@code active}. */
    private static boolean holds(String cond, int[] active) {
        boolean any = false;
        for (String conjunction : cond.split("\\|\\|")) {
            boolean all = true;
            for (String term : conjunction.split("&&")) {
                Matcher m = TERM.matcher(term.trim());
                if (!m.matches()) {
                    throw new IllegalArgumentException("cannot evaluate the condition `" + cond + "` (only version "
                        + "comparisons joined by && and || are understood) - extend the guard before using other forms");
                }
                int cmp = compare(active, version(m.group(2)));
                all &= switch (m.group(1)) {
                    case ">=" -> cmp >= 0;
                    case "<=" -> cmp <= 0;
                    case ">" -> cmp > 0;
                    case "<" -> cmp < 0;
                    case "=" -> cmp == 0;
                    default -> cmp != 0;
                };
            }
            any |= all;
        }
        return any;
    }

    /** Stonecutter's slash scanner: comments, with string, char and text-block literals skipped whole. {@code base} is
     *  the offset of {@code s} in its file, so a block comment's body can be scanned with file-wide positions.
     *  {@code nested} is set for the body of a block comment: Java's block comments do not nest, so Stonecutter writes
     *  a block comment that sits inside another one as {@code /^ ... ^/} (a doc comment as {@code /^* ... *^/}), and
     *  only there is that form a comment; in a file's own text it would be bare code. */
    private static List<Tok> tokenize(String s, int base, boolean nested) {
        List<Tok> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        boolean inCode = false;
        while (i < n) {
            char c = s.charAt(i);
            char next = i + 1 < n ? s.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                int end = i + 2;
                while (end < n && s.charAt(end) != '\n' && s.charAt(end) != '\r') {
                    end++;
                }
                out.add(new Tok(true, true, base + i, s.substring(i + 2, end)));
                i = end;
                inCode = false;
            } else if (c == '/' && next == '*') {
                int close = s.indexOf("*/", i + 2);
                out.add(new Tok(true, false, base + i, s.substring(i + 2, close < 0 ? n : close)));
                i = close < 0 ? n : close + 2;
                inCode = false;
            } else if (nested && c == '/' && next == '^') {
                int close = s.indexOf("^/", i + 2);
                out.add(new Tok(true, false, base + i, s.substring(i + 2, close < 0 ? n : close)));
                i = close < 0 ? n : close + 2;
                inCode = false;
            } else if (c == '"' || c == '\'') {
                i = skipLiteral(s, i);
                if (!inCode) {
                    out.add(new Tok(false, false, base + i, ""));
                    inCode = true;
                }
            } else {
                if (!inCode && !Character.isWhitespace(c)) {
                    out.add(new Tok(false, false, base + i, ""));
                    inCode = true;
                }
                i++;
            }
        }
        return out;
    }

    /** The index just past the string, char or text-block literal starting at {@code i}; a backslash escapes. */
    private static int skipLiteral(String s, int i) {
        String quote = s.startsWith("\"\"\"", i) ? "\"\"\"" : s.substring(i, i + 1);
        int j = i + quote.length();
        while (j < s.length() && !s.startsWith(quote, j)) {
            j += s.charAt(j) == '\\' ? 2 : 1;
        }
        return Math.min(s.length(), j + quote.length());
    }

    /** Offsets at which each line begins. */
    private static int[] lineStarts(String s) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }
}
