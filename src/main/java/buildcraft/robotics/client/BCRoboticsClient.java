/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.api.transport.pipe.PipeApiClient;
import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.ModelHolderStatic;
import buildcraft.lib.client.model.plug.PlugBakerSimple;

import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.client.gui.GuiZonePlanner;
import buildcraft.robotics.client.model.key.KeyPlugRobotStation;
import buildcraft.robotics.client.render.PlugRobotStationRenderer;
import buildcraft.robotics.client.render.RenderZonePlanner;

public class BCRoboticsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRoboticsClient.class);

    // Static model holder + baker for the docking station's state-invariant plinth (the docking
    // state indicator is rendered dynamically — see PlugRobotStationRenderer / KeyPlugRobotStation).
    public static final ModelHolderStatic ROBOT_STATION =
        new ModelHolderStatic("buildcraftunofficial:models/plugs/robot_station.json");
    public static final IPluggableStaticBaker<KeyPlugRobotStation> BAKER_PLUG_ROBOT_STATION =
        new PlugBakerSimple<>(ROBOT_STATION::getCutoutQuads);

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCRoboticsMenuTypes.ZONE_PLANNER.get(), GuiZonePlanner::new);
    }

    /** Registers the Zone Planner's in-world face preview renderer (the live terrain "screen"). */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCRoboticsBlockEntities.ZONE_PLANNER.get(), RenderZonePlanner::new);
    }

    /**
     * Registers the PictureInPicture renderer that paints the Zone Planner's terrain map into an
     * offscreen texture. Without it, the {@code ZoneMapPipRenderState} the GUI submits each frame is
     * silently dropped (no matching renderer). The 1.21.1 line lacks the vanilla picture-in-picture
     * class, so this registration is gated out there — 1.21.1 instead draws the same map straight into
     * the GUI via {@code ZoneMapGuiRenderer} (no registration needed).
     */
    //? if >=1.21.10 {
    @SubscribeEvent
    public static void registerPipRenderers(
            net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(
                buildcraft.robotics.client.render.ZoneMapPipRenderState.class,
                buildcraft.robotics.client.render.ZoneMapPipRenderer::new);
    }
    //?}

    /**
     * Registers the docking-station baker and dynamic renderer. Fires after all models are baked and
     * after {@link EntityRenderersEvent} has completed, so {@code PipeApiClient.registry} is
     * guaranteed to be set by Transport — mirrors {@code BCSiliconClient}'s facade/gate registration.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        if (PipeApiClient.registry != null) {
            PipeApiClient.registry.registerBaker(KeyPlugRobotStation.class, BAKER_PLUG_ROBOT_STATION);
            PipeApiClient.registry.registerRenderer(RobotStationPluggable.class, PlugRobotStationRenderer.INSTANCE);
        } else {
            LOGGER.warn("[robotics.client] PipeApiClient.registry is null at ModifyBakingResult! "
                + "Docking station in-world rendering will not work.");
        }
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCRoboticsClient.class);
    }
}
