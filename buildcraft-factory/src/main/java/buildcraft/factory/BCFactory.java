package buildcraft.factory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCFactory.MODID)
public class BCFactory {
    public static final String MODID = "buildcraftfactory";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCFactory.class);

    public static BCFactory INSTANCE;

    public BCFactory(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        LOGGER.info("BuildCraft Factory stub loaded");
    }
}
