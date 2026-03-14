package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeFaceTex;

import buildcraft.lib.misc.NBTUtilBC;

/** Base class for diamond (sorting) and diamond-wood (emerald/filtered extraction) pipes.
 * Provides a filter inventory per direction. */
public abstract class PipeBehaviourDiamond extends PipeBehaviour {

    public static final int FILTERS_PER_SIDE = 9;

    /** Simplified filter inventory — stores 54 slots (9 per direction).
     * In 1.12.2 this was ItemHandlerSimple; here we use a flat NonNullList until
     * ItemHandlerSimple is ported. */
    public final NonNullList<ItemStack> filterStacks = NonNullList.withSize(FILTERS_PER_SIDE * 6, ItemStack.EMPTY);

    public PipeBehaviourDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamond(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        CompoundTag filtersTag = nbt.getCompoundOrEmpty("filters");
        for (int i = 0; i < filterStacks.size(); i++) {
            String key = "slot" + i;
            CompoundTag itemTag = filtersTag.getCompoundOrEmpty(key);
            if (!itemTag.isEmpty()) {
                filterStacks.set(i, NBTUtilBC.itemStackFromNBT(itemTag));
            }
        }
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
        return nbt;
    }

    @Override
    public PipeFaceTex getTextureData(@Nullable Direction face) {
        return PipeFaceTex.get(face == null ? 0 : face.ordinal() + 1);
    }

    @Override
    public boolean onPipeActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        // GUI opening — BCTransportGuis not yet ported
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
