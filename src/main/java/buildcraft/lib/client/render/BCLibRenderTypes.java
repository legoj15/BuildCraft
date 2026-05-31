/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import java.util.function.Function;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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

    /**
     * Opaque sibling of {@link #LED}: the same depth-writing {@link #LED_PIPELINE}, minus the LED's
     * view-offset Z layering (which only matters for quads mounted flush on a block face). For
     * free-standing solid debug geometry — see {@link buildcraft.lib.debug.DebugRenderHelper}.
     */
    private static final RenderType DEBUG_SOLID = RenderType.create(
            "buildcraft:debug_solid",
            RenderSetup.builder(LED_PIPELINE).createRenderSetup()
    );

    public static RenderType led() {
        return LED;
    }

    /**
     * Vanilla's translucent, vertex-coloured filled-box render type. Used by the advanced-debug
     * overlay ({@link buildcraft.lib.debug.DebugRenderHelper}) — a box's per-vertex alpha controls
     * whether it reads as a translucent volume or a solid marker.
     */
    public static RenderType debugFilled() {
        return RenderTypes.debugFilledBox();
    }

    /**
     * Opaque, depth-writing vertex-coloured QUADs — for solid debug geometry that must sort
     * correctly against the world. Unlike {@link #debugFilled()} (vanilla's debug filled-box type,
     * which disables depth writes for see-through translucent overlays), boxes drawn through this
     * write depth, so opaque markers occlude and are occluded by world geometry correctly.
     */
    public static RenderType debugSolid() {
        return DEBUG_SOLID;
    }

    /**
     * Back-face-culling sibling of vanilla's {@link RenderTypes#entityTranslucent} (which is
     * {@code NO_CULL}). Same {@code ENTITY_TRANSLUCENT_CULL} pipeline vanilla reserves for its
     * item-target translucent type, but built with the standard setup so it renders to the active
     * output target — usable in the picture-in-picture pass.
     * <p>
     * Needed by the blueprint pipe preview: pipe models emit front + inverted-"inside" coplanar
     * quad pairs (the {@code dupDarker} pass in {@code PipeBaseModelGenStandard}) and rely on
     * culling to drop the inside quad. The no-cull translucent type let both render, double-blending
     * the paint overlay. Memoised per texture, matching vanilla's render-type factories.
     */
    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(
            texture -> RenderType.create(
                    "buildcraft:entity_translucent_cull",
                    RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .setOutline(RenderSetup.OutlineProperty.NONE)
                            .createRenderSetup()));

    public static RenderType entityTranslucentCull(Identifier texture) {
        return ENTITY_TRANSLUCENT_CULL.apply(texture);
    }

    private BCLibRenderTypes() {}
}
