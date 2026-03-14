package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

public class PipeBehaviourWood extends PipeBehaviourDirectional implements IMjRedstoneReceiver {

    /** Cost per item extracted, matching 1.12.2's BCTransportConfig.mjPerItem default (1 MJ per item). */
    private static final long MJ_PER_ITEM = MjAPI.MJ;

    public PipeBehaviourWood(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWood(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
    }

    @Override
    public int getTextureIndex(@Nullable Direction face) {
        // 1.12.2: TEX_FILLED(1) for extraction face, TEX_CLEAR(0) for others
        return (face != null && face == currentDir.face) ? 1 : 0;
    }

    @Override
    protected boolean canFaceDirection(Direction dir) {
        return dir != null && pipe.isConnected(dir)
            && pipe.getConnectedType(dir) == IPipe.ConnectedType.TILE;
    }

    // No onTick extraction — 1.12.2 extracts items directly in receivePower()

    /**
     * Attempt to extract items (or simulate extraction) using the given power budget.
     * Returns the leftover power that was NOT consumed.
     * Matches 1.12.2's PipeBehaviourWood.extract(long, boolean).
     */
    protected long extract(long power, boolean simulate) {
        if (power > 0 && getCurrentDir() != null) {
            PipeFlow flow = pipe.getFlow();
            if (flow instanceof IFlowItems) {
                IFlowItems itemFlow = (IFlowItems) flow;
                int maxItems = (int) (power / MJ_PER_ITEM);
                if (maxItems > 0) {
                    int extracted = extractItems(itemFlow, getCurrentDir(), maxItems, simulate);
                    if (extracted > 0) {
                        return power - extracted * MJ_PER_ITEM;
                    }
                }
            }
        }
        return power;
    }

    protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        return flow.tryExtractItems(count, dir, null, stack -> true, simulate);
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(@Nonnull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        // Only request power if we can actually extract items right now.
        // This is the key difference from the battery model:
        // simulate an extraction to see if items exist, and request only
        // as much power as we'd actually consume.
        final long power = 512 * MjAPI.MJ;
        return power - extract(power, true);
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        // Directly extract items when power is delivered.
        // Returns leftover power that was not consumed.
        return extract(microJoules, simulate);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        // Expose this wood pipe as an MJ receiver/connector so engines can discover it
        if (capability == MjAPI.CAP_RECEIVER || capability == MjAPI.CAP_CONNECTOR) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }

    // Prevent connecting to another wood pipe
    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWood);
    }

    @PipeEventHandler
    public static void sideCheck(PipeEventItem.SideCheck sideCheck) {
        // Wood pipes shouldn't push items out to the extraction side
    }
}
