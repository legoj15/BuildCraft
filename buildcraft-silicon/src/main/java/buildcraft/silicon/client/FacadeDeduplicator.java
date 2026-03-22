/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

import buildcraft.lib.misc.ItemStackKey;

import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Client-side visual deduplication of facades. Compares baked block model textures
 * on all 6 faces and removes later-registered facades that are visually identical
 * to an earlier one (e.g. bricks vs double brick_slab, waterlogged vs non-waterlogged).
 *
 * <p>Must be called after model baking is complete.
 */
public class FacadeDeduplicator {
    private static final boolean DEBUG = BCDebugging.shouldDebugLog("silicon.facade");
    private static final RandomSource RANDOM = RandomSource.create(42L);

    /**
     * Removes facade states from {@link FacadeStateManager#validFacadeStates} and
     * {@link FacadeStateManager#stackFacades} that are visually identical to an
     * earlier-registered state. Only the first (by blockstate ID order) survives.
     */
    public static void deduplicateVisuallyIdentical() {
        if (FacadeStateManager.validFacadeStates.isEmpty()) return;

        Map<String, FacadeBlockStateInfo> seen = new HashMap<>();
        Set<BlockState> toRemove = new HashSet<>();
        int dupCount = 0;

        for (Map.Entry<BlockState, FacadeBlockStateInfo> entry
                : FacadeStateManager.validFacadeStates.entrySet()) {
            FacadeBlockStateInfo info = entry.getValue();
            if (!info.isVisible) continue;

            String fingerprint = computeTextureFingerprint(info.state);
            if (fingerprint == null) continue;

            FacadeBlockStateInfo existing = seen.get(fingerprint);
            if (existing != null) {
                toRemove.add(entry.getKey());
                dupCount++;
                if (DEBUG) {
                    BCLog.logger.info("[silicon.facade] Dedup: " + info.state
                        + " is visually identical to " + existing.state);
                }
            } else {
                seen.put(fingerprint, info);
            }
        }

        // Remove duplicates from both maps
        for (BlockState state : toRemove) {
            FacadeBlockStateInfo removed = FacadeStateManager.validFacadeStates.remove(state);
            if (removed != null && !removed.requiredStack.isEmpty()) {
                ItemStackKey stackKey = new ItemStackKey(removed.requiredStack);
                List<FacadeBlockStateInfo> list = FacadeStateManager.stackFacades.get(stackKey);
                if (list != null) {
                    list.remove(removed);
                    if (list.isEmpty()) {
                        FacadeStateManager.stackFacades.remove(stackKey);
                    }
                }
            }
        }

        if (dupCount > 0) {
            BCLog.logger.info("[silicon.facade] Removed " + dupCount
                + " visually identical facade duplicates. Remaining: "
                + FacadeStateManager.validFacadeStates.size());
        }
    }

    /**
     * Computes a texture fingerprint for a blockstate by examining its baked model's
     * quads on all 6 faces. Uses the NeoForge 1.21.11 BlockStateModel/collectParts API.
     * Returns a canonical string of sorted texture names, or null if the model
     * couldn't be obtained.
     */
    private static String computeTextureFingerprint(BlockState state) {
        try {
            BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            if (model == null) return null;

            Set<String> textures = new LinkedHashSet<>();

            for (Direction dir : Direction.values()) {
                List<BakedQuad> quads = getQuadsFromModel(model, dir);
                for (BakedQuad quad : quads) {
                    textures.add(dir.name() + ":" + quad.sprite().contents().name().toString());
                }
            }
            // Also check null-direction (general/unculled quads)
            List<BakedQuad> generalQuads = getQuadsFromModel(model, null);
            for (BakedQuad quad : generalQuads) {
                textures.add("GENERAL:" + quad.sprite().contents().name().toString());
            }

            if (textures.isEmpty()) return null;

            List<String> sorted = new ArrayList<>(textures);
            sorted.sort(String::compareTo);
            return String.join("|", sorted);
        } catch (Exception e) {
            if (DEBUG) {
                BCLog.logger.warn("[silicon.facade] Failed to compute fingerprint for " + state, e);
            }
            return null;
        }
    }

    /**
     * Extracts BakedQuads for a given face from a BlockStateModel using the
     * NeoForge 1.21.11 collectParts/SimpleModelWrapper API.
     */
    private static List<BakedQuad> getQuadsFromModel(BlockStateModel model, Direction side) {
        List<BlockModelPart> parts = new ArrayList<>();
        model.collectParts(RANDOM, parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockModelPart part : parts) {
            if (part instanceof net.minecraft.client.renderer.block.model.SimpleModelWrapper smw) {
                QuadCollection qc = smw.quads();
                result.addAll(qc.getQuads(side));
            }
        }
        return result;
    }
}
