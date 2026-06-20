/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import java.util.function.Function;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.DepthStencilState;
//?}
//? if >=1.21.10 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}

//? if >=1.21.11 {
import net.minecraft.util.Util;
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
//?}
// RenderType: `rendertype.` subpackage on 1.21.11+, moved by the build-script regex
// replacement to `net.minecraft.client.renderer.RenderType` on the 1.21.10 node.
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}
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
    //? if >=1.21.10 {
    public static final RenderPipeline LED_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("buildcraftunofficial", "pipeline/led"))
            //? if >=26.1 {
            .withDepthStencilState(DepthStencilState.DEFAULT)
            //?} else {
            /*.withDepthWrite(true)*/
            //?}
            .build();
    //?}

    //? if >=1.21.11 {
    private static final RenderType LED = RenderType.create(
            "buildcraft:led",
            RenderSetup.builder(LED_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );
    //?} elif >=1.21.10 {
    /*private static final RenderType LED = RenderType.create(
            "buildcraft:led", 1536, false, false, LED_PIPELINE,
            RenderType.CompositeState.builder()
                    .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)
    );*/
    //?} else {
    /*// 1.21.1: no RenderPipeline GPU API — build the LED type the classic way. Mirrors LED_PIPELINE's
    // state: position_color shader, TRANSLUCENT blend (vanilla DEBUG_FILLED), LEQUAL depth WITH depth
    // write (default write-mask, so LEDs occlude each other), view-offset-z layering for face-flush quads.
    private static final RenderType LED = RenderType.create(
            "buildcraft:led",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLayeringState(net.minecraft.client.renderer.RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)
    );*/
    //?}

    /**
     * Opaque sibling of {@link #LED}: the same depth-writing {@link #LED_PIPELINE}, minus the LED's
     * view-offset Z layering (which only matters for quads mounted flush on a block face). For
     * free-standing solid debug geometry — see {@link buildcraft.lib.debug.DebugRenderHelper}.
     */
    //? if >=1.21.11 {
    private static final RenderType DEBUG_SOLID = RenderType.create(
            "buildcraft:debug_solid",
            RenderSetup.builder(LED_PIPELINE).createRenderSetup()
    );
    //?} elif >=1.21.10 {
    /*private static final RenderType DEBUG_SOLID = RenderType.create(
            "buildcraft:debug_solid", 1536, false, false, LED_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false)
    );*/
    //?} else {
    /*// 1.21.1: classic build of the same state as LED, minus the view-offset-z layering.
    private static final RenderType DEBUG_SOLID = RenderType.create(
            "buildcraft:debug_solid",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );*/
    //?}

    //? if <1.21.10 {
    /*// 1.21.1 only: vanilla's debugFilledBox() is TRIANGLE_STRIP here, but our debug cubes
    // (RenderPartCube) emit QUADS — feeding QUADS to a TRIANGLE_STRIP type collapses every face into
    // degenerate triangles. This is debugFilledBox's exact composite state with the mode corrected to
    // QUADS (POSITION_COLOR, translucent, view-offset-Z layering). 26.1.2/1.21.11 already use a QUADS
    // debugFilledBox; 1.21.10 uses the QUADS debugQuads() — see debugFilled().
    private static final RenderType DEBUG_FILLED = RenderType.create(
            "buildcraft:debug_filled",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_SHADER)
                    .setLayeringState(net.minecraft.client.renderer.RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );*/
    //?}

    public static RenderType led() {
        return LED;
    }

    /**
     * Vanilla's translucent, vertex-coloured filled-box render type. Used by the advanced-debug
     * overlay ({@link buildcraft.lib.debug.DebugRenderHelper}) — a box's per-vertex alpha controls
     * whether it reads as a translucent volume or a solid marker.
     */
    public static RenderType debugFilled() {
        //? if >=1.21.11 {
        return RenderTypes.debugFilledBox();
        //?} elif >=1.21.10 {
        /*// 1.21.10's debugFilledBox() is TRIANGLE_STRIP; our cubes (RenderPartCube) emit QUADS, so
        // use the QUADS-mode debugQuads() instead to avoid collapsing every face into degenerate tris.
        return RenderType.debugQuads();*/
        //?} else {
        /*return DEBUG_FILLED;*/
        //?}
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
    //? if >=26.1 {
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
    //?} elif >=1.21.11 {
    /*private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(
            texture -> RenderType.create(
                    "buildcraft:entity_translucent_cull",
                    RenderSetup.builder(RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .setOutline(RenderSetup.OutlineProperty.NONE)
                            .createRenderSetup()));*/
    //?} else {
    /*// 1.21.10: no RenderSetup; the vanilla itemEntityTranslucentCull factory (memoised
    // internally) IS the ITEM_ENTITY_TRANSLUCENT_CULL-based culling translucent type.
    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = RenderType::itemEntityTranslucentCull;*/
    //?}

    public static RenderType entityTranslucentCull(Identifier texture) {
        return ENTITY_TRANSLUCENT_CULL.apply(texture);
    }

    // ─── Vanilla render-type accessors ──────────────────────────────────────────
    // The stored render types live on RenderTypes (26.1 / 1.21.11) vs back on RenderType
    // (1.21.10). Routing the whole codebase through these wrappers keeps that single cross-line
    // divergence here instead of scattered across ~13 renderers (the class's stated purpose).

    public static RenderType entityCutout(Identifier texture) {
        //? if >=1.21.11 {
        return RenderTypes.entityCutout(texture);
        //?} else {
        /*return RenderType.entityCutout(texture);*/
        //?}
    }

    public static RenderType entityTranslucent(Identifier texture) {
        //? if >=1.21.11 {
        return RenderTypes.entityTranslucent(texture);
        //?} else {
        /*return RenderType.entityTranslucent(texture);*/
        //?}
    }

    public static RenderType entitySolid(Identifier texture) {
        //? if >=1.21.11 {
        return RenderTypes.entitySolid(texture);
        //?} else {
        /*return RenderType.entitySolid(texture);*/
        //?}
    }

    public static RenderType lines() {
        //? if >=1.21.11 {
        return RenderTypes.lines();
        //?} else {
        /*return RenderType.lines();*/
        //?}
    }

    /** Cull-back-face cutout for the given texture. 26.1 has a dedicated {@code entityCutoutCull};
     * 1.21.11 / 1.21.10 fold it into {@code entityCutout}. */
    public static RenderType entityCutoutCull(Identifier texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutoutCull(texture);
        //?} elif >=1.21.11 {
        /*return RenderTypes.entityCutout(texture);*/
        //?} else {
        /*return RenderType.entityCutout(texture);*/
        //?}
    }

    /**
     * Block-atlas cutout sheet — the render type vanilla's {@code Sheets.cutoutBlockSheet()} returned
     * (={@code RenderTypes.entityCutoutCull(LOCATION_BLOCKS)}). 26.2 dropped that convenience method, so
     * reproduce it from the underlying factory there; pre-26.2 nodes keep the original call unchanged.
     */
    public static RenderType cutoutBlockSheet() {
        //? if >=26.2 {
        /*return RenderTypes.entityCutoutCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);*/
        //?} else {
        return net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
        //?}
    }

    /** Translucent sheet for item-layer overlays: 1.21.11+ has a block-item variant; 1.21.10 the plain item sheet. */
    public static RenderType translucentItemSheet() {
        //? if >=1.21.11 {
        return net.minecraft.client.renderer.Sheets.translucentBlockItemSheet();
        //?} else {
        /*return net.minecraft.client.renderer.Sheets.translucentItemSheet();*/
        //?}
    }

    private BCLibRenderTypes() {}
}
