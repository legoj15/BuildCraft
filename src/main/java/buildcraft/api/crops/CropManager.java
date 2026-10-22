package buildcraft.api.crops;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

public final class CropManager {
    private static List<ICropHandler> handlers = new ArrayList<>();
    private static ICropHandler defaultHandler;
    private static ToLongFunction<Player> refusalCounter = player -> 0;

    private CropManager() {

    }

    public static void registerHandler(ICropHandler cropHandler) {
        handlers.add(cropHandler);
    }

    public static void setDefaultHandler(ICropHandler cropHandler) {
        defaultHandler = cropHandler;
    }

    public static ICropHandler getDefaultHandler() {
        return defaultHandler;
    }

    /** Sets how many of a player's clicks a claim mod has refused so far (BuildCraft points it at its own tally of
     *  machine clicks). {@link #plantCrop} reads it around each handler: a handler whose click was refused ends the
     *  planting there, as a refused click ends a player's, instead of the next handler clicking the same seed on the
     *  same soil the claim just refused. Only differences between two readings mean anything. */
    public static void setRefusalCounter(ToLongFunction<Player> counter) {
        refusalCounter = counter;
    }

    public static boolean isSeed(ItemStack stack) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isSeed(stack)) {
                return true;
            }
        }
        return defaultHandler != null && defaultHandler.isSeed(stack);
    }

    public static boolean canSustainPlant(Level world, ItemStack seed, BlockPos pos) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isSeed(seed) && cropHandler.canSustainPlant(world, seed, pos)) {
                return true;
            }
        }
        return defaultHandler != null && defaultHandler.isSeed(seed) && defaultHandler.canSustainPlant(world, seed, pos);
    }

    /** Attempts to plant the crop given by the seed into the world. Also checks to make sure that
     * {@link ICropHandler#isSeed(ItemStack)} is true, and
     * {@link ICropHandler#canSustainPlant(Level, ItemStack, BlockPos)} is true for the position. */
    public static boolean plantCrop(Level world, Player player, ItemStack seed, BlockPos pos) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isSeed(seed) && cropHandler.canSustainPlant(world, seed, pos)) {
                long refused = player == null ? 0 : refusalCounter.applyAsLong(player);
                if (cropHandler.plantCrop(world, player, seed, pos)) {
                    return true;
                }
                if (player != null && refusalCounter.applyAsLong(player) != refused) {
                    return false;
                }
            }
        }
        if (defaultHandler != null && defaultHandler.isSeed(seed) && defaultHandler.canSustainPlant(world, seed, pos)) {
            return defaultHandler.plantCrop(world, player, seed, pos);
        }
        return false;
    }

    public static boolean isMature(BlockGetter blockAccess, BlockState state, BlockPos pos) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isMature(blockAccess, state, pos)) {
                return true;
            }
        }
        return defaultHandler != null && defaultHandler.isMature(blockAccess, state, pos);
    }

    public static boolean harvestCrop(Level world, BlockPos pos, NonNullList<ItemStack> drops) {
        return harvestCrop(world, pos, drops, null);
    }

    /** {@link #harvestCrop(Level, BlockPos, NonNullList)} as {@code player}; see
     * {@link ICropHandler#harvestCrop(Level, BlockPos, NonNullList, Player)}. */
    public static boolean harvestCrop(Level world, BlockPos pos, NonNullList<ItemStack> drops, @Nullable Player player) {
        BlockState state = world.getBlockState(pos);
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isMature(world, state, pos)) {
                return cropHandler.harvestCrop(world, pos, drops, player);
            }
        }
        return defaultHandler != null && defaultHandler.isMature(world, state, pos)
                && defaultHandler.harvestCrop(world, pos, drops, player);
    }

}

