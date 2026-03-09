package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class InventoryUtil {
    /** Extracts all items from the handler and adds them to the given list. */
    public static void addAll(IItemHandler handler, List<ItemStack> list) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
    }

    /** Attempts to add the given stack to the best acceptor, in this order:
     * {@link ResourceHandler} instances on adjacent blocks (chests, hoppers, etc.),
     * and finally dropping it on the ground.
     *
     * In the future, IInjectable (BuildCraft pipes) will be checked first. */
    public static void addToBestAcceptor(Level level, BlockPos pos, @Nullable Direction ignore, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;
        // TODO: addToRandomInjectable (BuildCraft pipes) once transport is ported
        stack = addToRandomInventory(level, pos, stack);
        if (!stack.isEmpty()) {
            drop(level, pos, stack);
        }
    }

    /** Tries to insert the stack into adjacent ResourceHandler inventories (chests, hoppers, etc).
     * Uses NeoForge 1.21.x Capabilities.Item.BLOCK with ResourceHandler + Transaction API.
     * @return The leftover stack that could not be inserted. */
    @Nonnull
    public static ItemStack addToRandomInventory(Level level, BlockPos pos, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        List<Direction> toTry = new ArrayList<>(6);
        Collections.addAll(toTry, Direction.values());
        Collections.shuffle(toTry);

        ItemResource resource = ItemResource.of(stack);
        int remaining = stack.getCount();

        for (Direction face : toTry) {
            if (remaining <= 0) return ItemStack.EMPTY;

            BlockPos adjPos = pos.relative(face);
            // Query the adjacent block for item handler capability on the side facing us
            ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, adjPos, face.getOpposite());
            if (handler == null) continue;

            // insertStacking prefers filled slots first, then empty — passing null opens and auto-commits a transaction
            int inserted = ResourceHandlerUtil.insertStacking(handler, resource, remaining, null);
            remaining -= inserted;
        }

        if (remaining <= 0) return ItemStack.EMPTY;
        return stack.copyWithCount(remaining);
    }

    /** Drops the stack as an item entity at the given position. */
    public static void drop(Level level, BlockPos pos, @Nonnull ItemStack stack) {
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack);
        }
    }
}
