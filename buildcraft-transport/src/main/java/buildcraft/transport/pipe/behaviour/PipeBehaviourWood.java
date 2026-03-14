package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

public class PipeBehaviourWood extends PipeBehaviourDirectional implements IMjRedstoneReceiver {
    private final MjBattery battery = new MjBattery(1024 * MjAPI.MJ);

    public PipeBehaviourWood(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWood(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        battery.deserializeNBT(nbt.getCompoundOrEmpty("battery"));
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.put("battery", battery.serializeNBT());
        return nbt;
    }

    @Override
    public int getTextureIndex(@Nullable Direction face) {
        return face == currentDir.face ? 0 : 1;
    }

    @Override
    protected boolean canFaceDirection(Direction dir) {
        return dir != null && pipe.isConnected(dir)
            && pipe.getConnectedType(dir) == IPipe.ConnectedType.TILE;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (pipe.getHolder().getPipeWorld().isClientSide()) {
            return;
        }
        Direction dir = getCurrentDir();
        if (dir != null) {
            long potential = battery.extractPower(0, MjAPI.MJ);
            if (potential > 0) {
                PipeFlow flow = pipe.getFlow();
                if (flow instanceof IFlowItems) {
                    int maxItems = (int) (potential / (MjAPI.MJ / 2));
                    if (maxItems > 0) {
                        int extracted = extractItems((IFlowItems) flow, dir, maxItems, false);
                        if (extracted > 0) {
                            battery.extractPower(0, extracted * (MjAPI.MJ / 2));
                        }
                    }
                }
            }
        }
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
        return battery.getCapacity() - battery.getStored();
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return battery.addPowerChecking(microJoules, simulate);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        // Expose this wood pipe as an MJ receiver/connector so engines can discover it
        // (matches 1.12.2 MjCapabilityHelper behavior)
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
