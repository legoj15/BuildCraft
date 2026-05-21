/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.Arrays;
import java.util.stream.Collectors;

import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.client.model.ModelHolderVariable;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.json.JsonVariableModel;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.node.value.NodeVariableDouble;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.lib.misc.data.ModelVariableData;

import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileDynamoMJ;

/** Defines the variable model holders and expression variables for all engine types.
 * Provides getXxxEngineQuads() methods that set variables from tile entity state
 * and return baked quads for the renderer. */
public class BCEnergyModels {
    private static final NodeVariableDouble ENGINE_PROGRESS;
    private static final NodeVariableObject<EnumPowerStage> ENGINE_STAGE;
    private static final NodeVariableObject<Direction> ENGINE_FACING;

    private static final ModelHolderVariable ENGINE_STONE;
    private static final ModelHolderVariable ENGINE_IRON;
    private static final ModelHolderVariable ENGINE_FE;
    private static final ModelHolderVariable ENGINE_DYNAMO;

    static {
        FunctionContext fnCtx = new FunctionContext(ExpressionCompat.ENUM_POWER_STAGE, DefaultContexts.createWithAll());
        ENGINE_PROGRESS = fnCtx.putVariableDouble("progress");
        ENGINE_STAGE = fnCtx.putVariableObject("stage", EnumPowerStage.class);
        ENGINE_FACING = fnCtx.putVariableObject("direction", Direction.class);
        ENGINE_STONE = new ModelHolderVariable(
            "buildcraftunofficial:compat_models/engine_stone.json",
            fnCtx
        );
        ENGINE_IRON = new ModelHolderVariable(
            "buildcraftunofficial:compat_models/engine_iron.json",
            fnCtx
        );
        ENGINE_FE = new ModelHolderVariable(
            "buildcraftunofficial:compat_models/engine_rf.json",
            fnCtx
        );
        ENGINE_DYNAMO = new ModelHolderVariable(
            "buildcraftunofficial:compat_models/engine_dynamo.json",
            fnCtx
        );
    }

    // ─── Bake cache ──────────────────────────────────────────────────
    //
    // The JSON variable model rebakes every frame per engine (~22 MutableQuads
    // + their backing MutableVertex objects + the ArrayList + result array per
    // call). With several engines in view at 60 fps that's ~3 MB/s of GC churn
    // pure from the bake. We cache the result keyed by the only inputs the
    // model expressions look at — power stage, animation progress, and facing.
    //
    // Progress is quantised to 1/64 to bound the cache key's domain. The
    // piston is driven by the engine_base.json `progress_size = progress *
    // 15.99` expression, whose vertices bake to sub-pixel float coordinates;
    // 1/64 steps move the piston ~0.25 px each, which is smooth in motion.
    // (1/16 was too coarse at ~1 px per step — the piston visibly stair-
    // stepped, while the un-quantised core-engine path stayed smooth.)
    // Bounded by 6 (stage) × 65 (progress 0..64) × 6 (facing) = 2340 entries
    // per engine type; each entry holds the bake's MutableQuad[] reference, so
    // the steady-state memory cost is one MutableQuad[] per distinct (stage,
    // progress, facing) combination that has actually been seen, not the worst
    // case.
    //
    // The cached MutableQuad instances are shared across all engines of the
    // same type and state. RenderEngine_BC8 mutates `colour` (via
    // setCalculatedDiffuse) and `lighti` on each emitted quad, but those are
    // overwritten deterministically every render before the quad is read by
    // the vertex consumer, so cache reuse is safe.
    //
    // Cache invalidation happens automatically by comparing
    // `ModelHolderVariable.getModel()` (the raw JsonVariableModel reference)
    // against the one the cache was built against — `onTextureStitchPre`
    // nulls the holder's rawModel and `loadModelFromDisk` reassigns a fresh
    // instance, so a resource pack swap or atlas restitch produces a new
    // reference and the cache wipes itself on the next lookup.

