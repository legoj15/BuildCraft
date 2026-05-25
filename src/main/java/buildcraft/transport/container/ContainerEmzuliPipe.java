package buildcraft.transport.container;

import java.util.EnumMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.Widget_Neptune;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;
import buildcraft.transport.tile.TilePipeHolder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("this-escape")
public class ContainerEmzuliPipe extends ContainerBC_Neptune {
    public final PipeBehaviourEmzuli behaviour;
    public final EnumMap<SlotIndex, PaintWidget> paintWidgets = new EnumMap<>(SlotIndex.class);
    private final IPipeHolder pipeHolder;

    /** Client-side constructor (from network). */
    public ContainerEmzuliPipe(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBehaviour(playerInv, buf));
    }

    /** Server-side constructor. */
    public ContainerEmzuliPipe(int containerId, Inventory playerInv, PipeBehaviourEmzuli behaviour) {
        super(BCTransportMenuTypes.EMZULI_PIPE.get(), containerId, playerInv.player);
        this.behaviour = behaviour;
        this.pipeHolder = behaviour.pipe.getHolder();

        // 4 phantom filter slots — positions match 1.12.2
        addSlot(new SlotPhantom(behaviour.invFilters, 0, 25, 21));
        addSlot(new SlotPhantom(behaviour.invFilters, 1, 25, 49));
        addSlot(new SlotPhantom(behaviour.invFilters, 2, 134, 21));
        addSlot(new SlotPhantom(behaviour.invFilters, 3, 134, 49));

        // Player inventory at y=84
        addFullPlayerInventory(8, 84);

        // Create paint widgets for colour cycling
        for (SlotIndex index : SlotIndex.VALUES) {
            PaintWidget widget = new PaintWidget(this, index);
            addWidget(widget);
            paintWidgets.put(index, widget);
        }
    }

    private static PipeBehaviourEmzuli getBehaviour(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TilePipeHolder holder && holder.getPipe() != null) {
                if (holder.getPipe().getBehaviour() instanceof PipeBehaviourEmzuli emzuli) {
                    return emzuli;
                }
            }
        }
        throw new IllegalStateException("No emzuli pipe behaviour at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return pipeHolder.canPlayerInteract(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Phantom slots don't support shift-clicking items into them
        return ItemStack.EMPTY;
    }

    /** Widget for cycling the paint colour assigned to an Emzuli slot. */
    public static class PaintWidget extends Widget_Neptune<ContainerEmzuliPipe> {
        public final SlotIndex index;

        public PaintWidget(ContainerEmzuliPipe container, SlotIndex index) {
            super(container);
            this.index = index;
        }

        /** Client → server: set the colour for this slot. */
        public void setColour(DyeColor colour) {
            sendWidgetData((buffer) -> {
                buffer.writeByte(colour == null ? -1 : colour.getId());
            });
        }

        @Override
        public void handleWidgetDataServer(IPayloadContext ctx, PacketBufferBC buffer) {
            int c = buffer.readByte();
            DyeColor colour = (c >= 0 && c < 16) ? DyeColor.byId(c) : null;
            if (colour == null) {
                container.behaviour.slotColours.remove(index);
            } else {
                container.behaviour.slotColours.put(index, colour);
            }
            container.behaviour.pipe.getHolder().scheduleNetworkGuiUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }
}
