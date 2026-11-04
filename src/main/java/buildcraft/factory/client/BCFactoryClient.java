/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
import buildcraft.factory.client.gui.GuiChute;
import buildcraft.factory.client.gui.GuiDistiller;
import buildcraft.factory.client.gui.GuiHeatExchange;
import buildcraft.factory.client.gui.GuiTank;
import buildcraft.factory.client.render.RenderDistiller;
import buildcraft.factory.client.render.RenderHeatExchange;
import buildcraft.factory.client.render.RenderMiningWell;
import buildcraft.factory.client.render.RenderPump;
import buildcraft.factory.client.render.RenderTank;
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCFactoryClient {
    private static void registerClientContent(ClientRegistrationSinks sinks) {
        sinks.clientSetup(() ->
                buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.factory.BCFactoryItems.MINING_WELL.get(), "tip.block.mining_well"));

        sinks.menuScreen(BCFactoryMenuTypes.AUTO_WORKBENCH_ITEMS, GuiAutoCraftItems::new);
        sinks.menuScreen(BCFactoryMenuTypes.TANK, GuiTank::new);
        sinks.menuScreen(BCFactoryMenuTypes.CHUTE, GuiChute::new);
        sinks.menuScreen(BCFactoryMenuTypes.DISTILLER, GuiDistiller::new);
        sinks.menuScreen(BCFactoryMenuTypes.HEAT_EXCHANGE, GuiHeatExchange::new);

        sinks.blockEntityRenderer(BCFactoryBlockEntities.TANK, RenderTank::new);
        sinks.blockEntityRenderer(BCFactoryBlockEntities.DISTILLER, RenderDistiller::new);
        sinks.blockEntityRenderer(BCFactoryBlockEntities.HEAT_EXCHANGE, RenderHeatExchange::new);
        sinks.blockEntityRenderer(BCFactoryBlockEntities.PUMP, RenderPump::new);
        sinks.blockEntityRenderer(BCFactoryBlockEntities.MINING_WELL, RenderMiningWell::new);

        // Tube blocks are RenderShape.INVISIBLE; their visuals come from TubeRenderer's
        // laser system, not a block model. Without this, vanilla would still spawn
        // particles from the tube model's particle texture on break/hit.
        sinks.blockParticles(BCFactoryBlocks.TUBE, buildcraft.lib.client.NoParticleClientExtensions.INSTANCE);
    }

    public static void initClient() {
        registerClientContent(ClientPlatform.sinks());
        ClientPlatform.hooks().onLevelRender(buildcraft.factory.client.render.TubeRenderer::onRenderLevel);
    }
}
