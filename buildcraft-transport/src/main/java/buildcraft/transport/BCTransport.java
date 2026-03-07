package buildcraft.transport;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCTransport.MODID)
public class BCTransport {
    public static final String MODID = "buildcrafttransport";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCTransport.class);

    public static BCTransport INSTANCE;

    public BCTransport(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        LOGGER.info("BuildCraft Transport stub loaded");
    }
}
