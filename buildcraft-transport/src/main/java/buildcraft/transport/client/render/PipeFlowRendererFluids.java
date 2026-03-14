/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.transport.pipe.flow.PipeFlowFluids;

/** Renders fluids flowing through fluid pipes.
 *
 *  The 1.12.2 version used FluidRenderer with GL state management and a
 *  separate Tessellator. In NeoForge 1.21.11, fluid rendering is done
 *  through VertexConsumer and the FluidRenderer lib is not yet ported.
 *
 *  This is currently a stub — fluid pipe content won't render visually yet,
 *  but the infrastructure is wired so it will activate once FluidRenderer
 *  is ported to the lib module. */
public enum PipeFlowRendererFluids implements IPipeFlowRenderer<PipeFlowFluids> {
    INSTANCE;

    @Override
    public void render(PipeFlowFluids flow, double x, double y, double z, float partialTicks, VertexConsumer bb) {
        // TODO: Port FluidRenderer from buildcraft-lib and implement fluid pipe rendering
        // The 1.12.2 version renders fluid volumes per-face and center section
        // using FluidRenderer.renderFluid() with FluidSpriteType.FROZEN
    }
}
