/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.List;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;

/**
 * A BlockStateModel for the pipe_holder block.
 *
 * <p>MC 26.1: DynamicBlockStateModel was removed from NeoForge. The vanilla
 * BlockStateModel.collectParts() no longer receives level/pos/state context,
 * making position-dependent chunk-mesh rendering impossible through this API.
 *
 * <p>This model delegates to the vanilla-baked pipe_holder model for particle
 * textures & ambient occlusion. All actual pipe geometry is rendered by the
 * BER (RenderPipeHolder) which has full tile entity context.
 *
 * <p>TODO: Investigate if NeoForge 26.1 provides an alternative hook for
 * context-dependent block model rendering (e.g. via ModelData).
 */
public class PipeBlockStateModel implements BlockStateModel {
    private final BlockStateModel vanillaDelegate;

    public PipeBlockStateModel(BlockStateModel vanillaDelegate) {
        this.vanillaDelegate = vanillaDelegate;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        // MC 26.1: No level/pos context available. Delegate to vanilla model
        // which provides the static pipe_holder.json geometry (particle sprite).
        // All dynamic pipe rendering is handled by the BER (RenderPipeHolder).
        vanillaDelegate.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return vanillaDelegate.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return vanillaDelegate.materialFlags();
    }
}
