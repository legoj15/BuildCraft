package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCSilicon.MODID)
public class BCSilicon {
    public static final String MODID = "buildcraftsilicon";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCSilicon.class);

    public static BCSilicon INSTANCE;

    public BCSilicon(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        LOGGER.info("BuildCraft Silicon stub loaded");
    }
}
