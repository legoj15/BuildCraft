/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.Set;

import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.client.model.ModelHolderVariable;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.node.value.NodeVariableDouble;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;

import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileDynamoMJ;

/** Defines the variable model holders and expression variables for all engine types.
 * Provides getXxxEngineQuads() methods that set variables from tile entity state
 * and return baked quads for the renderer.
 *
 * <p><b>Bake split.</b> The JSON variable model would otherwise re-bake every cutout element of every visible engine
 * every render frame. Only the {@code base_moving} and {@code chamber} elements actually animate (their geometry
 * references {@code progress}); {@code base} and {@code trunk} depend only on heat stage and orientation. Each engine
 * tile owns an {@link buildcraft.lib.client.render.EngineModelCache} that bakes those two groups separately and re-bakes
 * a group only when its inputs change. An idle engine re-bakes nothing; a running engine re-bakes only the moving
 * elements. The result is bit-identical to the wholesale bake — {@code progress} is keyed with exact {@code double}
 * equality, never quantised. */
public class BCEnergyModels {
    private static final NodeVariableDouble ENGINE_PROGRESS;
    private static final NodeVariableObject<EnumPowerStage> ENGINE_STAGE;
    private static final NodeVariableObject<Direction> ENGINE_FACING;

    private static final ModelHolderVariable ENGINE_STONE;
    private static final ModelHolderVariable ENGINE_IRON;
    private static final ModelHolderVariable ENGINE_FE;
    private static final ModelHolderVariable ENGINE_DYNAMO;

    /** The cutout elements of {@code engine_base.json}/{@code engine_dynamo.json} whose geometry references the
     * {@code progress} variable — the animated piston parts. The complement (static base + heat trunk) is re-baked
     * only on a stage/facing change. */
    static final Set<String> ANIMATED_ELEMENTS = Set.of("base_moving", "chamber");

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

    /** Writes the three engine expression variables. Shared by all engine quad methods below. */
    private static void applyVariables(double progress, EnumPowerStage stage, Direction facing) {
        ENGINE_PROGRESS.value = progress;
        ENGINE_STAGE.value = stage;
        ENGINE_FACING.value = facing;
    }

    private static MutableQuad[] getEngineQuads(ModelHolderVariable model, TileEngineBase_BC8 tile,
        float partialTicks) {
        return tile.clientModelCache.getQuads(model, tile.clientModelData,
            BCEnergyModels::applyVariables, ANIMATED_ELEMENTS,
            tile.getProgressClient(partialTicks), tile.getPowerStage(), tile.getOrientation());
    }

    public static MutableQuad[] getStoneEngineQuads(TileEngineStone_BC8 tile, float partialTicks) {
        return getEngineQuads(ENGINE_STONE, tile, partialTicks);
    }

    public static MutableQuad[] getIronEngineQuads(TileEngineIron_BC8 tile, float partialTicks) {
        return getEngineQuads(ENGINE_IRON, tile, partialTicks);
    }

    public static MutableQuad[] getFeEngineQuads(TileEngineFE tile, float partialTicks) {
        return getEngineQuads(ENGINE_FE, tile, partialTicks);
    }

    public static MutableQuad[] getDynamoQuads(TileDynamoMJ tile, float partialTicks) {
        return getEngineQuads(ENGINE_DYNAMO, tile, partialTicks);
    }
}
