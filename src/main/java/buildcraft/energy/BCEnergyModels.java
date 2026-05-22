/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.client.model.ModelHolderVariable;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.tile.EngineModelCache;
import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.node.value.NodeVariableDouble;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;

import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileDynamoMJ;

/** Defines the variable model holders and expression variables for the energy-subsystem engine
 * types (Stirling, Combustion, FE, MJ Dynamo). Each engine renders through a shared
 * {@link EngineModelCache}; see that class for the bake-cache and quantisation rationale. */
public class BCEnergyModels {
    private static final NodeVariableDouble ENGINE_PROGRESS;
    private static final NodeVariableObject<EnumPowerStage> ENGINE_STAGE;
    private static final NodeVariableObject<Direction> ENGINE_FACING;

    private static final EngineModelCache ENGINE_STONE;
    private static final EngineModelCache ENGINE_IRON;
    private static final EngineModelCache ENGINE_FE;
    private static final EngineModelCache ENGINE_DYNAMO;

    static {
        FunctionContext fnCtx = new FunctionContext(ExpressionCompat.ENUM_POWER_STAGE, DefaultContexts.createWithAll());
        ENGINE_PROGRESS = fnCtx.putVariableDouble("progress");
        ENGINE_STAGE = fnCtx.putVariableObject("stage", EnumPowerStage.class);
        ENGINE_FACING = fnCtx.putVariableObject("direction", Direction.class);
        ENGINE_STONE = new EngineModelCache(
            new ModelHolderVariable("buildcraftunofficial:compat_models/engine_stone.json", fnCtx),
            ENGINE_PROGRESS, ENGINE_STAGE, ENGINE_FACING);
        ENGINE_IRON = new EngineModelCache(
            new ModelHolderVariable("buildcraftunofficial:compat_models/engine_iron.json", fnCtx),
            ENGINE_PROGRESS, ENGINE_STAGE, ENGINE_FACING);
        ENGINE_FE = new EngineModelCache(
            new ModelHolderVariable("buildcraftunofficial:compat_models/engine_rf.json", fnCtx),
            ENGINE_PROGRESS, ENGINE_STAGE, ENGINE_FACING);
        ENGINE_DYNAMO = new EngineModelCache(
            new ModelHolderVariable("buildcraftunofficial:compat_models/engine_dynamo.json", fnCtx),
            ENGINE_PROGRESS, ENGINE_STAGE, ENGINE_FACING);
    }

    public static MutableQuad[] getStoneEngineQuads(TileEngineStone_BC8 tile, float partialTicks) {
        return ENGINE_STONE.getQuads(tile, partialTicks);
    }

    public static MutableQuad[] getIronEngineQuads(TileEngineIron_BC8 tile, float partialTicks) {
        return ENGINE_IRON.getQuads(tile, partialTicks);
    }

    public static MutableQuad[] getFeEngineQuads(TileEngineFE tile, float partialTicks) {
        return ENGINE_FE.getQuads(tile, partialTicks);
    }

    public static MutableQuad[] getDynamoQuads(TileDynamoMJ tile, float partialTicks) {
        return ENGINE_DYNAMO.getQuads(tile, partialTicks);
    }
}
