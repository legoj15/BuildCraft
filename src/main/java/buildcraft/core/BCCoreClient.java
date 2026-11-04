/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core;

import net.neoforged.neoforge.common.NeoForge;

import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCCoreClient {
    /** BuildCraft's client init, run by the loader entrypoint on a physical client, after {@link BCCore#init}. */
    public static void init() {
        ClientRegistrationSinks sinks = ClientPlatform.sinks();
        // Per-player display preferences — registered client-side only so each client owns its own copy.
        buildcraft.platform.Platform.configRegistrar().register(buildcraft.platform.config.BCConfigType.CLIENT,
                BCUnifiedClientConfig.SPEC, null);

        // Live re-render diamond pipes when the colourblind toggle changes. Both variants are
        // already on the block atlas (the `pipes/` directory atlas source picks up `_cb.png`
        // companions at startup), so this just kicks the level renderer to rebuild visible
        // chunks — no resource-pack reload needed.
        buildcraft.platform.Platform.configHooks().onReload(BCUnifiedClientConfig.SPEC,
                buildcraft.transport.client.model.PipeBaseModelGenStandard::onColorBlindToggle);

        ClientPlatform.configScreens().registerConfigScreen();

        ClientPlatform.hooks().onLevelRender(buildcraft.lib.client.render.MarkerRenderer::onRenderLevelStage);
        // Engine BERs
        sinks.blockEntityRenderer(BCCoreBlockEntities.ENGINE_REDSTONE,
                ctx -> new buildcraft.lib.client.render.tile.RenderEngine_BC8(BCCoreModels::getWoodEngineQuads));
        sinks.blockEntityRenderer(BCCoreBlockEntities.ENGINE_CREATIVE,
                ctx -> new buildcraft.lib.client.render.tile.RenderEngine_BC8(BCCoreModels::getCreativeEngineQuads));
        // F3 debug overlay: tick handler for polling IDebuggable + sending server requests
        ClientPlatform.hooks().onClientTick(buildcraft.core.client.DebugOverlayHelper::onClientTick);
        // Marker laser length and the volume marker distance readout under the crosshair: work out per tick, draw per frame
        ClientPlatform.hooks().onClientTick(() -> {
            buildcraft.core.client.MarkerLengthIndicator.onClientTick();
            buildcraft.core.client.MarkerDistanceTracker.onClientTick();
        });
        // Register the fluid shard tint source for fragile fluid containers
        //? if >=1.21.10 {
        sinks.itemTintSource(net.minecraft.resources.Identifier.parse("buildcraftunofficial:fluid_shard_tint"),
                buildcraft.core.client.FluidShardTintSource.MAP_CODEC);
        //?} else {
        /*// 1.21.1 has no data-driven ItemTintSource — register the equivalent ItemColor on the item.
        sinks.itemColor(buildcraft.core.client.FluidShardTintSource.INSTANCE,
                () -> new net.minecraft.world.level.ItemLike[] {buildcraft.core.BCCoreItems.FRAGILE_FLUID_CONTAINER.get()});*/
        //?}
        // 1.21.1 only: swap the shard's neoforge:fluid_container model for one that pushes the fluid
        // layer clear of the frame, fixing the base/fluid z-fight. The vanilla FLUID_TRANSFORM gap
        // (~0.001) is below 1.21.1's classic item-render depth precision; modern nodes render it fine,
        // so this is a 1.21.1-only swap (their model-baking-result API differs anyway).
        //? if <1.21.10 {
        /*sinks.editBakedModels(
                models -> {
                    net.minecraft.client.resources.model.ModelResourceLocation mrl =
                            net.minecraft.client.resources.model.ModelResourceLocation.inventory(
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                            buildcraft.core.BCCoreItems.FRAGILE_FLUID_CONTAINER.get()));
                    net.minecraft.client.resources.model.BakedModel vanilla = models.get(mrl);
                    if (vanilla != null) {
                        models.put(mrl, buildcraft.platform.client.ClientPlatform.models().layeredFluidContainerModel(vanilla));
                    }
                }
        );*/
        //?}
        // F3 debug overlay layer, and the marker length readout just above the crosshair
        sinks.guiLayerAboveAll(net.minecraft.resources.Identifier.parse("buildcraftunofficial:debug_overlay"),
                buildcraft.core.client.DebugOverlayRenderer::render);
        sinks.guiLayerAbove(ClientRegistrationSinks.VanillaGuiLayer.CROSSHAIR,
                net.minecraft.resources.Identifier.parse("buildcraftunofficial:marker_length"),
                buildcraft.core.client.MarkerLengthIndicator::render);
        // List GUI screen
        sinks.menuScreen(BCCoreMenuTypes.LIST, buildcraft.core.list.GuiList::new);
        // Register static tooltips, and the Curios head renderer for the Robot Goggles
        sinks.clientSetup(() -> {
            buildcraft.lib.client.BCTooltips.addTooltip(BCCoreItems.ENGINE_CREATIVE.get(), "tip.block.engine_creative");
            buildcraft.lib.client.BCTooltips.addTooltip(BCCoreItems.ENGINE_REDSTONE.get(), "tip.block.engine_redstone");
            buildcraft.lib.client.BCTooltips.addTooltip(BCCoreItems.MARKER_VOLUME.get(), "tip.block.marker_volume");
            buildcraft.lib.client.BCTooltips.addTooltip(BCCoreItems.MARKER_PATH.get(), "tip.block.marker_path");
            // Curios draws nothing for a curio without a renderer; Accessories has a default one.
            if (buildcraft.platform.Platform.loader().isModLoaded("curios")) {
                buildcraft.lib.compat.client.CuriosHeadItemRenderer.register(BCCoreItems.GOGGLES.get());
            }
        });
        // Register List tooltip handler (shows 'Matches' in tooltip while List GUI is open)
        ClientPlatform.hooks().onItemTooltip(buildcraft.core.list.ListTooltipHandler.INSTANCE::itemTooltip);

        // Custom particles for brushing pipes (suppresses the underlying block's vanilla wood/invisible particles)
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick.class,
            event -> {
                if (event.getEntity().level().isClientSide() && event.getItem().is(net.minecraft.world.item.Items.BRUSH)) {
                    int maxDuration = event.getItem().getUseDuration(event.getEntity());
                    int used = maxDuration - event.getDuration();
                    // BrushItem spawns dust every 10 ticks
                    if (used > 0 && (used % 10) == 0) {
                        net.minecraft.world.phys.HitResult hit = net.minecraft.client.Minecraft.getInstance().hitResult;
                        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                            net.minecraft.world.level.block.state.BlockState state = event.getEntity().level().getBlockState(pos);
                            if (state.getBlock() instanceof buildcraft.transport.block.BlockPipeHolder) {
                                buildcraft.transport.client.PipeHolderClientExtensions.INSTANCE.addHitEffects(
                                    //? if <26.3 {
                                    /*state, event.getEntity().level(), blockHit, net.minecraft.client.Minecraft.getInstance().particleEngine
                                    *///?} else {
                                    state, event.getEntity().level(), pos, blockHit.getDirection(),
                                    net.minecraft.client.Minecraft.getInstance().particleEngine
                                    //?}
                                );
                            }
                        }
                    }
                }
            }
        );
    }
}
