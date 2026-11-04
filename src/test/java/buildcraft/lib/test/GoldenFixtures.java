/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Golden save-format fixtures: pins the exact persisted shape of a value so a later refactor (the platform layer's
 * {@code BCFluidStack} and transfer slices) can prove it changed nothing.
 *
 * <p>A fixture is SNBT text under {@code src/test/resources/golden/<dir>/<name>.snbt}, one top-level key per line,
 * keys sorted. A non-compound value is wrapped as {@code {value: ...}}. Comparison is on the parsed tags (typed:
 * a byte never equals an int), so the text layout is only for humans and diffs.
 *
 * <p>Directory choice at runtime, no Stonecutter directives: {@code golden/<minecraft version>/} wins when it exists
 * (a node whose bytes differ from its group), else {@code golden/legacy/} for nodes without vanilla's
 * {@code ValueOutput} (1.21.1) or {@code golden/modern/} for the rest.
 *
 * <p><b>Regenerating.</b> The default run only compares. To rewrite the fixtures of the node under test run, e.g.
 * <pre>
 *   BUILDCRAFT_GOLDEN_UPDATE=true ./gradlew :26.3:test --tests "*Golden*"
 * </pre>
 * (the environment variable, or the system property {@code buildcraft.golden.update=true} when the test JVM is given
 * one). Update mode writes into {@code golden/<minecraft version>/} of the source tree and passes; review the diff,
 * and fold identical per-version directories into {@code legacy}/{@code modern} by hand.
 */
public final class GoldenFixtures {
    private GoldenFixtures() {}

    private static boolean updating() {
        return "true".equalsIgnoreCase(System.getProperty("buildcraft.golden.update"))
                || "true".equalsIgnoreCase(System.getenv("BUILDCRAFT_GOLDEN_UPDATE"))
                || "1".equals(System.getenv("BUILDCRAFT_GOLDEN_UPDATE"));
    }

    /** The node's Minecraft version name, read reflectively because vanilla renamed the accessor in 26.x. */
    public static String minecraftVersion() {
        Object version = SharedConstants.getCurrentVersion();
        for (String method : new String[] { "name", "getName", "id", "getId" }) {
            try {
                Object value = version.getClass().getMethod(method).invoke(version);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
                // try the next spelling
            }
        }
        return group();
    }

    /** {@code legacy} (no ValueOutput, i.e. 1.21.1) or {@code modern}. */
    public static String group() {
        try {
            Class.forName("net.minecraft.world.level.storage.ValueOutput");
            return "modern";
        } catch (ClassNotFoundException e) {
            return "legacy";
        }
    }

    /** Wraps a non-compound tag so every fixture is a compound. */
    public static CompoundTag asCompound(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            return compound;
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("value", tag);
        return wrapper;
    }

    /** Fails unless {@code actual} equals the committed fixture {@code name}; in update mode rewrites it instead. */
    public static void assertGolden(String name, Tag actual) {
        CompoundTag compound = asCompound(actual);
        if (updating()) {
            write(name, compound);
            return;
        }
        CompoundTag expected = load(name);
        if (!expected.equals(compound)) {
            throw new AssertionError("golden fixture '" + name + "' (" + minecraftVersion() + ")\n"
                    + "expected: " + render(expected) + "\nactual:   " + render(compound));
        }
    }

    /** The committed fixture {@code name}, parsed. In update mode, whatever was just written for this node. */
    public static CompoundTag load(String name) {
        for (String dir : new String[] { minecraftVersion(), group() }) {
            String resource = "/golden/" + dir + "/" + name + ".snbt";
            try (InputStream in = GoldenFixtures.class.getResourceAsStream(resource)) {
                if (in != null) {
                    return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + resource, e);
            }
        }
        throw new AssertionError("no golden fixture '" + name + "' for " + minecraftVersion() + " / " + group()
                + "; regenerate with BUILDCRAFT_GOLDEN_UPDATE=true");
    }

    /** Whether this run rewrites fixtures (then {@link #load} may see stale classpath copies: callers skip round trips). */
    public static boolean isUpdating() {
        return updating();
    }

    private static void write(String name, CompoundTag tag) {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("stonecutter.gradle.kts"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("cannot find the project root to write golden fixtures");
        }
        Path file = root.resolve("src/test/resources/golden").resolve(minecraftVersion()).resolve(name + ".snbt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, render(tag) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + file, e);
        }
    }

    /** Sorted keys, one per line. */
    public static String render(CompoundTag tag) {
        List<String> keys = NBTUtilBC.keysOf(tag);
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
            sb.append("\n  ").append(quoteKey(keys.get(i))).append(": ").append(tag.get(keys.get(i)));
            if (i + 1 < keys.size()) {
                sb.append(',');
            }
        }
        return sb.append(keys.isEmpty() ? "}" : "\n}").toString();
    }

    private static String quoteKey(String key) {
        return key.matches("[A-Za-z0-9._+-]+") ? key : "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static CompoundTag parse(String snbt) {
        try {
            //? if >=1.21.10 {
            return TagParser.parseCompoundFully(snbt);
            //?} else {
            /*return TagParser.parseTag(snbt);*/
            //?}
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("golden fixture does not parse: " + snbt, e);
        }
    }
}
