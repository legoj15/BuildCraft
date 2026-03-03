package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SoundUtil {
    public static void playSlideSound(Level level, BlockPos pos, BlockState state, InteractionResult result) {
        // Stub
    }

    public static void playChangeColour(Level level, BlockPos pos, @Nullable DyeColor colour) {
        // Stub — requires sound registry
    }
}
