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

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.QuadCollection;*/
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;*/
//?}
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
 *
 * <p><b>Why this is client-only, and what that means for the server.</b> The dedup
 * compares <em>baked block-model textures</em>, which only exist on the client — a
 * dedicated server's {@code ResourceManager} exposes {@code data/} only (never
 * {@code assets/}), and the Mojang server jar ships zero block models. So the redirect
 * table this class produces ({@link FacadeStateManager#stackRedirects}) can only ever
 * be computed client-side.
 *
 * <p>But {@link FacadeStateManager#stackRedirects} is read by
 * {@link buildcraft.silicon.recipe.FacadeAssemblyRecipes} during the
 * <em>server-authoritative</em> assembly-table tick. The two only line up when the
 * logical server runs in the same JVM as the client that computed the table — i.e.
 * single-player or a LAN host. On a dedicated server (or for a LAN guest) the server's
 * copy of the field is whatever <em>that</em> process computed, which for a model-less
 * dedicated server is always empty.
 *
 * <p>To keep that honest, redirect <em>computation</em> (here, needs models) is split
 * from redirect <em>publication</em> ({@link #applyRedirectAuthority()}, gated on
 * {@link net.minecraft.client.Minecraft#hasSingleplayerServer()}). The invariant both
 * sides rely on is: <b>{@code stackRedirects} is non-empty only when the server reading
 * it will actually honor it.</b> A client connected to a dedicated server computes its
 * redirects (for its own deduped list) but does <em>not</em> publish them into
 * {@code stackRedirects}, so no client surface can advertise a redirect the server will
 * reject. See the {@code todos.md} "Facade redirects are client-only" entry for the
 * full rationale and the rejected alternatives (server-side JSON resolution, shipped
 * data table).
 */
@SuppressWarnings("deprecation")
public class FacadeDeduplicator {
    private static final boolean DEBUG = BCDebugging.shouldDebugLog("silicon.facade");
    private static final RandomSource RANDOM = RandomSource.create(42L);

    /**
     * The redirect table computed by the last {@link #deduplicateVisuallyIdentical} pass,
     * held separately from the published {@link FacadeStateManager#stackRedirects} so that
     * the authority decision ({@link #applyRedirectAuthority()}) can be re-evaluated at every
     * world join without recomputing (models don't change between an SP disconnect and a
     * dedicated-server join in the same client process, but the authority does). Empty until
     * the first dedup pass; never null.
     */
    private static volatile Map<ItemStackKey, List<FacadeBlockStateInfo>> computedRedirects = Map.of();

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
    //? if >=1.21.10 {
    public static void deduplicateVisuallyIdentical(Map<BlockState, BlockStateModel> blockStateModels) {
    //?} else {
    /*public static void deduplicateVisuallyIdentical(Map<BlockState, net.minecraft.client.resources.model.BakedModel> blockStateModels) {*/
    //?}
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

            //? if >=1.21.10 {
            BlockStateModel model = blockStateModels.get(info.state);
            //?} else {
            /*net.minecraft.client.resources.model.BakedModel model = blockStateModels.get(info.state);*/
            //?}
            if (model == null) {
                nullFingerprints++;
                continue;
            }

            String fingerprint = computeTextureFingerprint(info.state, model);
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
        //? if >=1.21.10 {
        for (Map.Entry<BlockState, BlockStateModel> modelEntry : blockStateModels.entrySet()) {
        //?} else {
        /*for (Map.Entry<BlockState, net.minecraft.client.resources.model.BakedModel> modelEntry : blockStateModels.entrySet()) {*/
        //?}
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

            //? if >=1.21.10 {
            BlockStateModel model = modelEntry.getValue();
            //?} else {
            /*net.minecraft.client.resources.model.BakedModel model = modelEntry.getValue();*/
            //?}
            if (model == null) continue;

            String fingerprint = computeTextureFingerprint(state, model);
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

        // Freeze the inner Lists and publish the visual-dedup snapshots atomically (volatile writes).
        // Publish order: validFacadeStates → stackFacades. A reader that grabs each field via separate
        // volatile reads might see a mid-publish state across the two fields; we accept that brief
        // inconsistency (a worst-case Assembly Table tick gets one stale read). The fields are never
        // *individually* mid-mutation, so no CME can occur.
        //
        // validFacadeStates + stackFacades are the visual-dedup result — the de-duplicated facade list
        // that is BuildCraft's core facade feature, and they publish unconditionally (every client,
        // single-player or connected to a dedicated server, dedups its own displayed list).
        //
        // stackRedirects is held back: redirects are only *valid to publish* when this client's JVM is
        // also running the logical server (see applyRedirectAuthority). We stash the computed table in
        // computedRedirects and let the authority gate decide whether it reaches the live field. Calling
        // applyRedirectAuthority() here covers the in-game F3+T re-bake case (already logged in, authority
        // known); the login path calls it again after ensureInitialized() for the fresh-join case.
        Map<ItemStackKey, List<FacadeBlockStateInfo>> publishedStackFacades = new HashMap<>(nextStackFacades.size());
        for (Map.Entry<ItemStackKey, List<FacadeBlockStateInfo>> e : nextStackFacades.entrySet()) {
            publishedStackFacades.put(e.getKey(), List.copyOf(e.getValue()));
        }
        Map<ItemStackKey, List<FacadeBlockStateInfo>> frozenRedirects = new HashMap<>(nextStackRedirects.size());
        for (Map.Entry<ItemStackKey, List<FacadeBlockStateInfo>> e : nextStackRedirects.entrySet()) {
            frozenRedirects.put(e.getKey(), List.copyOf(e.getValue()));
        }
        FacadeStateManager.validFacadeStates = Collections.unmodifiableSortedMap(nextValid);
        FacadeStateManager.stackFacades = Map.copyOf(publishedStackFacades);
        computedRedirects = Map.copyOf(frozenRedirects);
        applyRedirectAuthority();
    }

    /**
     * Publishes the computed redirect table into the live, server-read
     * {@link FacadeStateManager#stackRedirects} field <em>iff</em> this client's JVM is also running the
     * logical server it's connected to ({@link net.minecraft.client.Minecraft#hasSingleplayerServer()} —
     * true for single-player and for the host of an "Open to LAN" world). Otherwise it clears the field.
     *
     * <p>This is the enforcement point for the invariant documented on the class: {@code stackRedirects}
     * is non-empty only when the server that reads it shares this JVM and therefore honors it. On a
     * dedicated server the server process computes no redirects (no models) and its field stays empty;
     * a client connected to that server still computes its own redirects for its deduped display list,
     * but this method keeps them out of the field so nothing can offer a craft the server will reject.
     *
     * <p>Must run on the client thread. Safe to call repeatedly — it's a pure function of
     * {@code computedRedirects} and the current connection state, so it's the right thing to re-run at
     * every world join (the authority can flip between an SP session and a later dedicated-server join in
     * the same client process, with no model re-bake in between).
     */
    public static void applyRedirectAuthority() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean authoritative = mc != null && mc.hasSingleplayerServer();
        if (authoritative) {
            FacadeStateManager.stackRedirects = computedRedirects;
            if (DEBUG) {
                BCLog.logger.info("[silicon.facade] Integrated server present — published "
                    + computedRedirects.size() + " facade recipe redirects.");
            }
        } else {
            FacadeStateManager.stackRedirects = Map.of();
            if (DEBUG) {
                BCLog.logger.info("[silicon.facade] No integrated server (dedicated/LAN-guest) — "
                    + "facade recipe redirects withheld; the server owns its own (empty) table.");
            }
        }
    }

    /**
     * Computes a texture fingerprint for a BlockStateModel by examining its quads
     * on all 6 faces. Uses the NeoForge 1.21.11 collectParts/SimpleModelWrapper API.
     * Returns a canonical string of sorted texture names, or null if no quads found.
     *
     * <p>{@code state} is the blockstate the {@code model} was looked up for. On the modern
     * ({@code >=1.21.10}) nodes the model is already state-resolved and {@code collectParts}
     * needs no state, so it is unused there; on 1.21.1 it is forwarded into the legacy
     * {@code BakedModel.getQuads(state, ...)} call — see {@link #getQuadsFromModel}.
     */
    //? if >=1.21.10 {
    private static String computeTextureFingerprint(BlockState state, BlockStateModel model) {
    //?} else {
    /*private static String computeTextureFingerprint(BlockState state, net.minecraft.client.resources.model.BakedModel model) {*/
    //?}
        try {
            Set<String> textures = new LinkedHashSet<>();

            for (Direction dir : Direction.values()) {
                List<BakedQuad> quads = getQuadsFromModel(state, model, dir);
                for (BakedQuad quad : quads) {
                    //? if >=26.1 {
                    textures.add(dir.name() + ":" + quad.materialInfo().sprite().contents().name().toString());
                    //?} elif >=1.21.10 {
                    /*textures.add(dir.name() + ":" + quad.sprite().contents().name().toString());*/
                    //?} else {
                    /*textures.add(dir.name() + ":" + quad.getSprite().contents().name().toString());*/
                    //?}
                }
            }
            // Also check null-direction (general/unculled quads)
            List<BakedQuad> generalQuads = getQuadsFromModel(state, model, null);
            for (BakedQuad quad : generalQuads) {
                //? if >=26.1 {
                textures.add("GENERAL:" + quad.materialInfo().sprite().contents().name().toString());
                //?} elif >=1.21.10 {
                /*textures.add("GENERAL:" + quad.sprite().contents().name().toString());*/
                //?} else {
                /*textures.add("GENERAL:" + quad.getSprite().contents().name().toString());*/
                //?}
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
    //? if >=1.21.10 {
    private static List<BakedQuad> getQuadsFromModel(BlockState state, BlockStateModel model, Direction side) {
    //?} else {
    /*private static List<BakedQuad> getQuadsFromModel(BlockState state, net.minecraft.client.resources.model.BakedModel model, Direction side) {*/
    //?}
        //? if >=26.1 {
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
        //?} elif >=1.21.10 {
        /*List<BlockModelPart> parts = new ArrayList<>();
        model.collectParts(RANDOM, parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockModelPart part : parts) {
            if (part instanceof net.minecraft.client.renderer.block.model.SimpleModelWrapper smw) {
                QuadCollection qc = smw.quads();
                result.addAll(qc.getQuads(side));
            }
        }
        return result;*/
        //?} else {
        /*// 1.21.1: BakedModel.getQuads needs the REAL block state, not null. Vanilla SimpleBakedModel
        // ignores it, but (a) multipart models evaluate their selectors against it and (b) third-party
        // IDynamicBakedModels may dereference it without a null-check — e.g. Modular Machinery Reborn's
        // hatch model NPEs on state.hasProperty(...) and log-spams once per face per hatch block at every
        // world join (issue #24). The state is on hand at both call sites; forward it, mirroring
        // PlugBakerFacade.getQuadsFromModel which already fixed this for facade rendering.
        return model.getQuads(state, side, RANDOM);*/
        //?}
    }
}
