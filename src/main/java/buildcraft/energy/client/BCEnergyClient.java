/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.client;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.BCEnergyModels;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.client.gui.ScreenEngineIron;
import buildcraft.energy.client.gui.ScreenEngineFE;
import buildcraft.energy.client.gui.ScreenDynamoMJ;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.tile.RenderEngine_BC8;
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCEnergyClient {
    private static void registerScreens(ClientRegistrationSinks sinks) {
        sinks.menuScreen(BCEnergyMenuTypes.ENGINE_STONE, ScreenEngineStone::new);
        sinks.menuScreen(BCEnergyMenuTypes.ENGINE_IRON, ScreenEngineIron::new);
        sinks.menuScreen(BCEnergyMenuTypes.ENGINE_FE, ScreenEngineFE::new);
        sinks.menuScreen(BCEnergyMenuTypes.DYNAMO_MJ, ScreenDynamoMJ::new);
    }

    private static void registerRenderers(ClientRegistrationSinks sinks) {
        sinks.blockEntityRenderer(BCEnergyBlockEntities.ENGINE_STONE,
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileEngineStone_BC8 stone) {
                        return BCEnergyModels.getStoneEngineQuads(stone, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
        sinks.blockEntityRenderer(BCEnergyBlockEntities.ENGINE_IRON,
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileEngineIron_BC8 iron) {
                        return BCEnergyModels.getIronEngineQuads(iron, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
        sinks.blockEntityRenderer(BCEnergyBlockEntities.ENGINE_FE,
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileEngineFE fe) {
                        return BCEnergyModels.getFeEngineQuads(fe, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
        sinks.blockEntityRenderer(BCEnergyBlockEntities.DYNAMO_MJ,
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileDynamoMJ dynamo) {
                        return BCEnergyModels.getDynamoQuads(dynamo, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
    }

    private static void setupClient(ClientRegistrationSinks sinks) {
        sinks.clientSetup(() -> {
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.energy.BCEnergyItems.ENGINE_STONE.get(), "tip.block.engine_stone");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.energy.BCEnergyItems.ENGINE_IRON.get(), "tip.block.engine_iron");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.energy.BCEnergyItems.ENGINE_FE.get(), "tip.block.engine_rf");
            buildcraft.lib.client.BCTooltips.addTooltip(buildcraft.energy.BCEnergyItems.DYNAMO_MJ.get(), "tip.block.mj_dynamo");
        });
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(buildcraft.energy.client.BCEnergyFluidsClient.class);
        ClientRegistrationSinks sinks = ClientPlatform.sinks();
        registerScreens(sinks);
        registerRenderers(sinks);
        setupClient(sinks);

        // Fuel/coolant/distillation registries feed the guide book and JEI. Servers populate
        // them at ServerAboutToStartEvent (BCEnergy); a multiplayer client never fires that
        // event, so populate at login — the guide content build runs even later (off the
        // server's recipe sync arriving, see BCLibClient), so ordering is safe.
        ClientPlatform.hooks().onLoggingIn(buildcraft.energy.BCEnergyRecipes::ensureInitialized);
        //? if >=26.3 {
        // The server-sent Stirling fuel list belongs to the world just left.
        ClientPlatform.hooks().onLoggingOut(buildcraft.energy.compat.viewer.StirlingFuelSync::clearReceived);
        //?}
    }
}
