/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import buildcraft.api.transport.pipe.PipeApiClient;
import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.plug.PlugBakerSimple;

import buildcraft.robotics.BCRobotics;
import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.BCRoboticsParticles;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.client.gui.GuiZonePlanner;
import buildcraft.robotics.client.model.RobotChargeTintSource;
import buildcraft.robotics.client.model.RobotStationModel;
import buildcraft.robotics.client.model.key.KeyPlugRobotStation;
import buildcraft.robotics.client.particle.RobotEnergyParticle;
import buildcraft.robotics.client.render.PlugRobotStationRenderer;
import buildcraft.robotics.client.render.RenderRobot;
import buildcraft.robotics.client.render.RenderZonePlanner;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.entity.RobotExhaust;
import buildcraft.platform.client.ClientHooks;
import buildcraft.platform.client.ClientPlatform;
import buildcraft.platform.client.ClientRegistrationSinks;

public class BCRoboticsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRoboticsClient.class);

    // Baker for the docking station's state-invariant pedestal, in the sprite 1.7.10 used for both
    // its idle states (the reserved/linked ones are re-emitted per frame — see
    // PlugRobotStationRenderer / KeyPlugRobotStation). Both paths build their quads from
    // RobotStationModel, which is what keeps them pixel-identical.
    public static final IPluggableStaticBaker<KeyPlugRobotStation> BAKER_PLUG_ROBOT_STATION =
        new PlugBakerSimple<>(RobotStationModel::bakedQuads);

    private static void registerClientContent(ClientRegistrationSinks sinks) {
        sinks.menuScreen(BCRoboticsMenuTypes.ZONE_PLANNER, GuiZonePlanner::new);
        sinks.menuScreen(BCRoboticsMenuTypes.PROGRAMMING_TABLE, buildcraft.robotics.client.gui.GuiProgrammingTable::new);
        sinks.menuScreen(BCRoboticsMenuTypes.REQUESTER, buildcraft.robotics.client.gui.GuiRequester::new);

        // The Zone Planner's in-world face preview renderer (the live terrain "screen") and the robot entity
        // renderer; the two renderer generations are absorbed inside RenderRobot itself.
        sinks.blockEntityRenderer(BCRoboticsBlockEntities.ZONE_PLANNER, RenderZonePlanner::new);
        sinks.entityRenderer(BCRoboticsEntities.ROBOT, RenderRobot::new);

        // The robot's red energy exhaust. The sprite-set registration type moved from ParticleEngine to
        // ParticleResources at 1.21.10; a constructor reference fits both.
        sinks.particleSpriteSet(BCRoboticsParticles.ROBOT_ENERGY, RobotEnergyParticle.Provider::new);

        // 1.21.1-only: registers the buildcraftunofficial:board item property that drives the per-board
        // robot and board icons through classic model overrides. On the newer nodes the items/*.json
        // condition chains do the same job and no registration is needed — see RoboticsBoardItemProperties.
        //? if <1.21.10 {
        /*sinks.clientSetup(buildcraft.robotics.client.model.RoboticsBoardItemProperties::register);*/
        //?}

        // The robot icon's charge-faded eye decals: RobotChargeTintSource reads the stack's energy and returns a
        // neutral multiplier at the charge fraction, tinting the red eye decal (tintindex 0) of
        // robot_chassis_base.json — 7.1.x's decal pass, which faded the same art by alpha. Registered per node
        // family — data-driven tint source id on 1.21.10+ (referenced by the tints of every items/robot.json model
        // leaf; the always-on cyan under-port is a vanilla constant there), classic per-item ItemColor on 1.21.1,
        // which has no tint-source registry and answers both tintindexes from this one class.
        //? if >=1.21.10 {
        sinks.itemTintSource(Identifier.fromNamespaceAndPath(BCRobotics.MODID, "robot_charge"),
                RobotChargeTintSource.MAP_CODEC);
        //?} else {
        /*sinks.itemColor(RobotChargeTintSource.INSTANCE,
                () -> new net.minecraft.world.level.ItemLike[] {BCRoboticsItems.ROBOT.get()});*/
        //?}

        // The PictureInPicture renderer that paints the Zone Planner's terrain map into an offscreen texture.
        // Without it, the ZoneMapPipRenderState the GUI submits each frame is silently dropped (no matching
        // renderer). The 1.21.1 line lacks the vanilla picture-in-picture class, so this registration is gated
        // out there — 1.21.1 instead draws the same map straight into the GUI via ZoneMapGuiRenderer.
        //? if >=1.21.10 {
        sinks.pipRenderer(buildcraft.robotics.client.render.ZoneMapPipRenderState.class,
                buildcraft.robotics.client.render.ZoneMapPipRenderer::new);
        //?}
    }

    /**
     * Both docking-station caches hold TextureAtlasSprite references, which a resource reload
     * discards. Cleared here, not in ModifyBakingResult, because the new block atlas is only live by
     * BakingCompleted. The baked side is doubly load-bearing: PlugBakerSimple only re-bakes when the
     * quad ARRAY's identity changes, so dropping the cache is what makes the rebuilt array reach it.
     */
    public static void onBakingCompleted() {
        RobotStationModel.onModelBake();
        PlugRobotStationRenderer.onModelBake();
    }

    /**
     * Registers the docking-station baker and dynamic renderer. Fires after all models are baked and
     * after {@link EntityRenderersEvent} has completed, so {@code PipeApiClient.registry} is
     * guaranteed to be set by Transport — mirrors {@code BCSiliconClient}'s facade/gate registration.
     */
    public static void onModifyBakingResult() {
        if (PipeApiClient.registry != null) {
            PipeApiClient.registry.registerBaker(KeyPlugRobotStation.class, BAKER_PLUG_ROBOT_STATION);
            PipeApiClient.registry.registerRenderer(RobotStationPluggable.class, PlugRobotStationRenderer.INSTANCE);
        } else {
            LOGGER.warn("[robotics.client] PipeApiClient.registry is null at ModifyBakingResult! "
                + "Docking station in-world rendering will not work.");
        }
    }

    /** Client game hooks (the session and tick hooks the Zone Planner cache and the table recipes need). */
    public static final class GameBus {
        private GameBus() {}

        static void register(ClientHooks hooks) {
            hooks.onLoggingIn(GameBus::onClientLoggingIn);
            hooks.onClientTick(GameBus::onClientTick);
            hooks.onLoggingOut(GameBus::onClientLoggingOut);
            hooks.onClientLevelUnload(GameBus::onLevelUnload);
            hooks.onClientChunkLoad(GameBus::invalidateClientChunk);
            hooks.onClientChunkUnload(GameBus::invalidateClientChunk);
        }

        /** Table recipes are registered at ServerAboutToStartEvent on servers; a multiplayer client (where
         *  that event never fires) needs the programming recipe before its GUI can rebuild the option grid,
         *  so it registers here — same reason BCSiliconRecipes has its own LoggingIn hook. Once-per-JVM via
         *  the ensureInitialized guard. */
        public static void onClientLoggingIn() {
            buildcraft.robotics.BCRoboticsRecipes.ensureInitialized();
        }

        // The Zone Planner's terrain-colour cache (ZonePlannerMapDataClient): the five hooks below keep it from
        // serving another world's terrain, a closed world, or a chunk that has since reloaded. Block changes
        // inside a loaded chunk raise no client event, so the cache also refreshes by age (see LevelChunkCache).

        /** Drives the cache's refresh clock. */
        public static void onClientTick() {
            ZonePlannerMapDataClient.INSTANCE.tick();
        }

        /** Leaving a world: its terrain must not outlive it (and the cache must not pin its level). */
        public static void onClientLoggingOut() {
            ZonePlannerMapDataClient.INSTANCE.clear();
        }

        /** A client level closing (disconnect, or the old dimension on a dimension change). */
        public static void onLevelUnload() {
            ZonePlannerMapDataClient.INSTANCE.clear();
        }

        /** A client chunk (re)loaded — the server re-sent it, so what was scanned from the old copy is stale — or
         *  dropped, where the viewport must show a gap, not the last terrain it had. {@code ChunkPos} reads as
         *  fields up to 1.21.11 and as record accessors from 26.1, so the position is taken from its (stable)
         *  block-origin getters. */
        private static void invalidateClientChunk(net.minecraft.world.level.Level level,
                                                  net.minecraft.world.level.ChunkPos pos) {
            ZonePlannerMapDataClient.INSTANCE.invalidateChunk(level, pos.getMinBlockX() >> 4, pos.getMinBlockZ() >> 4);
        }
    }

    public static void initClient() {
        ClientPlatform.sinks().onModelsBaked(buildcraft.platform.event.HookOrder.DEFAULT,
                BCRoboticsClient::onBakingCompleted);
        ClientPlatform.sinks().onModelsBaking(BCRoboticsClient::onModifyBakingResult);
        registerClientContent(ClientPlatform.sinks());
        // Guide book: Programming/Integration Table steps on board and robot pages (type="programming"/"integration").
        buildcraft.robotics.client.guide.RoboticsGuideRecipes.register();
        // Guide book: the programmed-board icon of the Robots > Boards pages (icon kind "board").
        buildcraft.robotics.client.guide.RoboticsGuideIcons.register();
        // The exhaust thins itself by the particle setting (7.1.x's rule); the common-side robot reads it
        // through this seam so EntityRobot never names a client class. The ParticleStatus enum moved
        // package at 1.21.10 and lost getId() by 26.1, so it is read through the option by constant name
        // rather than imported.
        RobotExhaust.setParticleSettingSource(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc == null || mc.options == null
                    ? 0 : RobotExhaust.particleSettingId(mc.options.particles().get().name());
        });
        GameBus.register(ClientPlatform.hooks());
    }
}
