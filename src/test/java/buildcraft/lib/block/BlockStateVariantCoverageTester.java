/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import buildcraft.TestHelper;

/**
 * Every state of every BuildCraft block must be matched by a variant in its blockstate JSON.
 *
 * <p>Scope: this checks variant-key coverage only — not that the matched variant's model file exists
 * (vanilla logs a missing model file at resource load), and not multipart definitions.
 *
 * <p>A {@code "variants"} key only constrains the properties it names ({@code ""} matches every state), so
 * adding a property such as {@code WATERLOGGED} is free when the keys leave it out — but a JSON whose keys
 * enumerate every property would silently leave the new states on the missing-texture model, and nothing
 * fails until someone sees a purple-black block in-world. This walks the live block registry (so a new
 * block or property is covered automatically) against the one shared {@code src/main/resources} tree
 * that every Stonecutter node ships. {@code "multipart"} definitions are skipped: a part that matches
 * nothing just draws nothing, which is a legitimate design.
 */
public class BlockStateVariantCoverageTester {

    private static final String NAMESPACE = "buildcraftunofficial";
    private static final String BLOCKSTATE_DIR = "src/main/resources/assets/" + NAMESPACE + "/blockstates";

    @Test
    void everyBlockStateMatchesAVariant() {
        Path dir = TestHelper.repoRoot().resolve(BLOCKSTATE_DIR);
        Assertions.assertTrue(Files.isDirectory(dir), "blockstate directory missing: " + dir);

        Map<String, String> failures = new TreeMap<>();
        int variantBlocks = 0;
        int waterloggableChecked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (!NAMESPACE.equals(key.getNamespace())) {
                continue;
            }
            Path json = dir.resolve(key.getPath() + ".json");
            if (!Files.isRegularFile(json)) {
                failures.put(key.getPath(), "no blockstate JSON at " + json.getFileName());
                continue;
            }
            JsonObject root = parse(json);
            if (!root.has("variants")) {
                continue; // multipart
            }
            variantBlocks++;
            List<String> keys = new ArrayList<>(root.getAsJsonObject("variants").keySet());
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (keys.stream().noneMatch(k -> matches(k, state))) {
                    failures.put(key.getPath(), "no variant matches " + state + " (keys: " + keys + ")");
                    break;
                }
            }
            if (block.defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED)) {
                waterloggableChecked++;
            }
        }

        Assertions.assertTrue(variantBlocks > 0, "no BuildCraft variant blockstates were checked -- registry walk broke");
        Assertions.assertTrue(waterloggableChecked > 0,
            "no waterloggable BuildCraft block was checked -- the pipe holder and markers should be");
        Assertions.assertTrue(failures.isEmpty(), "blockstate JSON coverage gaps:\n  "
            + String.join("\n  ", failures.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList()));
    }

    /** Vanilla variant-key semantics: comma-separated {@code property=value} pairs, every named property must
     *  match, unnamed properties are unconstrained, {@code ""} matches everything. */
    private static boolean matches(String variantKey, BlockState state) {
        if (variantKey.isEmpty()) {
            return true;
        }
        for (String pair : variantKey.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) {
                return false;
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(kv[0]);
            if (property == null || !kv[1].equals(valueName(state, property))) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static JsonObject parse(Path json) {
        try {
            return JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
