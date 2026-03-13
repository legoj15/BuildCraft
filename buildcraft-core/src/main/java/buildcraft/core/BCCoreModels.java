/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core;

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

    static {
        FunctionContext fnCtx = new FunctionContext(ExpressionCompat.ENUM_POWER_STAGE, DefaultContexts.createWithAll());
        ENGINE_PROGRESS = fnCtx.putVariableDouble("progress");
        ENGINE_STAGE = fnCtx.putVariableObject("stage", EnumPowerStage.class);
        ENGINE_FACING = fnCtx.putVariableObject("direction", Direction.class);
        ENGINE_WOOD = new ModelHolderVariable(
            "buildcraftcore:compat_models/engine_wood.json",
            fnCtx
        );
        ENGINE_CREATIVE = new ModelHolderVariable(
            "buildcraftcore:compat_models/engine_creative.json",
            fnCtx
        );
    }

    public static MutableQuad[] getWoodEngineQuads(TileEngineBase_BC8 tile, float partialTicks) {
        ENGINE_PROGRESS.value = tile.getProgressClient(partialTicks);
        ENGINE_STAGE.value = tile.getPowerStage();
        ENGINE_FACING.value = tile.getOrientation();
        if (tile.clientModelData.hasNoNodes()) {
            tile.clientModelData.setNodes(ENGINE_WOOD.createTickableNodes());
        }
        tile.clientModelData.refresh();
        return ENGINE_WOOD.getCutoutQuads();
    }

    public static MutableQuad[] getCreativeEngineQuads(TileEngineBase_BC8 tile, float partialTicks) {
        ENGINE_PROGRESS.value = tile.getProgressClient(partialTicks);
        ENGINE_STAGE.value = tile.getPowerStage();
        ENGINE_FACING.value = tile.getOrientation();
        if (tile.clientModelData.hasNoNodes()) {
            tile.clientModelData.setNodes(ENGINE_CREATIVE.createTickableNodes());
        }
        tile.clientModelData.refresh();
        return ENGINE_CREATIVE.getCutoutQuads();
    }
}
