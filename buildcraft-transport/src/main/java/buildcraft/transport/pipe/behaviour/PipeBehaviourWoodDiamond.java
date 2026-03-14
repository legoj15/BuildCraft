package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.misc.NBTUtilBC;

/** Emerald (WoodDiamond) pipe — filtered extraction with round-robin support. */
public class PipeBehaviourWoodDiamond extends PipeBehaviourWood {

    public enum FilterMode {
        WHITE_LIST,
        BLACK_LIST,
        ROUND_ROBIN;

        public static FilterMode get(int index) {
            if (index < 0 || index >= values().length) return WHITE_LIST;
            return values()[index];
        }
    }

    public final NonNullList<ItemStack> filterStacks = NonNullList.withSize(9, ItemStack.EMPTY);
    public FilterMode filterMode = FilterMode.WHITE_LIST;
    public int currentFilter = 0;
    public boolean filterValid = false;

    public PipeBehaviourWoodDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodDiamond(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        CompoundTag filtersTag = nbt.getCompoundOrEmpty("filters");
        for (int i = 0; i < filterStacks.size(); i++) {
            CompoundTag itemTag = filtersTag.getCompoundOrEmpty("slot" + i);
            if (!itemTag.isEmpty()) {
                filterStacks.set(i, NBTUtilBC.itemStackFromNBT(itemTag));
            }
        }
        filterMode = FilterMode.get(nbt.getByteOr("mode", (byte) 0));
        currentFilter = nbt.getByteOr("currentFilter", (byte) 0) % filterStacks.size();
        filterValid = hasAnyFilter();
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        CompoundTag filtersTag = new CompoundTag();
        for (int i = 0; i < filterStacks.size(); i++) {
            ItemStack stack = filterStacks.get(i);
            if (!stack.isEmpty()) {
                filtersTag.put("slot" + i, NBTUtilBC.itemStackToNBT(stack));
            }
        }
        nbt.put("filters", filtersTag);
        nbt.putByte("mode", (byte) filterMode.ordinal());
        nbt.putByte("currentFilter", (byte) currentFilter);
        return nbt;
    }

    @Override
    public void writePayload(FriendlyByteBuf buffer) {
        super.writePayload(buffer);
        buffer.writeByte(filterMode.ordinal());
        buffer.writeByte(currentFilter);
        buffer.writeBoolean(filterValid);
    }

    @Override
    public void readPayload(FriendlyByteBuf buffer, Object ctx) throws java.io.IOException {
        super.readPayload(buffer, ctx);
        filterMode = FilterMode.get(buffer.readUnsignedByte());
        currentFilter = buffer.readUnsignedByte() % filterStacks.size();
        filterValid = buffer.readBoolean();
    }

    @Override
    public boolean onPipeActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        // GUI opening — BCTransportGuis not yet ported
        return false;
    }

    private IStackFilter getStackFilter() {
        switch (filterMode) {
            default:
            case WHITE_LIST:
                if (!hasAnyFilter()) {
                    return stack -> true;
                }
                return stack -> {
                    for (ItemStack filter : filterStacks) {
                        if (!filter.isEmpty() && StackUtil.isMatchingItemOrList(filter, stack)) {
                            return true;
                        }
                    }
                    return false;
                };
            case BLACK_LIST:
                return stack -> {
                    for (ItemStack filter : filterStacks) {
                        if (!filter.isEmpty() && StackUtil.isMatchingItemOrList(filter, stack)) {
                            return false;
                        }
                    }
                    return true;
                };
            case ROUND_ROBIN:
                return (comparison) -> {
                    ItemStack filter = filterStacks.get(currentFilter);
                    return StackUtil.isMatchingItemOrList(filter, comparison);
                };
        }
    }

    @Override
    protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        if (filterStacks.get(currentFilter).isEmpty()) {
            advanceFilter();
        }
        int extracted = flow.tryExtractItems(1, getCurrentDir(), null, getStackFilter(), simulate);
        if (extracted > 0 && filterMode == FilterMode.ROUND_ROBIN && !simulate) {
            advanceFilter();
        }
        return extracted;
    }

    private void advanceFilter() {
        int lastFilter = currentFilter;
        filterValid = false;
        while (true) {
            currentFilter++;
            if (currentFilter >= filterStacks.size()) {
                currentFilter = 0;
            }
            if (!filterStacks.get(currentFilter).isEmpty()) {
                filterValid = true;
                break;
            }
            if (currentFilter == lastFilter) {
                break;
            }
        }
        if (lastFilter != currentFilter) {
            pipe.getHolder().scheduleNetworkGuiUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }

    private boolean hasAnyFilter() {
        for (ItemStack stack : filterStacks) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        for (ItemStack stack : filterStacks) {
            if (!stack.isEmpty()) {
                toDrop.add(stack);
            }
        }
    }
}