    private static final int PROGRESS_QUANTIZATION = 64;
    private static final int FACING_COUNT = 6;
    private static final int PROGRESS_VALUES = PROGRESS_QUANTIZATION + 1;
    private static final int CACHE_SIZE =
        EnumPowerStage.values().length * PROGRESS_VALUES * FACING_COUNT;

    private static final class EngineQuadCache {
        final MutableQuad[][] entries = new MutableQuad[CACHE_SIZE][];
        JsonVariableModel lastRawModel;
    }

    private static final EngineQuadCache CACHE_STONE = new EngineQuadCache();
    private static final EngineQuadCache CACHE_IRON = new EngineQuadCache();
    private static final EngineQuadCache CACHE_FE = new EngineQuadCache();
    private static final EngineQuadCache CACHE_DYNAMO = new EngineQuadCache();

    private static int cacheKey(EnumPowerStage stage, int progressQuant, Direction facing) {
        return stage.ordinal() * PROGRESS_VALUES * FACING_COUNT
            + progressQuant * FACING_COUNT
            + facing.ordinal();
    }

    private static MutableQuad[] getEngineQuads(ModelHolderVariable model,
                                                EngineQuadCache cache,
                                                TileEngineBase_BC8 tile,
                                                float partialTicks) {
        JsonVariableModel rawModel = model.getModel();
        if (rawModel == null) return MutableQuad.EMPTY_ARRAY;

        // Detect resource reload (model reload) and wipe stale cache entries.
        // Cached MutableQuads embed atlas-mapped UVs and sprite references; a
        // new rawModel implies a fresh stitch, so the prior UVs are stale.
        if (rawModel != cache.lastRawModel) {
            Arrays.fill(cache.entries, null);
            cache.lastRawModel = rawModel;
        }

        float progress = tile.getProgressClient(partialTicks);
        EnumPowerStage stage = tile.getPowerStage();
        Direction facing = tile.getOrientation();

        int progressQuant = Math.max(0, Math.min(PROGRESS_QUANTIZATION,
            (int) (progress * PROGRESS_QUANTIZATION + 0.5f)));
        int key = cacheKey(stage, progressQuant, facing);

        MutableQuad[] cached = cache.entries[key];
        if (cached != null) {
            return cached;
        }

        // Cache miss: set the model variables to the quantised values, refresh
        // the tile's tickable nodes (a no-op for engines whose expressions are
        // pure functions of progress/stage/facing, but cheap to keep), and bake.
        ENGINE_PROGRESS.value = (double) progressQuant / PROGRESS_QUANTIZATION;
        ENGINE_STAGE.value = stage;
        ENGINE_FACING.value = facing;
        if (tile.clientModelData.hasNoNodes()) {
            tile.clientModelData.setNodes(model.createTickableNodes());
        }
        tile.clientModelData.refresh();

        MutableQuad[] quads = model.getCutoutQuads();
        cache.entries[key] = quads;
        return quads;
    }

    public static MutableQuad[] getStoneEngineQuads(TileEngineStone_BC8 tile, float partialTicks) {
        return getEngineQuads(ENGINE_STONE, CACHE_STONE, tile, partialTicks);
    }

    public static MutableQuad[] getIronEngineQuads(TileEngineIron_BC8 tile, float partialTicks) {
        return getEngineQuads(ENGINE_IRON, CACHE_IRON, tile, partialTicks);
    }

    public static MutableQuad[] getFeEngineQuads(TileEngineFE tile, float partialTicks) {
        return getEngineQuads(ENGINE_FE, CACHE_FE, tile, partialTicks);
    }

    public static MutableQuad[] getDynamoQuads(TileDynamoMJ tile, float partialTicks) {
        return getEngineQuads(ENGINE_DYNAMO, CACHE_DYNAMO, tile, partialTicks);
    }
}
