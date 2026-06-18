package buildcraft.transport.pipe.behaviour;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.container.ContainerDiamondWoodPipe;

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

    public final ItemHandlerSimple filters = new ItemHandlerSimple(9);
    public FilterMode filterMode = FilterMode.WHITE_LIST;
    public int currentFilter = 0;
    public boolean filterValid = false;

    public PipeBehaviourWoodDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodDiamond(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        CompoundTag filtersTag = NBTUtilBC.getCompound(nbt, "filters");
        if (!filtersTag.isEmpty()) {
            filters.deserializeNBT(filtersTag);
        }
        filterMode = FilterMode.get(NBTUtilBC.getByte(nbt, "mode", (byte) 0));
        currentFilter = NBTUtilBC.getByte(nbt, "currentFilter", (byte) 0) % filters.getSlots();
        filterValid = hasAnyFilter();
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.put("filters", filters.serializeNBT());
        nbt.putByte("mode", (byte) filterMode.ordinal());
        nbt.putByte("currentFilter", (byte) currentFilter);
        return nbt;
    }

    @Override
    public void readFromNbt(CompoundTag nbt) {
        super.readFromNbt(nbt);
        filters.deserializeNBT(NBTUtilBC.getCompound(nbt, "filters"));
        filterMode = FilterMode.get(NBTUtilBC.getByte(nbt, "mode", (byte) 0));
        currentFilter = NBTUtilBC.getByte(nbt, "currentFilter", (byte) 0) % filters.getSlots();
        filterValid = hasAnyFilter();
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
        currentFilter = buffer.readUnsignedByte() % filters.getSlots();
        filterValid = buffer.readBoolean();
    }

    @Override
    public boolean onPipeActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        // 1.12.2: wrench cycles direction via PipeBehaviourDirectional
        if (isHoldingWrench(player)) {
            return super.onPipeActivate(player, trace, hitX, hitY, hitZ, part);
        }
        // Don't intercept pluggable placement
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && held.getItem() instanceof buildcraft.api.transport.IItemPluggable) {
            return false;
        }
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            final PipeBehaviourWoodDiamond self = this;
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.buildcraftunofficial.pipe_diamond_wood.title");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player p) {
                    return new ContainerDiamondWoodPipe(containerId, playerInv, self);
                }
            }, (buf) -> {
                buf.writeBlockPos(pipe.getHolder().getPipePos());
            });
        }
        return true;
    }

    private IStackFilter getStackFilter() {
        switch (filterMode) {
            default:
            case WHITE_LIST:
                if (!hasAnyFilter()) {
                    return stack -> true;
                }
                return stack -> {
                    for (int i = 0; i < filters.getSlots(); i++) {
                        ItemStack filter = filters.getStackInSlot(i);
                        if (!filter.isEmpty() && StackUtil.isMatchingItemOrList(filter, stack)) {
                            return true;
                        }
                    }
                    return false;
                };
            case BLACK_LIST:
                return stack -> {
                    for (int i = 0; i < filters.getSlots(); i++) {
                        ItemStack filter = filters.getStackInSlot(i);
                        if (!filter.isEmpty() && StackUtil.isMatchingItemOrList(filter, stack)) {
                            return false;
                        }
                    }
                    return true;
                };
            case ROUND_ROBIN:
                return (comparison) -> {
                    ItemStack filter = filters.getStackInSlot(currentFilter);
                    return StackUtil.isMatchingItemOrList(filter, comparison);
                };
        }
    }

    @Override
    protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        if (filters.getStackInSlot(currentFilter).isEmpty()) {
            advanceFilter();
        }
        int extracted = flow.tryExtractItems(1, getCurrentDir(), null, getStackFilter(), simulate);
        if (extracted > 0 && filterMode == FilterMode.ROUND_ROBIN && !simulate) {
            advanceFilter();
        }
        return extracted;
    }

    @Override
    protected FluidStack extractFluid(IFlowFluid flow, Direction dir, int millibuckets, boolean simulate) {
        // 1.12.2 parity: the emerald (wood-diamond) FLUID pipe applies its whitelist/blacklist to
        // fluid extraction. Without this override it inherited PipeBehaviourWood.extractFluid and
        // pulled any fluid, making its filter slots inert.
        switch (filterMode) {
            case WHITE_LIST: {
                if (!hasAnyFilter()) {
                    // Empty whitelist behaves like a plain wooden fluid pipe: extract any fluid.
                    return flow.tryExtractFluid(millibuckets, dir, null, simulate);
                }
                Object result = flow.tryExtractFluidAdv(millibuckets, dir, makeFluidFilter(false), simulate);
                return result instanceof FluidStack fs ? fs : null;
            }
            case BLACK_LIST: {
                Object result = flow.tryExtractFluidAdv(millibuckets, dir, makeFluidFilter(true), simulate);
                return result instanceof FluidStack fs ? fs : null;
            }
            case ROUND_ROBIN:
            default:
                // 1.12.2 parity: round-robin is unsupported for fluids.
                return null;
        }
    }

    /** Builds an {@link IFluidFilter} over the filter slots (each slot's contained fluid).
     *
     * @param inverted true for blacklist semantics, false for whitelist. */
    private IFluidFilter makeFluidFilter(boolean inverted) {
        return fluid -> {
            if (fluid == null || fluid.isEmpty()) {
                return false;
            }
            boolean anyMatch = false;
            for (int i = 0; i < filters.getSlots(); i++) {
                FluidStack f = getFilterFluid(i);
                if (!f.isEmpty() && FluidStack.isSameFluidSameComponents(f, fluid)) {
                    anyMatch = true;
                    break;
                }
            }
            return inverted != anyMatch;
        };
    }

    /** The fluid represented by the filter item in the given slot (e.g. a bucket's contents), or empty. */
    private FluidStack getFilterFluid(int slot) {
        ItemStack stack = filters.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
    }

    private void advanceFilter() {
        int lastFilter = currentFilter;
        filterValid = false;
        while (true) {
            currentFilter++;
            if (currentFilter >= filters.getSlots()) {
                currentFilter = 0;
            }
            if (!filters.getStackInSlot(currentFilter).isEmpty()) {
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
        for (int i = 0; i < filters.getSlots(); i++) {
            if (!filters.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    // Phantom slots — filter contents are NOT real items and should not drop.
}
