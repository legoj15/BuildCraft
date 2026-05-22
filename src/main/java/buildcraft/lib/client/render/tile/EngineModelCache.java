/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import java.util.Arrays;

import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.client.model.ModelHolderVariable;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.json.JsonVariableModel;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.expression.node.value.NodeVariableDouble;
import buildcraft.lib.expression.node.value.NodeVariableObject;

/**
 * Per-engine-type bake cache for an animated JSON variable engine model.
 * <p>
 * One instance backs one engine model (stone, iron, FE, dynamo, wood, creative) and is shared by
 * every engine of that type in the world. Every BuildCraft engine — whatever subsystem it lives in
 * — renders its piston through one of these, so all engines animate identically.
 * <p>
 * <b>Why cache.</b> The JSON variable model rebakes from scratch on every call (~22 MutableQuads
 * plus their backing MutableVertex objects, an ArrayList, and the result array). With several
 * engines in view at 60 fps that is megabytes per second of pure GC churn. The bake result is
 * cached, keyed by the only inputs the model expressions read — power stage, animation progress,
 * and facing.
 * <p>
 * <b>Why quantise progress.</b> Progress is a continuous float, so it is quantised to 1/128 to
 * bound the cache key's domain. The piston is driven by {@code engine_base.json}'s
 * {@code progress_size = progress * 15.99} expression, whose vertices bake to sub-pixel float
 * coordinates; 1/128 steps move the piston ~0.125 px each, smooth in motion. (The cache shipped
 * at 1/16 — ~1 px per step, which visibly stair-stepped — then 1/64; 1/128 halves the step
 * again and brings the cached path level with the once-uncached core engines.)
 * <p>
 * The cache is bounded by 6 (stage) × 129 (progress 0..128) × 6 (facing) = 4644 {@code MutableQuad[]}
 * reference slots; each populated slot additionally holds one bake's {@code MutableQuad[]}. The
 * steady-state cost is one bake per distinct (stage, progress, facing) actually seen, not the
 * worst case.
 * <p>
 * The cached MutableQuad instances are shared across all engines of the same type and state.
 * {@code RenderEngine_BC8} mutates {@code colour} (via {@code setCalculatedDiffuse}) and
 * {@code lighti} on each emitted quad, but those are overwritten deterministically every render
 * before the quad is read by the vertex consumer, so cache reuse is safe.
 * <p>
 * Invalidation is automatic: a resource-pack swap or atlas restitch makes
 * {@link ModelHolderVariable#getModel()} return a fresh {@link JsonVariableModel} reference (its
 * {@code onTextureStitchPre} nulls the raw model and {@code loadModelFromDisk} reassigns a new
 * instance), and the cache wipes itself the next time it sees a model it was not built against.
 */
public final class EngineModelCache {

    private static final int PROGRESS_QUANTIZATION = 128;
    private static final int FACING_COUNT = 6;
    private static final int PROGRESS_VALUES = PROGRESS_QUANTIZATION + 1;
    private static final int CACHE_SIZE =
        EnumPowerStage.values().length * PROGRESS_VALUES * FACING_COUNT;

    private final ModelHolderVariable model;
    private final NodeVariableDouble progressVar;
    private final NodeVariableObject<EnumPowerStage> stageVar;
    private final NodeVariableObject<Direction> facingVar;

    private final MutableQuad[][] entries = new MutableQuad[CACHE_SIZE][];
    private JsonVariableModel lastRawModel;

    /**
     * @param model       the variable model to bake.
     * @param progressVar the model's {@code progress} variable — set per bake to the quantised progress.
     * @param stageVar    the model's {@code stage} variable.
     * @param facingVar   the model's {@code direction} variable.
     */
    public EngineModelCache(ModelHolderVariable model,
                            NodeVariableDouble progressVar,
                            NodeVariableObject<EnumPowerStage> stageVar,
                            NodeVariableObject<Direction> facingVar) {
        this.model = model;
        this.progressVar = progressVar;
        this.stageVar = stageVar;
        this.facingVar = facingVar;
    }

    private static int cacheKey(EnumPowerStage stage, int progressQuant, Direction facing) {
        return stage.ordinal() * PROGRESS_VALUES * FACING_COUNT
            + progressQuant * FACING_COUNT
            + facing.ordinal();
    }

    /** Bakes — or returns a cached bake of — this engine model for the tile's current render state. */
    public MutableQuad[] getQuads(TileEngineBase_BC8 tile, float partialTicks) {
        JsonVariableModel rawModel = model.getModel();
        if (rawModel == null) {
            return MutableQuad.EMPTY_ARRAY;
        }

        // Detect a resource reload (new raw model) and wipe stale cache entries: cached
        // MutableQuads embed atlas-mapped UVs and sprite references, so a fresh stitch makes
        // the prior UVs stale.
        if (rawModel != lastRawModel) {
            Arrays.fill(entries, null);
            lastRawModel = rawModel;
        }

        float progress = tile.getProgressClient(partialTicks);
        EnumPowerStage stage = tile.getPowerStage();
        Direction facing = tile.getOrientation();

        int progressQuant = Math.max(0, Math.min(PROGRESS_QUANTIZATION,
            (int) (progress * PROGRESS_QUANTIZATION + 0.5f)));
        int key = cacheKey(stage, progressQuant, facing);

        MutableQuad[] cached = entries[key];
        if (cached != null) {
            return cached;
        }

        // Cache miss: set the model variables to the quantised values, refresh the tile's
        // tickable nodes (a no-op for engines, whose expressions are pure functions of
        // progress/stage/facing, but cheap to keep), and bake.
        progressVar.value = (double) progressQuant / PROGRESS_QUANTIZATION;
        stageVar.value = stage;
        facingVar.value = facing;
        if (tile.clientModelData.hasNoNodes()) {
            tile.clientModelData.setNodes(model.createTickableNodes());
        }
        tile.clientModelData.refresh();

        MutableQuad[] quads = model.getCutoutQuads();
        entries[key] = quads;
        return quads;
    }
}
