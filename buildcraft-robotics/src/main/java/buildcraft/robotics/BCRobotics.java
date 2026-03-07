package buildcraft.robotics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCRobotics.MODID)
public class BCRobotics {
    public static final String MODID = "buildcraftrobotics";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCRobotics.class);

    public static BCRobotics INSTANCE;

    public BCRobotics(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        LOGGER.info("BuildCraft Robotics stub loaded");
    }
}
