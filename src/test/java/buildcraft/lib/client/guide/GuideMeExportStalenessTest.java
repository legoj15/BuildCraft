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
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Test;

import buildcraft.TestHelper;

/** The GuideME guide under {@code assets/buildcraftunofficial/guides/} is generated from the in-game guide's sources by
 *  {@code scripts/guide-to-guideme.py} and committed. This fails when the two have drifted apart: a guide page, the
 *  registration script, a recipe fact the export uses, a lang string the export read, or the exporter itself changed
 *  without a re-run, or a generated file was edited by hand.
 *
 *  <p>The script writes {@code scripts/guide-to-guideme.stamp} with a hash of its inputs, the lang keys it read, and a
 *  hash of its output; this recomputes both exactly as the script's {@code input_hash} / {@code _digest} do. Each file
 *  contributes its repo-relative path and its bytes with carriage returns removed, so a CRLF checkout hashes the same.
 *  Recipes contribute only {@code recipe_facts}, so a recipe edit that cannot change a page leaves this green. Keep the
 *  two in step. */
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
        Map<String, TreeSet<String>> langKeys = new TreeMap<>();
        for (String line : Files.readAllLines(stampFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("inputs ")) stampInputs = line.substring(7);
            else if (line.startsWith("outputs ")) stampOutputs = line.substring(8);
            else if (line.startsWith("lang ")) {
                // "lang <locale> <key>": the lang keys that locale's export read
                String[] parts = line.split(" ", 3);
                assertEquals(3, parts.length, STAMP + " has a malformed lang line: " + line);
                langKeys.computeIfAbsent(parts[1], k -> new TreeSet<>()).add(parts[2]);
            }
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
        return files;
    }

    /** The script's {@code recipe_facts}: per recipe JSON its id, type and result, then the BC items it consumes.
     *  Nothing else in a recipe reaches a generated page, so nothing else may redden this test. */
    private static List<String> recipeFacts(Path root) throws IOException {
        List<Path> recipes = new ArrayList<>(
            walk(root.resolve(RECIPES), 1).stream().filter(p -> p.toString().endsWith(".json")).toList());
        recipes.sort((a, b) -> stem(a).compareTo(stem(b)));
        List<String> lines = new ArrayList<>();
        for (Path p : recipes) {
            String stem = stem(p);
            JsonObject r;
            try {
                r = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException e) {
                lines.add("recipe " + stem + " invalid");
                continue;
            }
            String type = r.has("type") ? r.get("type").getAsString() : "-";
            String result = resultId(r);
            lines.add("recipe " + stem + " " + type + " " + (result == null ? "-" : result));
            if (result == null) continue;
            TreeSet<String> uses = new TreeSet<>();
            if (r.get("key") instanceof JsonObject key) {
                for (var e : key.entrySet()) collectItemIds(e.getValue(), uses);
            }
            if (r.get("ingredients") instanceof JsonArray ingredients) {
                for (JsonElement e : ingredients) collectItemIds(e, uses);
            }
            for (String id : uses) {
                if (id.startsWith("buildcraftunofficial:")) lines.add("use " + stem + " " + id);
            }
        }
        return lines;
    }

    private static String resultId(JsonObject r) {
        JsonElement res = r.get("result");
        if (res instanceof JsonObject o) res = o.get("id");
        return res == null || res.isJsonNull() ? null : res.getAsString();
    }

    /** guide_model's {@code ing_ids}, item ids only: a bare string that is not a {@code #tag}, an {@code item} field,
     *  and an {@code items} string or list, recursing through lists. */
    private static void collectItemIds(JsonElement v, TreeSet<String> out) {
        if (v instanceof JsonPrimitive s && s.isString()) {
            if (!s.getAsString().startsWith("#")) out.add(s.getAsString());
        } else if (v instanceof JsonArray a) {
            for (JsonElement e : a) collectItemIds(e, out);
        } else if (v instanceof JsonObject o) {
            if (o.has("item")) out.add(o.get("item").getAsString());
            if (o.get("items") instanceof JsonPrimitive items) collectItemIds(items, out);
            else if (o.get("items") instanceof JsonArray items) {
                for (JsonElement e : items) collectItemIds(e, out);
            }
        }
    }

    private static String stem(Path p) {
        String name = p.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private static List<Path> outputFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>(walk(root.resolve(GUIDE_OUT), Integer.MAX_VALUE));
        files.add(root.resolve(GUIDE_DEF));
        return files;
    }

    private static String inputHash(Path root, Map<String, TreeSet<String>> langKeys) throws IOException {
        MessageDigest h = digest(root, inputFiles(root));
        for (String line : recipeFacts(root)) {
            h.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        for (String locale : langKeys.keySet()) {
            JsonObject merged = JsonParser.parseString(Files.readString(root.resolve(LANG), StandardCharsets.UTF_8))
                .getAsJsonObject().deepCopy();
            if (!"en_us".equals(locale)) {
                Path own = root.resolve(LANG).resolveSibling(locale + ".json");
                for (var e : JsonParser.parseString(Files.readString(own, StandardCharsets.UTF_8))
                    .getAsJsonObject().entrySet()) {
                    merged.add(e.getKey(), e.getValue());
                }
            }
            for (String key : langKeys.get(locale)) {
                JsonElement v = merged.get(key);
                String value = v == null || v.isJsonNull() ? "\0" : v.getAsString();
                h.update((locale + "\u001f" + key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8));
            }
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
