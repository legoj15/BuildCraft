package buildcraft.transport.pipe.behaviour;

import java.util.EnumMap;
import java.util.EnumSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
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

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;

/** Emzuli pipe — filtered extraction with round-robin across 4 preset slots, each with a colour assignment.
 * Activated via gate statements (stubbed until BCTransportStatements is ported). */
public class PipeBehaviourEmzuli extends PipeBehaviourWood {

    public enum SlotIndex {
        SQUARE(DyeColor.RED),
        CIRCLE(DyeColor.GREEN),
        TRIANGLE(DyeColor.BLUE),
        CROSS(DyeColor.YELLOW);

        public static final SlotIndex[] VALUES = values();

        public final DyeColor colour;

        SlotIndex(DyeColor colour) {
            this.colour = colour;
        }

        public SlotIndex next() {
            switch (this) {
                case SQUARE: return CIRCLE;
                case CIRCLE: return TRIANGLE;
                case TRIANGLE: return CROSS;
                case CROSS: return SQUARE;
                default: throw new IllegalStateException("Unknown SlotIndex - " + this);
            }
        }
    }

    public final EnumMap<SlotIndex, DyeColor> slotColours = new EnumMap<>(SlotIndex.class);
    public final NonNullList<ItemStack> invFilters = NonNullList.withSize(4, ItemStack.EMPTY);
    private final EnumSet<SlotIndex> activeSlots;
    private final byte[] activatedTtl = new byte[SlotIndex.VALUES.length];
    private SlotIndex currentSlot = null;

    private final IStackFilter filter = this::filterMatches;

    public PipeBehaviourEmzuli(IPipe pipe) {
        super(pipe);
        activeSlots = EnumSet.noneOf(SlotIndex.class);
    }

    public PipeBehaviourEmzuli(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        CompoundTag filtersTag = nbt.getCompoundOrEmpty("Filters");
        for (int i = 0; i < invFilters.size(); i++) {
            CompoundTag itemTag = filtersTag.getCompoundOrEmpty("slot" + i);
            if (!itemTag.isEmpty()) {
                invFilters.set(i, NBTUtilBC.itemStackFromNBT(itemTag));
            }
        }
        activeSlots = EnumSet.noneOf(SlotIndex.class);
        currentSlot = NBTUtilBC.readEnum(nbt.get("currentSlot"), SlotIndex.class);
        for (SlotIndex index : SlotIndex.VALUES) {
            byte c = nbt.getByteOr("slotColors[" + index.ordinal() + "]", (byte) 0);
            if (c > 0 && c <= 16) {
                slotColours.put(index, DyeColor.byId(c - 1));
            }
        }
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        CompoundTag filtersTag = new CompoundTag();
        for (int i = 0; i < invFilters.size(); i++) {
            ItemStack stack = invFilters.get(i);
            if (!stack.isEmpty()) {
                filtersTag.put("slot" + i, NBTUtilBC.itemStackToNBT(stack));
            }
        }
        nbt.put("Filters", filtersTag);
        if (currentSlot != null) {
            nbt.put("currentSlot", NBTUtilBC.writeEnum(currentSlot));
        }
        for (SlotIndex index : SlotIndex.VALUES) {
            DyeColor c = slotColours.get(index);
            nbt.putByte("slotColors[" + index.ordinal() + "]", (byte) (c == null ? 0 : c.getId() + 1));
        }
        return nbt;
    }

    @Override
    public void writePayload(FriendlyByteBuf buffer) {
        super.writePayload(buffer);
        for (SlotIndex index : SlotIndex.VALUES) {
            DyeColor c = slotColours.get(index);
            buffer.writeByte(c == null ? -1 : c.getId());
        }
        buffer.writeByte(currentSlot == null ? -1 : currentSlot.ordinal());
    }

    @Override
    public void readPayload(FriendlyByteBuf buffer, Object ctx) throws java.io.IOException {
        super.readPayload(buffer, ctx);
        for (SlotIndex index : SlotIndex.VALUES) {
            int c = buffer.readByte();
            if (c >= 0 && c < 16) {
                slotColours.put(index, DyeColor.byId(c));
            } else {
                slotColours.remove(index);
            }
        }
        int slotOrd = buffer.readByte();
        currentSlot = (slotOrd >= 0 && slotOrd < SlotIndex.VALUES.length) ? SlotIndex.VALUES[slotOrd] : null;
    }

    @Override
    protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        if (currentSlot == null && activeSlots.size() > 0) {
            currentSlot = getNextSlot();
        }
        if (currentSlot == null) return 0;
        int extracted = flow.tryExtractItems(count, dir, slotColours.get(currentSlot), filter, simulate);
        if (extracted > 0 && !simulate) {
            currentSlot = getNextSlot();
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
        return extracted;
    }

    private boolean filterMatches(ItemStack stack) {
        if (currentSlot == null) return false;
        ItemStack current = invFilters.get(currentSlot.ordinal());
        return StackUtil.isMatchingItemOrList(current, stack);
    }

    @Override
    public void onTick() {
        super.onTick();
        if (pipe.getHolder().getPipeWorld().isClientSide()) {
            return;
        }
        for (SlotIndex index : SlotIndex.VALUES) {
            byte val = activatedTtl[index.ordinal()];
            if (val > 0) {
                val--;
                activatedTtl[index.ordinal()] = val;
            }
            if (val == 0) {
                activeSlots.remove(index);
                if (currentSlot == index) {
                    currentSlot = getNextSlot();
                    pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
                }
            }
        }
    }

    private SlotIndex getNextSlot() {
        SlotIndex current = currentSlot == null ? SlotIndex.CROSS : currentSlot;
        int i = SlotIndex.VALUES.length;
        while (i-- > 0) {
            current = current.next();
            if (activeSlots.contains(current) && !invFilters.get(current.ordinal()).isEmpty()) {
                return current;
            }
        }
        return null;
    }

    public SlotIndex getCurrentSlot() {
        return this.currentSlot;
    }

    public EnumSet<SlotIndex> getActiveSlots() {
        return this.activeSlots;
    }

    @Override
    public boolean onPipeActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        // GUI opening — BCTransportGuis not yet ported
        return false;
    }

    @Override
    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        for (ItemStack stack : invFilters) {
            if (!stack.isEmpty()) {
                toDrop.add(stack);
            }
        }
    }

    // Statement handlers removed — BCTransportStatements not yet ported
}
