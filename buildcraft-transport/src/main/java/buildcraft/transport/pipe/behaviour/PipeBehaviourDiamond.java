package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeFaceTex;

import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.container.ContainerDiamondPipe;

/** Base class for diamond (sorting) and diamond-wood (emerald/filtered extraction) pipes.
 * Provides a filter inventory per direction. */
public abstract class PipeBehaviourDiamond extends PipeBehaviour {

    public static final int FILTERS_PER_SIDE = 9;

    /** Filter inventory — 54 phantom slots (9 per direction). */
    public final ItemHandlerSimple filters = new ItemHandlerSimple(FILTERS_PER_SIDE * 6);

    public PipeBehaviourDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamond(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        CompoundTag filtersTag = nbt.getCompoundOrEmpty("filters");
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
                    return Component.translatable("gui.buildcrafttransport.pipe_diamond.title");
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
