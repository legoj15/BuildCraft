/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core;

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

/** Defines the variable model holders and expression variables for core engine types
 * (redstone/wood engine and creative engine). */
public class BCCoreModels {
    private static final NodeVariableDouble ENGINE_PROGRESS;
    private static final NodeVariableObject<EnumPowerStage> ENGINE_STAGE;
    private static final NodeVariableObject<Direction> ENGINE_FACING;

    private static final ModelHolderVariable ENGINE_WOOD;
    private static final ModelHolderVariable ENGINE_CREATIVE;

    /** The cutout elements of {@code engine_base.json} whose geometry references the {@code progress} variable, i.e.
     * the animated piston parts. Everything else (the static base + heat trunk) is the complement. See
     * {@link buildcraft.lib.client.render.EngineModelCache} for why the bake is split this way. */
    static final Set<String> ANIMATED_ELEMENTS = Set.of("base_moving", "chamber");

    static {
        FunctionContext fnCtx = new FunctionContext(ExpressionCompat.ENUM_POWER_STAGE, DefaultContexts.createWithAll());
        ENGINE_PROGRESS = fnCtx.putVariableDouble("progress");
        ENGINE_STAGE = fnCtx.putVariableObject("stage", EnumPowerStage.class);
        ENGINE_FACING = fnCtx.putVariableObject("direction", Direction.class);
        ENGINE_WOOD = new ModelHolderVariable(
            "buildcraftunofficial:compat_models/engine_wood.json",
            fnCtx
        );
        ENGINE_CREATIVE = new ModelHolderVariable(
            "buildcraftunofficial:compat_models/engine_creative.json",
            fnCtx
        );
    }

    /** Writes the three engine expression variables. Shared by the two engine quad methods below. */
    private static void applyVariables(double progress, EnumPowerStage stage, Direction facing) {
        ENGINE_PROGRESS.value = progress;
        ENGINE_STAGE.value = stage;
        ENGINE_FACING.value = facing;
    }

    public static MutableQuad[] getWoodEngineQuads(TileEngineBase_BC8 tile, float partialTicks) {
        return tile.clientModelCache.getQuads(ENGINE_WOOD, tile.clientModelData,
            BCCoreModels::applyVariables, ANIMATED_ELEMENTS,
            tile.getProgressClient(partialTicks), tile.getPowerStage(), tile.getOrientation());
    }

    public static MutableQuad[] getCreativeEngineQuads(TileEngineBase_BC8 tile, float partialTicks) {
        return tile.clientModelCache.getQuads(ENGINE_CREATIVE, tile.clientModelData,
            BCCoreModels::applyVariables, ANIMATED_ELEMENTS,
            tile.getProgressClient(partialTicks), tile.getPowerStage(), tile.getOrientation());
    }
}
