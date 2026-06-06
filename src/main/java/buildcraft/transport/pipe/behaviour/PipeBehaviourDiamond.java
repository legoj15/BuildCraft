package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeFaceTex;

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.container.ContainerDiamondPipe;

/** Base class for diamond (sorting) and diamond-wood (emerald/filtered extraction) pipes.
 * Provides a filter inventory per direction. */
@SuppressWarnings("this-escape")
public abstract class PipeBehaviourDiamond extends PipeBehaviour {

    public static final int FILTERS_PER_SIDE = 9;

    private static final Identifier ADVANCEMENT_NEED_LIST =
        Identifier.parse("buildcraftunofficial:too_many_pipe_filters");

    /** Filter inventory — 54 phantom slots (9 per direction). */
    public final ItemHandlerSimple filters =
        new ItemHandlerSimple(FILTERS_PER_SIDE * 6, this::onFilterSlotChange);

    public PipeBehaviourDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamond(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        CompoundTag filtersTag = NBTUtilBC.getCompound(nbt, "filters");
        if (!filtersTag.isEmpty()) {
            filters.deserializeNBT(filtersTag);
        }
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.put("filters", filters.serializeNBT());
        return nbt;
    }

    @Override
    public void readFromNbt(CompoundTag nbt) {
        super.readFromNbt(nbt);
        filters.deserializeNBT(NBTUtilBC.getCompound(nbt, "filters"));
    }

    /** Grants the "too many pipe filters" advancement (and its List recipe reward) once a
     * player has crammed ≥ 7 of 9 filter slots on any single side — the nudge that a List
     * item would consolidate this. Mirrors the 1.12.2 PipeBehaviourDiamond hook. */
    protected void onFilterSlotChange(ItemHandlerSimple handler, int slot, ItemStack before, ItemStack after) {
        Level level = pipe.getHolder().getPipeWorld();
        if (level.isClientSide()) {
            return;
        }
        int baseIndex = FILTERS_PER_SIDE * (slot / FILTERS_PER_SIDE);
        int count = 0;
        for (int i = 0; i < FILTERS_PER_SIDE; i++) {
            if (!filters.getStackInSlot(baseIndex + i).isEmpty()) {
                count++;
            }
        }
        if (count >= FILTERS_PER_SIDE - 2) {
            GameProfile owner = pipe.getHolder().getOwner();
            if (owner != null && GameProfileUtil.getId(owner) != null) {
                AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(owner), level, ADVANCEMENT_NEED_LIST);
            }
        }
    }

    @Override
    public PipeFaceTex getTextureData(@Nullable Direction face) {
        return PipeFaceTex.get(face == null ? 0 : face.ordinal() + 1);
    }

    @Override
    public boolean onPipeActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            final PipeBehaviourDiamond self = this;
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.buildcraftunofficial.pipe_diamond.title");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player p) {
                    return new ContainerDiamondPipe(containerId, playerInv, self);
                }
            }, (buf) -> {
                buf.writeBlockPos(pipe.getHolder().getPipePos());
            });
        }
        return true;
    }

    // Phantom slots — filter contents are NOT real items and should not drop.
}
