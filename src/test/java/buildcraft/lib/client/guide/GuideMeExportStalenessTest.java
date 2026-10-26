/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import buildcraft.TestHelper;

/** The GuideME guide under {@code assets/buildcraftunofficial/guides/} is generated from the in-game guide's sources by
 *  {@code scripts/guide-to-guideme.py} and committed. This fails when the two have drifted apart: a guide page, the
 *  registration script, a recipe, a lang string the export read, or the exporter itself changed without a re-run, or
 *  a generated file was edited by hand.
 *
 *  <p>The script writes {@code scripts/guide-to-guideme.stamp} with a hash of its inputs, the lang keys it read, and a
 *  hash of its output; this recomputes both exactly as the script's {@code input_hash} / {@code _digest} do. Each file
 *  contributes its repo-relative path and its bytes with carriage returns removed, so a CRLF checkout hashes the same.
 *  Keep the two in step. */
class GuideMeExportStalenessTest {

    private static final String RES = "src/main/resources/";
    private static final String COMPAT = RES + "assets/buildcraftunofficial/compat/buildcraft/";
    private static final String GUIDE_OUT = RES + "assets/buildcraftunofficial/guides/buildcraftunofficial/guide";
    private static final String GUIDE_DEF = RES + "assets/buildcraftunofficial/guideme_guides/guide.json";
    private static final String LANG = RES + "assets/buildcraftunofficial/lang/en_us.json";
    private static final String RECIPES = RES + "data/buildcraftunofficial/recipe";
    private static final String STAMP = "scripts/guide-to-guideme.stamp";

    @Test
    void exportMatchesItsSources() throws IOException {
        Path root = TestHelper.repoRoot();
        Path stampFile = root.resolve(STAMP);
        assertTrue(Files.isRegularFile(stampFile), STAMP + " is missing: run python scripts/guide-to-guideme.py");

        String stampInputs = null;
        String stampOutputs = null;
        TreeSet<String> langKeys = new TreeSet<>();
        for (String line : Files.readAllLines(stampFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("inputs ")) stampInputs = line.substring(7);
            else if (line.startsWith("outputs ")) stampOutputs = line.substring(8);
            else if (line.startsWith("lang ")) langKeys.add(line.substring(5));
        }

        String hint = " Run python scripts/guide-to-guideme.py and commit the result.";
        assertEquals(stampInputs, inputHash(root, langKeys),
            "The guide sources changed since the GuideME export was generated." + hint);
        assertEquals(stampOutputs, hex(digest(root, outputFiles(root))),
            "The generated GuideME files differ from what the exporter wrote (edited by hand?)." + hint);
    }

    private static List<Path> inputFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("scripts/guide_model.py"));
        files.add(root.resolve("scripts/guide-to-guideme.py"));
        files.add(root.resolve(COMPAT + "guide.txt"));
        files.addAll(walk(root.resolve(COMPAT + "guide"), Integer.MAX_VALUE));
        files.addAll(walk(root.resolve(RECIPES), 1).stream().filter(p -> p.toString().endsWith(".json")).toList());
        return files;
    }

    private static List<Path> outputFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>(walk(root.resolve(GUIDE_OUT), Integer.MAX_VALUE));
        files.add(root.resolve(GUIDE_DEF));
        return files;
    }

    private static String inputHash(Path root, TreeSet<String> langKeys) throws IOException {
        MessageDigest h = digest(root, inputFiles(root));
        JsonObject lang = JsonParser.parseString(Files.readString(root.resolve(LANG), StandardCharsets.UTF_8))
            .getAsJsonObject();
        for (String key : langKeys) {
            JsonElement v = lang.get(key);
            String value = v == null || v.isJsonNull() ? "\0" : v.getAsString();
            h.update((key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return hex(h);
    }

    private static List<Path> walk(Path dir, int depth) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir, depth)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    private static MessageDigest digest(Path root, List<Path> files) throws IOException {
        MessageDigest h;
        try {
            h = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort((a, b) -> rel(root, a).compareTo(rel(root, b)));
        for (Path p : sorted) {
            h.update((rel(root, p) + "\n").getBytes(StandardCharsets.UTF_8));
            h.update(stripCarriageReturns(Files.readAllBytes(p)));
            h.update((byte) '\n');
        }
        return h;
    }

    private static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static byte[] stripCarriageReturns(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        for (byte b : bytes) {
            if (b != '\r') out.write(b);
        }
        return out.toByteArray();
    }

    private static String hex(MessageDigest h) {
        return HexFormat.of().formatHex(h.digest());
    }
}
