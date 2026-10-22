/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.TestHelper;

/**
 * Minecraft 26.3 replaced the shape-computed {@code blocksMotion()} with block tags: {@code minecraft:blocks_motion}
 * now decides the MOTION_BLOCKING heightmap (rain, spawning, oil-spout surfaces), suffocation, the fluid flow vector
 * and lava fire spread, and {@code minecraft:washed_away_by_fluids} decides which blocks a flowing fluid may replace.
 * A modded block is in neither unless the mod lists it. These two files keep every BuildCraft block where it stood on
 * 26.2 and earlier: a block that was "legacy solid" ({@code isSolid()}, the old {@code blocksMotion()}) in every state
 * goes in {@code blocks_motion_no_leaves}; one that was solid in none and is no fluid container (it was washed away)
 * goes in {@code washed_away_by_fluids}. On the older lines both tags are inert.
 * <p>
 * Every entry is written {@code {"id": ..., "required": false}}: these files merge into vanilla tags the whole game
 * reads, and one required entry naming a block that is not registered (the decorated blocks exist only in a dev
 * environment) makes the loader drop the merged tag, vanilla's own entries included, together with every tag that
 * includes it. Optional entries cannot do that; a misspelt id is still caught here, where every block is registered.
 * <p>
 * Breaks when a block is added, or its shape or {@code forceSolidOn/Off} changes, without updating the tag files, or
 * when an entry is written as a required one.
 */
public class BlockMotionTagsTester {
    private static final String NAMESPACE = "buildcraftunofficial";
    private static final String TAG_DIR = "src/main/resources/data/minecraft/tags/block/";

    @Test
    @SuppressWarnings("deprecation") // isSolid() is the exact pre-26.3 test the tags must reproduce
    public void tagsMatchThePre263BlocksMotionClassification() throws IOException {
        TreeSet<String> solid = new TreeSet<>();
        TreeSet<String> washedAway = new TreeSet<>();
        List<String> mixed = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (!NAMESPACE.equals(key.getNamespace())) {
                continue;
            }
            List<BlockState> states = block.getStateDefinition().getPossibleStates();
            long solidStates = states.stream().filter(BlockState::isSolid).count();
            if (solidStates == states.size()) {
                solid.add(key.toString());
            } else if (solidStates == 0) {
                if (!(block instanceof LiquidBlockContainer)) {
                    washedAway.add(key.toString());
                }
            } else {
                mixed.add(key + " (" + solidStates + "/" + states.size() + " states solid)");
            }
        }
        // Liveness: the frame forces itself solid; if block caches were not built every block would read non-solid.
        Assertions.assertTrue(solid.contains(NAMESPACE + ":frame"), "frame should be legacy-solid; solid=" + solid);

        Assertions.assertEquals(List.of(), mixed,
            "a block whose states disagree on isSolid() cannot be expressed by a block tag; decide it by hand");
        Assertions.assertEquals(solid, readTag("blocks_motion_no_leaves.json"),
            "minecraft:blocks_motion_no_leaves must list exactly the legacy-solid BuildCraft blocks");
        Assertions.assertEquals(washedAway, readTag("washed_away_by_fluids.json"),
            "minecraft:washed_away_by_fluids must list exactly the BuildCraft blocks fluids replaced before 26.3");
    }

    private static TreeSet<String> readTag(String file) throws IOException {
        Path path = TestHelper.repoRoot().resolve(TAG_DIR + file);
        Assertions.assertTrue(Files.exists(path), "missing tag file " + path);
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        Assertions.assertFalse(json.has("replace") && json.get("replace").getAsBoolean(),
            file + " must not replace the vanilla tag");
        TreeSet<String> values = new TreeSet<>();
        List<String> required = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray("values")) {
            if (value.isJsonObject()) {
                JsonObject entry = value.getAsJsonObject();
                values.add(entry.get("id").getAsString());
                if (!entry.has("required") || entry.get("required").getAsBoolean()) {
                    required.add(entry.get("id").getAsString());
                }
            } else {
                values.add(value.getAsString());
                required.add(value.getAsString());
            }
        }
        Assertions.assertEquals(List.of(), required,
            file + ": entries must be {\"id\": ..., \"required\": false}; a required entry for an unregistered block"
                + " drops the whole vanilla tag");
        return values;
    }
}
