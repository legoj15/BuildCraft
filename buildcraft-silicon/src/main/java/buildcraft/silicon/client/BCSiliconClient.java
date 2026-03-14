package buildcraft.silicon.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.client.render.RenderLaser;

public class BCSiliconClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCSiliconBlockEntities.LASER.get(), RenderLaser::new);
    }
}
