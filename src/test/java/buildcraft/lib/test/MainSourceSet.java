/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The main source set <em>as this node compiled it</em>, for unit tests that inspect code rather than run it.
 *
 * <p>Stonecutter hands each node its own view of the shared tree: the active node compiles {@code src/main/java}
 * raw, every other node compiles a generated copy under {@code versions/<node>/build/generated/stonecutter} in
 * which the other lines' {@code //? if} branches are commented out. A test that read {@code src/} directly would
 * therefore see the ACTIVE node's code on every node. The build script passes the node's real directories as
 * system properties ({@code buildcraft.test.mainSourceDirs} / {@code buildcraft.test.mainClassesDirs}, see the
 * {@code tasks.test} block in build.gradle.kts), and this class is the one place that reads them.
 */
public final class MainSourceSet {
    private MainSourceSet() {}

    /** A main source file: its path relative to the source root ({@code buildcraft/lib/Foo.java}, forward slashes)
     *  and its full text. */
    public record SourceFile(String relativePath, String text) {}

    /** The directories javac compiled this node's main code from. */
    public static List<Path> sourceDirs() {
        return dirs("buildcraft.test.mainSourceDirs", false);
    }

    /** The directories this node's compiled main classes were written to. */
    public static List<Path> classesDirs() {
        return dirs("buildcraft.test.mainClassesDirs", true);
    }

    /** Every main {@code .java} file javac actually compiled on this node. {@code compat/rei/**} is skipped because
     *  the build excludes it from compilation (no REI for the current lines), so its imports never resolve. */
    public static List<SourceFile> javaFiles() {
        List<SourceFile> files = new ArrayList<>();
        for (Path root : sourceDirs()) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    if (rel.contains("/compat/rei/")) {
                        continue;
                    }
                    files.add(new SourceFile(rel, Files.readString(p, StandardCharsets.UTF_8)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    /** Every compiled main {@code .class} file on this node. */
    public static List<Path> classFiles() {
        List<Path> files = new ArrayList<>();
        for (Path root : classesDirs()) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(f -> f.toString().endsWith(".class")).forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    /** One main source file's live code on this node ({@link #codeOnly}), by its source-root-relative path. */
    public static String codeOf(String relativePath) {
        return javaFiles().stream()
            .filter(f -> f.relativePath().equals(relativePath))
            .map(f -> codeOnly(f.text()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("this node did not compile " + relativePath));
    }

    /** The brace-balanced body of the first method in {@code code} whose declaration contains {@code signature}. */
    public static String methodBody(String code, String signature) {
        int at = code.indexOf(signature);
        if (at < 0) {
            throw new AssertionError("method not found: " + signature);
        }
        int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    /**
     * Blanks out every comment <em>and</em> the inside of every string, char and text-block literal in {@code src},
     * keeping the literal delimiters and all line breaks, so a search over the result sees only code that is live on
     * this node: Stonecutter's inactive branches are {@code /* *}{@code /} blocks, prose mentions of an API sit in
     * comments, and a string that merely names an API (or a text block holding a code sample) is not a use of it.
     */
    public static String codeOnly(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    blank(out, src.charAt(i));
                    i++;
                }
                i += 2;
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                out.append("\"\"\"");
                i += 3;
                while (i < n && !src.startsWith("\"\"\"", i)) {
                    i = blankEscapeAware(out, src, i);
                }
                if (i < n) {
                    out.append("\"\"\"");
                    i += 3;
                }
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < n && src.charAt(i) != c && src.charAt(i) != '\n') {
                    i = blankEscapeAware(out, src, i);
                }
                if (i < n && src.charAt(i) == c) {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Blanks the literal character at {@code i} — both characters of an escape pair — and returns the next index. */
    private static int blankEscapeAware(StringBuilder out, String src, int i) {
        int end = Math.min(src.length(), i + (src.charAt(i) == '\\' ? 2 : 1));
        for (; i < end; i++) {
            blank(out, src.charAt(i));
        }
        return i;
    }

    private static void blank(StringBuilder out, char c) {
        out.append(c == '\n' ? '\n' : ' ');
    }

    /** @param strict every named directory must exist — a missing classes dir would silently leave part of the
     *                node's code unscanned; source-set dirs may legitimately name absent defaults. */
    private static List<Path> dirs(String property, boolean strict) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("System property " + property + " is not set — the tasks.test block in "
                + "build.gradle.kts passes it; a test run outside Gradle has to pass it by hand");
        }
        List<Path> dirs = new ArrayList<>();
        for (String part : value.split(File.pathSeparator)) {
            Path p = Path.of(part);
            if (Files.isDirectory(p)) {
                dirs.add(p);
            } else if (strict) {
                throw new IllegalStateException(property + " names a directory that does not exist: " + p);
            }
        }
        if (dirs.isEmpty()) {
            throw new IllegalStateException(property + " names no existing directory: " + value);
        }
        return dirs;
    }
}
