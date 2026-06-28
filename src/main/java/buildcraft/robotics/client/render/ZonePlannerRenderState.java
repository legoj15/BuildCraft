/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * Render state snapshot for Zone Planner block entities. Minimal — the actual terrain is sampled from the
 * client level in {@link RenderZonePlanner} each frame (the data is already client-side cached). Mirrors
 * {@code ArchitectTableRenderState}; on 1.21.1 (no render-state model) this is an unused plain holder.
 */
//? if >=1.21.10 {
public class ZonePlannerRenderState extends BlockEntityRenderState {
//?} else {
/*public class ZonePlannerRenderState {*/
//?}
}
