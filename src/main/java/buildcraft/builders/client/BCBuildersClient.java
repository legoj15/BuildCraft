/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.BCBuildersEntities;
import buildcraft.builders.BCBuildersEventDist;
import buildcraft.builders.client.tooltip.BlueprintTooltipOverlay;
import buildcraft.builders.client.tooltip.SchematicSingleTooltipOverlay;
import buildcraft.builders.gui.GuiArchitectTable;
import buildcraft.builders.gui.GuiElectronicLibrary;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.gui.GuiReplacer;
import buildcraft.builders.gui.GuiBuilder;
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCBuildersClient {
    public static void initClient(IEventBus modEventBus) {
        registerClientContent(ClientPlatform.sinks());
        NeoForge.EVENT_BUS.addListener(
                //? if >=26.1 {
                net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent.class,
                //?} elif >=1.21.10 {
                /*RenderLevelStageEvent.AfterTranslucentBlocks.class,*/
                //?} else {
                /*RenderLevelStageEvent.class,*/
                //?}
                event -> {
                    BCBuildersEventDist.INSTANCE.renderAllQuarries(event);
                    BCBuildersEventDist.INSTANCE.renderAllFillers(event);
                    BCBuildersEventDist.INSTANCE.renderAllArchitectTables(event);
                    BCBuildersEventDist.INSTANCE.renderAllBuilders(event);
                });
        // Fade out architect "digitizing" cubes one tick at a time on the client.
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> buildcraft.builders.snapshot.ClientArchitectScans.INSTANCE.tick());
        //? if >=26.1 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent.class,
                event -> {
                    BCBuildersEventDist.INSTANCE.renderAllFillersCustomGeometry(event);
                    BCBuildersEventDist.INSTANCE.renderAllBuildersCustomGeometry(event);
                });
        //?}
        // Draws a second tooltip-shaped panel below Blueprint/Template tooltips with a rotating
        // 3D preview. Mirrors the 1.12.2 BCBuildersEventDist#onPostText pattern; the 1.12.2
        // PostText event was removed in modern NeoForge, so we hook Pre and run the layout
        // math ourselves. See BlueprintTooltipOverlay for the full explanation.
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class,
                BlueprintTooltipOverlay::onPreTooltip);
        // Same treatment for used single-block schematics: synthesize a 1×1×1 Blueprint and
        // render through the same PiP pipeline. Without this, two different "used" schematics
        // in the inventory look identical — a 1.12.2 UX gap we're closing here.
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class,
                SchematicSingleTooltipOverlay::onPreTooltip);
    }

    private static void registerClientContent(ClientRegistrationSinks sinks) {
        sinks.clientSetup(() ->
                buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.builders.BCBuildersItems.QUARRY.get(), "tip.block.quarry"));

        sinks.menuScreen(BCBuildersMenuTypes.FILLER, GuiFiller::new);
        sinks.menuScreen(BCBuildersMenuTypes.BUILDER, GuiBuilder::new);
        sinks.menuScreen(BCBuildersMenuTypes.ARCHITECT, GuiArchitectTable::new);
        sinks.menuScreen(BCBuildersMenuTypes.LIBRARY, GuiElectronicLibrary::new);
        sinks.menuScreen(BCBuildersMenuTypes.REPLACER, GuiReplacer::new);

        sinks.entityRenderer(BCBuildersEntities.QUARRY_RIG, net.minecraft.client.renderer.entity.NoopRenderer::new);
        sinks.blockEntityRenderer(BCBuildersBlockEntities.QUARRY, buildcraft.builders.client.render.RenderQuarry::new);
        sinks.blockEntityRenderer(BCBuildersBlockEntities.ARCHITECT,
                buildcraft.builders.client.render.RenderArchitectTable::new);
        sinks.blockEntityRenderer(BCBuildersBlockEntities.FILLER, buildcraft.builders.client.render.RenderFiller::new);

        // The PictureInPicture renderer that paints blueprint previews into an offscreen texture as real 3D
        // block models. Without this, any BlueprintPipRenderState submitted by BlueprintRenderer would be
        // silently dropped by the GuiRenderer (no matching renderer class).
        //? if >=1.21.10 {
        sinks.pipRenderer(buildcraft.builders.client.render.pip.BlueprintPipRenderState.class,
                buildcraft.builders.client.render.pip.BlueprintPipRenderer::new);
        //?}
    }
}
