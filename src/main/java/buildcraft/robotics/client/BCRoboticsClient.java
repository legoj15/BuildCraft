package buildcraft.robotics.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.client.gui.GuiZonePlanner;

public class BCRoboticsClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCRoboticsMenuTypes.ZONE_PLANNER.get(), GuiZonePlanner::new);
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

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCRoboticsClient.class);
    }
}
