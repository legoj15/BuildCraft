/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.api.enums.EnumPowerStage;

/**
 * Render state snapshot for engine block entities.
 * Fields are populated each frame by TileEngineBase_BC8.collectRenderState().
 * (1.21.1 has no render-state model; this stays a plain holder there and is unused by the direct render().)
 */
//? if >=1.21.10 {
public class EngineRenderState extends BlockEntityRenderState {
//?} else {
/*public class EngineRenderState {*/
//?}
    public BlockPos blockPos;
    public float progress;
    public EnumPowerStage powerStage = EnumPowerStage.BLUE;
    public Direction facing = Direction.UP;
}
