/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.api.transport.pipe.PipeApiClient;
import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.plug.PlugBakerSimple;

import buildcraft.robotics.BCRobotics;
import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.client.gui.GuiZonePlanner;
import buildcraft.robotics.client.model.RobotChargeTintSource;
import buildcraft.robotics.client.model.RobotStationModel;
import buildcraft.robotics.client.model.key.KeyPlugRobotStation;
import buildcraft.robotics.client.render.PlugRobotStationRenderer;
import buildcraft.robotics.client.render.RenderRobot;
import buildcraft.robotics.client.render.RenderZonePlanner;

public class BCRoboticsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRoboticsClient.class);

    // Baker for the docking station's state-invariant pedestal, in the sprite 1.7.10 used for both
    // its idle states (the reserved/linked ones are re-emitted per frame — see
    // PlugRobotStationRenderer / KeyPlugRobotStation). Both paths build their quads from
    // RobotStationModel, which is what keeps them pixel-identical.
    public static final IPluggableStaticBaker<KeyPlugRobotStation> BAKER_PLUG_ROBOT_STATION =
        new PlugBakerSimple<>(RobotStationModel::bakedQuads);

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCRoboticsMenuTypes.ZONE_PLANNER.get(), GuiZonePlanner::new);
    }

    /** Registers the Zone Planner's in-world face preview renderer (the live terrain "screen") and the
     *  robot entity renderer. {@code registerEntityRenderer} has the same signature on all five nodes, so
     *  this line needs no directive — the two renderer generations are absorbed inside
     *  {@link RenderRobot} itself. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCRoboticsBlockEntities.ZONE_PLANNER.get(), RenderZonePlanner::new);
        event.registerEntityRenderer(BCRoboticsEntities.ROBOT.get(), RenderRobot::new);
    }

    // 1.21.1-only: registers the buildcraftunofficial:board item property that drives the per-board
    // robot and board icons through classic model overrides. On the newer nodes the items/*.json
    // condition chains do the same job and no registration is needed — see RoboticsBoardItemProperties.
    //? if <1.21.10 {
    /*@SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(buildcraft.robotics.client.model.RoboticsBoardItemProperties::register);
    }*/
    //?}

    /** The robot icon's charge-faded eye decals: {@code RobotChargeTintSource} reads the stack's
     *  energy and returns a neutral multiplier at the charge fraction, tinting the red eye decal
     *  (tintindex 0) of {@code robot_chassis_base.json} — 7.1.x's decal pass, which faded the same
     *  art by alpha. Registered per node family — data-driven tint source id on 1.21.10+ (referenced
     *  by the {@code tints} of every {@code items/robot.json} model leaf; the always-on cyan
     *  under-port is a vanilla constant there), classic per-item {@code ItemColor} on 1.21.1, which
     *  has no tint-source registry and answers both tintindexes from this one class. */
    //? if >=1.21.10 {
    @SubscribeEvent
    public static void registerItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(BCRobotics.MODID, "robot_charge"),
                RobotChargeTintSource.MAP_CODEC);
    }
    //?}
    //? if <1.21.10 {
    /*@SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(RobotChargeTintSource.INSTANCE, BCRoboticsItems.ROBOT.get());
    }*/
    //?}

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
        // Both caches hold TextureAtlasSprite references, which a resource reload discards. The baked
        // side is doubly load-bearing: PlugBakerSimple only re-bakes when the quad ARRAY's identity
        // changes, so dropping the cache here is what makes the rebuilt array reach it.
        RobotStationModel.onModelBake();
        PlugRobotStationRenderer.onModelBake();
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
