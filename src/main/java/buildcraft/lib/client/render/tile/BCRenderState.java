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
 * Shared, empty render-state snapshot for BuildCraft block-entity renderers whose BER looks its tile up
 * from the level each frame (via the inherited {@code blockPos}) and reads all animation data live — so
 * nothing needs to be copied into the render state during {@code extractRenderState}. The engine, tank,
 * pump, mining well, distiller, heat exchanger, quarry, filler, architect table and zone-planner BERs all
 * use this one type instead of each declaring its own byte-identical empty subclass.
 *
 * <p><b>This type must never declare a field.</b> The default {@code extractRenderState} populates the
 * INHERITED {@code blockPos} (and crumbling overlay) via {@code BlockEntityRenderState.extractBase}. A
 * subclass — or this class — that re-declared an inherited field would shadow the populated one behind
 * static-typed access, leaving it null and NPE-ing on world load. So this only ever ADDS nothing; a BER
 * that genuinely needs per-frame state (e.g. {@code PipeHolderRenderState}) keeps its own class.
 *
 * <p>On 1.21.1 there is no render-state model, so this is an unused plain holder there (those BERs render
 * directly via the classic {@code render(...)} path and never construct it).
 */
//? if >=1.21.10 {
public class BCRenderState extends BlockEntityRenderState {
//?} else {
/*public class BCRenderState {*/
//?}
}
