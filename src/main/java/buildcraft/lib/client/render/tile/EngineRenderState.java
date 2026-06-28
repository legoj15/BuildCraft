/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

//? if >=1.21.10 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * Render state snapshot for engine block entities. Minimal — {@code RenderEngine_BC8} looks the engine
 * tile up from the level each frame (via the inherited {@code blockPos}) and animates straight off it, so
 * no per-frame fields are copied here. On 1.21.1 (no render-state model) this is an unused plain holder.
 */
//? if >=1.21.10 {
public class EngineRenderState extends BlockEntityRenderState {
//?} else {
/*public class EngineRenderState {*/
//?}
}
