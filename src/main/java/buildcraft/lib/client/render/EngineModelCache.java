/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render;

import java.util.Set;

import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.client.model.ModelHolderVariable;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.data.ModelVariableData;

/** Per-engine-tile cache of the baked engine model, split into two independently-keyed groups so that an idle engine
 * skips the whole bake and a running engine only re-bakes its moving elements.
 *
 * <p>The engine JSON model ({@code compat_models/engine_base.json}) has four cutout elements:
 * <ul>
 * <li><b>animated</b> ({@code base_moving}, {@code chamber}) — geometry references the {@code progress} variable, so it
 * must be re-baked whenever {@code (progress, facing)} changes. For a running engine {@code progress} changes every
 * frame; for an idle engine it is constant and the group is not re-baked.</li>
 * <li><b>static</b> ({@code base}, {@code trunk}) — geometry depends only on {@code stage} (heat level) and the
 * {@code builtin:rotate_facing} rule, so it is re-baked only when {@code (stage, facing)} changes — both rare.</li>
 * </ul>
 * {@code facing} is in both keys because the rotate rule rotates the whole model.
 *
 * <p>{@code progress} is quantised to 1/16-block steps before it is used as a cache key or fed to the model — the
 * engine's {@code progress_size = progress * 15.99} expression has 1-pixel resolution, so a 1/16 step is the
 * animation's natural granularity. Quantising keeps the animated group's cache effective for a <em>running</em>
 * engine (its raw progress changes every frame, but the snapped value holds for several frames), not just an idle
 * one. All engine types share this quantised animation.
 *
 * <p>Each engine tile owns one instance, sitting next to its {@link ModelVariableData}. The instance is not
 * thread-safe; it is only ever touched from the client render thread. */
public final class EngineModelCache {

    /** Sentinel for "no value baked yet" — chosen so the first frame always misses. NaN never equals itself. */
    private static final double NO_PROGRESS = Double.NaN;

    /** Animation-progress quantisation: {@code progress} is snapped to this many steps before it becomes a cache key
     * or is fed to the model. 1/16 matches the engine model's {@code progress_size = progress * 15.99} expression,
     * which has 1-pixel resolution — so the snap is the animation's natural granularity, and it is what lets the
     * animated group's cache hit across the several frames a running engine spends within one step. */
    private static final int PROGRESS_STEPS = 16;

    // Static group: depends on (stage, facing).
    private MutableQuad[] staticQuads;
    private EnumPowerStage staticStage;
    private Direction staticFacing;

    // Animated group: depends on (progress, facing).
    private MutableQuad[] animatedQuads;
    private double animatedProgress = NO_PROGRESS;
    private Direction animatedFacing;

    /** Concatenation of static + animated; the array actually handed to the renderer. */
    private MutableQuad[] combined;

    /** The {@link ModelHolderVariable#getReloadGeneration()} the caches above were baked at. A mismatch means a
     * resource reload re-stitched the atlas and the cached quads' UVs/sprites are stale. */
    private int reloadGeneration = -1;

    /** Sets the three {@code ENGINE_*} expression variables on the model's function context. The model-type holder
     * ({@code BCCoreModels}/{@code BCEnergyModels}) owns the {@code NodeVariableDouble}/{@code NodeVariableObject}
     * instances, so it passes a setter that writes them. */
    public interface IEngineVariableSetter {
        void apply(double progress, EnumPowerStage stage, Direction facing);
    }

    /** Bakes (or returns the cached) full cutout quad array for an engine, re-baking only the groups whose inputs
     * changed since the previous call.
     *
     * @param model The engine's variable model holder.
     * @param clientModelData The tile's model variable node cache; {@link ModelVariableData#refresh()} is invoked
     *            before any bake, exactly as the pre-optimisation code did.
     * @param variableSetter Writes the {@code progress}/{@code stage}/{@code facing} expression variables; invoked
     *            once before each group that actually needs a re-bake.
     * @param animatedNames The element names that form the animated group (i.e. those referencing {@code progress}).
     * @param progress The raw animation progress from {@code tile.getProgressClient(partialTicks)}; quantised to
     *            1/16 internally.
     * @param stage The engine's current power/heat stage.
     * @param facing The engine's orientation.
     * @return The combined baked quad array. May be {@link MutableQuad#EMPTY_ARRAY} if the model failed to load. */
    public MutableQuad[] getQuads(ModelHolderVariable model, ModelVariableData clientModelData,
        IEngineVariableSetter variableSetter, Set<String> animatedNames,
        double progress, EnumPowerStage stage, Direction facing) {

        if (!model.hasBakedQuads() && model.getModel() == null) {
            // Model failed to load; getModel() prints the warning. Don't poison the cache.
            return MutableQuad.EMPTY_ARRAY;
        }

        int generation = model.getReloadGeneration();
        boolean reloaded = generation != reloadGeneration;
        if (reloaded) {
            reloadGeneration = generation;
            // Force both groups to miss.
            staticStage = null;
            staticFacing = null;
            animatedProgress = NO_PROGRESS;
            animatedFacing = null;
        }

        if (clientModelData.hasNoNodes()) {
            clientModelData.setNodes(model.createTickableNodes());
        }

        // Quantise progress to 1/16-block steps (see PROGRESS_STEPS): this is both the animated group's cache key
        // and the value the model bakes with. Snapping here — rather than comparing the raw double — is what lets
        // the cache hit across the several frames a running engine spends inside one step.
        int progressStep = Math.max(0, Math.min(PROGRESS_STEPS, (int) (progress * PROGRESS_STEPS + 0.5)));
        double quantProgress = (double) progressStep / PROGRESS_STEPS;

        boolean rebakedAny = false;

        // ── Static group: (stage, facing) ──
        if (staticQuads == null || stage != staticStage || facing != staticFacing) {
            variableSetter.apply(quantProgress, stage, facing);
            clientModelData.refresh();
            staticQuads = model.getCutoutQuads(animatedNames, true);
            staticStage = stage;
            staticFacing = facing;
            rebakedAny = true;
        }

        // ── Animated group: (quantised progress, facing) ──
        if (animatedQuads == null || quantProgress != animatedProgress || facing != animatedFacing) {
            variableSetter.apply(quantProgress, stage, facing);
            clientModelData.refresh();
            animatedQuads = model.getCutoutQuads(animatedNames, false);
            animatedProgress = quantProgress;
            animatedFacing = facing;
            rebakedAny = true;
        }

        if (rebakedAny || combined == null) {
            combined = concat(staticQuads, animatedQuads);
        }
        return combined;
    }

    private static MutableQuad[] concat(MutableQuad[] a, MutableQuad[] b) {
        if (a.length == 0) return b;
        if (b.length == 0) return a;
        MutableQuad[] out = new MutableQuad[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
