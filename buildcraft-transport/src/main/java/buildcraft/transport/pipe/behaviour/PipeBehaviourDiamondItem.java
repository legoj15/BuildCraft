package buildcraft.transport.pipe.behaviour;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

import buildcraft.lib.misc.StackUtil;

/** Diamond (sorting) pipe behaviour for item transport pipes.
 * Routes items to specific sides based on the filter items configured per direction. */
public class PipeBehaviourDiamondItem extends PipeBehaviourDiamond {

    public PipeBehaviourDiamondItem(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamondItem(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
    }

    @PipeEventHandler
    public void sideCheck(PipeEventItem.SideCheck sideCheck) {
        ItemStack toSort = sideCheck.stack;

        boolean hasFilterInAnySlot = false;
        for (int i = 0; i < filterStacks.size(); i++) {
            if (!filterStacks.get(i).isEmpty()) {
                hasFilterInAnySlot = true;
                break;
            }
        }
        if (!hasFilterInAnySlot) {
            return;
        }

        // Check each direction for matching filters
        for (Direction face : Direction.values()) {
            int baseSlot = face.ordinal() * FILTERS_PER_SIDE;
            boolean sideHasFilter = false;
            boolean sideMatches = false;
            for (int i = 0; i < FILTERS_PER_SIDE; i++) {
                ItemStack filter = filterStacks.get(baseSlot + i);
                if (!filter.isEmpty()) {
                    sideHasFilter = true;
                    if (StackUtil.isMatchingItemOrList(filter, toSort)) {
                        sideMatches = true;
                        break;
                    }
                }
            }
            if (sideMatches) {
                sideCheck.increasePriority(face, 12);
            } else if (sideHasFilter) {
                sideCheck.disallow(face);
            }
        }
    }

    @PipeEventHandler
    public void split(PipeEventItem.Split split) {
        // Default split behaviour — items are not split in diamond pipes
    }
}
