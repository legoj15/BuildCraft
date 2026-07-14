/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.model;

import java.util.ArrayList;
import java.util.List;

//? if >=26.1 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
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

/**
 * The single home for extracting {@code BakedQuad}s from a resolved block(-state) model across the render
 * cliff. Before this existed the same directive ladder was copy-pasted byte-for-byte into
 * {@link buildcraft.silicon.client.FacadeDeduplicator} and {@code PlugBakerFacade}; the modern-node half
 * also lived a third time (in a different, all-sides shape) in {@code PreviewBlockModelRenderer}. The
 * model APIs diverge by MC line, so keeping the fork in one place means one file to fix when the 26.x
 * pre-release model API churns again.
 *
 * <p>Three branches:
 * <ul>
 *   <li><b>&gt;=26.1</b> — {@code BlockStateModel.collectParts} → {@code SimpleModelWrapper.quads()} →
 *       {@code QuadCollection.getQuads(side)} (dispatch/geometry package split).</li>
 *   <li><b>&gt;=1.21.10</b> (1.21.10 / 1.21.11) — the same {@code collectParts} shape but the pre-26.1
 *       {@code BlockModelPart} / {@code SimpleModelWrapper} / {@code QuadCollection} types.</li>
 *   <li><b>&lt;1.21.10</b> (1.21.1) — the classic {@code BakedModel.getQuads(state, side, random)}. The
 *       {@code state} MUST be the real block state, never {@code null}: MULTIPART models evaluate their
 *       selectors against it and third-party {@code IDynamicBakedModel}s dereference it un-guarded (a
 *       null there was bug #24 — invisible mushroom facades + Modular Machinery Reborn log spam).</li>
 * </ul>
 *
 * <p><b>The {@link RandomSource} is owned by the caller, deliberately.</b> {@code collectParts} (and the
 * 1.21.1 {@code getQuads}) draw on it to pick weighted/rotated model variants, so the callers keep their
 * own instances (the facade dedup + facade baker use a fixed seed for a stable fingerprint; the preview
 * seeds 42L for flicker-free variant selection). Passing it in — rather than the helper holding a static
 * one — is what keeps every call site's quad set (and its order) byte-identical to the pre-hoist code; the
 * facade texture-fingerprint depends on that stability.
 */
@SuppressWarnings("deprecation")
public final class BlockModelQuadExtractor {

    private BlockModelQuadExtractor() {}

    /**
     * Extracts the {@code BakedQuad}s for a single face ({@code side}, or {@code null} for the
     * general/unculled quads) of {@code model}. On the modern nodes only {@code SimpleModelWrapper} parts
     * contribute (the shape the facade system bakes/compares); on 1.21.1 the real {@code state} is
     * forwarded into {@code BakedModel.getQuads} (see the class note).
     */
    //? if >=1.21.10 {
    public static List<BakedQuad> getQuadsFromModel(BlockState state, BlockStateModel model, Direction side, RandomSource random) {
    //?} else {
    /*public static List<BakedQuad> getQuadsFromModel(BlockState state, BakedModel model, Direction side, RandomSource random) {*/
    //?}
        //? if >=26.1 {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
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
        model.collectParts(random, parts);
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
        // ignores it, but MULTIPART models evaluate their selectors against it and third-party
        // IDynamicBakedModels may dereference it without a null-check (issue #24 — invisible mushroom-block
        // facades + Modular Machinery Reborn NPE log-spam once per face per hatch at every world join).
        return model.getQuads(state, side, random);*/
        //?}
    }

    //? if >=26.1 {
    /**
     * Extracts every quad of {@code model} (all six faces plus the general/unculled bucket) flattened into
     * one list, for whole-block rendering (the blueprint/architect cell preview). Unlike
     * {@link #getQuadsFromModel} this walks <i>all</i> {@code BlockStateModelPart}s via
     * {@code part.getQuads(dir)} — it must not filter to {@code SimpleModelWrapper}, or a block whose model
     * bakes to another part type would preview with missing geometry. 26.1+ only: below the cliff the
     * preview uses {@code BlockRenderDispatcher.renderSingleBlock} instead, so no quad list is built there.
     */
    public static List<BakedQuad> getAllQuads(BlockStateModel model, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction dir : Direction.values()) {
                quads.addAll(part.getQuads(dir));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }
    //?}
}
