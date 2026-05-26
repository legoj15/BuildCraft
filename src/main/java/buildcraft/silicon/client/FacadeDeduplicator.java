/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Comparator;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
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
@SuppressWarnings("deprecation")
public class FacadeDeduplicator {
    private static final boolean DEBUG = BCDebugging.shouldDebugLog("silicon.facade");
    private static final RandomSource RANDOM = RandomSource.create(42L);

    /**
     * Removes facade states from {@link FacadeStateManager#validFacadeStates} and
     * {@link FacadeStateManager#stackFacades} that are visually identical to an
     * earlier-registered state. Only the first (by blockstate ID order) survives.
     *
     * <p><b>Thread-safety:</b> builds entirely new map snapshots off the side, then publishes
     * them via volatile reference reassignment. Readers (server tick, recipe lookup) see either
     * the pre-dedup or post-dedup snapshot — never a partially-mutated map — so this is safe to
     * run on the render thread while the server thread is iterating these maps.
     *
     * @param blockStateModels the baked blockstate-to-model map from the BakingResult
     */
    public static void deduplicateVisuallyIdentical(Map<BlockState, BlockStateModel> blockStateModels) {
        // Snapshot the current maps once at the top — we operate on copies for the entire pass.
        SortedMap<BlockState, FacadeBlockStateInfo> currentValid = FacadeStateManager.validFacadeStates;
        Map<ItemStackKey, List<FacadeBlockStateInfo>> currentStackFacades = FacadeStateManager.stackFacades;

        BCLog.logger.info("[silicon.facade] Starting visual deduplication of "
            + currentValid.size() + " facade states...");
        if (currentValid.isEmpty()) return;

        Map<String, FacadeBlockStateInfo> seen = new HashMap<>();
        Map<BlockState, FacadeBlockStateInfo> toRemove = new HashMap<>();
        int dupCount = 0;
        int nullFingerprints = 0;

        for (Map.Entry<BlockState, FacadeBlockStateInfo> entry : currentValid.entrySet()) {
            FacadeBlockStateInfo info = entry.getValue();
            if (!info.isVisible) continue;

            BlockStateModel model = blockStateModels.get(info.state);
            if (model == null) {
                nullFingerprints++;
                continue;
            }

            String fingerprint = computeTextureFingerprint(model);
            if (fingerprint == null) {
                nullFingerprints++;
                continue;
            }

            FacadeBlockStateInfo existing = seen.get(fingerprint);
            if (existing != null) {
                toRemove.put(entry.getKey(), existing);
                dupCount++;
                if (DEBUG) {
                    BCLog.logger.info("[silicon.facade] Dedup: " + info.state
                        + " is visually identical to " + existing.state);
                }
            } else {
                seen.put(fingerprint, info);
            }
        }

        BCLog.logger.info("[silicon.facade] Dedup scan complete: " + seen.size() + " unique, "
            + dupCount + " duplicates, " + nullFingerprints + " null fingerprints");

        // Build the new snapshots off-side. We start from the current published maps and apply
        // removals/insertions to local copies; the live volatile fields stay untouched until publish.
        Comparator<? super BlockState> validComparator = currentValid.comparator();
        SortedMap<BlockState, FacadeBlockStateInfo> nextValid =
            validComparator != null ? new TreeMap<>(validComparator) : new TreeMap<>();
        nextValid.putAll(currentValid);

        // For stackFacades we copy each List too so we can mutate it without touching the
        // published immutable Lists. The dedup-pass's "remove this info from this key's list"
        // operation only touches keys we then re-wrap as immutable below.
        Map<ItemStackKey, List<FacadeBlockStateInfo>> nextStackFacades = new HashMap<>(currentStackFacades.size());
        for (Map.Entry<ItemStackKey, List<FacadeBlockStateInfo>> e : currentStackFacades.entrySet()) {
            nextStackFacades.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        Map<ItemStackKey, List<FacadeBlockStateInfo>> nextStackRedirects = new HashMap<>();
        int redirectCount = 0;

        for (Map.Entry<BlockState, FacadeBlockStateInfo> removal : toRemove.entrySet()) {
            BlockState state = removal.getKey();
            FacadeBlockStateInfo surviving = removal.getValue();
            FacadeBlockStateInfo removed = nextValid.remove(state);
            if (removed != null && !removed.requiredStack.isEmpty()) {
                ItemStackKey stackKey = new ItemStackKey(removed.requiredStack);
                List<FacadeBlockStateInfo> list = nextStackFacades.get(stackKey);
                if (list != null) {
                    list.remove(removed);
                    if (list.isEmpty()) {
                        nextStackFacades.remove(stackKey);
                    }
                }
                // Redirect: removed block's item → surviving facade info(s)
                nextStackRedirects.computeIfAbsent(stackKey, k -> new ArrayList<>()).add(surviving);
                redirectCount++;
            }
        }

        if (dupCount > 0) {
            BCLog.logger.info("[silicon.facade] Removed " + dupCount
                + " visually identical facade duplicates. Remaining: "
                + nextValid.size()
                + " (" + redirectCount + " recipe redirects registered)");
        } else {
            BCLog.logger.info("[silicon.facade] No visual duplicates found.");
        }

        // Second pass: scan ALL blocks (including non-facade blocks like slabs) and
        // redirect any that share textures with a surviving facade.
        // This enables e.g. brick_slab → bricks facade in the Assembly Table.
        int extraRedirects = 0;
        for (Map.Entry<BlockState, BlockStateModel> modelEntry : blockStateModels.entrySet()) {
            BlockState state = modelEntry.getKey();
            // Skip blocks that are already valid facades — they're handled above
            if (nextValid.containsKey(state)) continue;

            net.minecraft.world.item.Item blockItem = state.getBlock().asItem();
            if (blockItem == net.minecraft.world.item.Items.AIR) continue;

            // Only process the default blockstate to avoid scanning every variant of every block
            if (state != state.getBlock().defaultBlockState()) continue;

            net.minecraft.world.item.ItemStack requiredStack = new net.minecraft.world.item.ItemStack(blockItem);
            ItemStackKey stackKey = new ItemStackKey(requiredStack);
            // Skip if this item already has a facade or a redirect
            if (nextStackFacades.containsKey(stackKey)) continue;
            if (nextStackRedirects.containsKey(stackKey)) continue;

            BlockStateModel model = modelEntry.getValue();
            if (model == null) continue;

            String fingerprint = computeTextureFingerprint(model);
            if (fingerprint == null) continue;

            FacadeBlockStateInfo match = seen.get(fingerprint);
            if (match != null) {
                nextStackRedirects.computeIfAbsent(stackKey, k -> new ArrayList<>()).add(match);
                extraRedirects++;
                if (DEBUG) {
                    BCLog.logger.info("[silicon.facade] Extra redirect: "
                        + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                        + " → " + match.state);
                }
            }
        }

        if (extraRedirects > 0) {
            BCLog.logger.info("[silicon.facade] Added " + extraRedirects
                + " extra recipe redirects from non-facade blocks (total redirects: "
                + nextStackRedirects.size() + ")");
        }

        // Freeze the inner Lists and publish all three snapshots atomically (volatile writes).
        // Publish order: validFacadeStates → stackFacades → stackRedirects. A reader that grabs
        // each field via separate volatile reads might see a mid-publish state across two fields;
        // we accept that brief inconsistency (a worst-case Assembly Table tick gets one stale read
        // out of three). The fields are never *individually* mid-mutation, so no CME can occur.
        Map<ItemStackKey, List<FacadeBlockStateInfo>> publishedStackFacades = new HashMap<>(nextStackFacades.size());
        for (Map.Entry<ItemStackKey, List<FacadeBlockStateInfo>> e : nextStackFacades.entrySet()) {
            publishedStackFacades.put(e.getKey(), List.copyOf(e.getValue()));
        }
        Map<ItemStackKey, List<FacadeBlockStateInfo>> publishedStackRedirects = new HashMap<>(nextStackRedirects.size());
        for (Map.Entry<ItemStackKey, List<FacadeBlockStateInfo>> e : nextStackRedirects.entrySet()) {
            publishedStackRedirects.put(e.getKey(), List.copyOf(e.getValue()));
        }
        FacadeStateManager.validFacadeStates = Collections.unmodifiableSortedMap(nextValid);
        FacadeStateManager.stackFacades = Map.copyOf(publishedStackFacades);
        FacadeStateManager.stackRedirects = Map.copyOf(publishedStackRedirects);
    }

    /**
     * Computes a texture fingerprint for a BlockStateModel by examining its quads
     * on all 6 faces. Uses the NeoForge 1.21.11 collectParts/SimpleModelWrapper API.
     * Returns a canonical string of sorted texture names, or null if no quads found.
     */
    private static String computeTextureFingerprint(BlockStateModel model) {
        try {
            Set<String> textures = new LinkedHashSet<>();

            for (Direction dir : Direction.values()) {
                List<BakedQuad> quads = getQuadsFromModel(model, dir);
                for (BakedQuad quad : quads) {
                    textures.add(dir.name() + ":" + quad.materialInfo().sprite().contents().name().toString());
                }
            }
            // Also check null-direction (general/unculled quads)
            List<BakedQuad> generalQuads = getQuadsFromModel(model, null);
            for (BakedQuad quad : generalQuads) {
                textures.add("GENERAL:" + quad.materialInfo().sprite().contents().name().toString());
            }

            if (textures.isEmpty()) return null;

            List<String> sorted = new ArrayList<>(textures);
            sorted.sort(String::compareTo);
            return String.join("|", sorted);
        } catch (Exception e) {
            if (DEBUG) {
                BCLog.logger.warn("[silicon.facade] Failed to compute fingerprint for model", e);
            }
            return null;
        }
    }

    /**
     * Extracts BakedQuads for a given face from a BlockStateModel using the
     * NeoForge 1.21.11 collectParts/SimpleModelWrapper API.
     */
    private static List<BakedQuad> getQuadsFromModel(BlockStateModel model, Direction side) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RANDOM, parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            if (part instanceof net.minecraft.client.resources.model.SimpleModelWrapper smw) {
                QuadCollection qc = smw.quads();
                result.addAll(qc.getQuads(side));
            }
        }
        return result;
    }
}
