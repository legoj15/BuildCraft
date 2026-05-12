/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Project-owned wrappers around vanilla render pipelines.
 * <p>
 * Centralising these here means the rest of the codebase refers to BuildCraft-named
 * {@link RenderType}s instead of vanilla {@code debug_*} names, so future Mojang
 * renames or behaviour tweaks land in one place.
 */
public final class BCLibRenderTypes {
    /**
     * Untextured, unlit, vertex-coloured QUADs with proper depth ordering.
     * <p>
     * Inherits {@code core/position_color} shaders + {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#POSITION_COLOR}
     * vertex format from vanilla's {@link RenderPipelines#DEBUG_FILLED_SNIPPET}, but
     * <strong>overrides depth state to {@link DepthStencilState#DEFAULT}</strong>
     * (depth test {@code LESS_THAN_OR_EQUAL} <em>with depth write enabled</em>).
     * <p>
     * Vanilla's {@code DEBUG_FILLED_BOX} disables depth write because it's intended
     * for debug-overlay rendering where the overlay shouldn't occlude itself; in our
     * case the LEDs are opaque indicators on a block face and we want adjacent LEDs
     * to correctly hide each other when viewed at grazing angles — which requires
     * depth writes so the GPU can z-test subsequent LED fragments against earlier
     * ones in the same batch.
     * <p>
     * Must be registered via {@link net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent}
     * — see {@link buildcraft.lib.client.BCLibClient#initClient}.
     */
    public static final RenderPipeline LED_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("buildcraftunofficial", "pipeline/led"))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    private static final RenderType LED = RenderType.create(
            "buildcraft:led",
            RenderSetup.builder(LED_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    public static RenderType led() {
        return LED;
    }

    private BCLibRenderTypes() {}
}
