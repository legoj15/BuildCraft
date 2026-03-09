package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

public class SoundUtil {
    public static void playBlockPlace(Level world, BlockPos pos) {
        playBlockPlace(world, pos, world.getBlockState(pos));
    }

    public static void playBlockPlace(Level world, BlockPos pos, BlockState state) {
        SoundType soundType = state.getSoundType();
        float volume = (soundType.getVolume() + 1.0F) / 2.0F;
        float pitch = soundType.getPitch() * 0.8F;
        world.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, volume, pitch);
    }

    public static void playSlideSound(Level level, BlockPos pos, BlockState state, InteractionResult result) {
        // Stub
    }

    public static void playChangeColour(Level level, BlockPos pos, @Nullable DyeColor colour) {
        // Stub — requires sound registry
    }

    public static void playBucketEmpty(Level world, BlockPos pos, FluidStack fluid) {
        world.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public static void playBucketFill(Level world, BlockPos pos, FluidStack fluid) {
        world.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
