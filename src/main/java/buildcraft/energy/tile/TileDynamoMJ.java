package buildcraft.energy.tile;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.mj.MjBatteryReceiver;

@SuppressWarnings("this-escape")
public class TileDynamoMJ extends TileEngineGearBase implements IBCMenuProvider {
    public static final long MAX_MJ = 1000 * MjAPI.MJ;

    private final MjBattery mjBattery;
    private final MjBatteryReceiver mjConnector;

    public TileDynamoMJ(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.DYNAMO_MJ.get(), pos, state, 0, MAX_FE);
        mjBattery = new MjBattery(MAX_MJ);
        mjConnector = new MjBatteryReceiver(mjBattery);
    }

    @Nonnull
    @Override
    protected IMjConnector createConnector() {
        return mjConnector;
    }

    public MjBattery getMjBattery() {
        return mjBattery;
    }

    public MjBatteryReceiver getMjReceiver() {
        return mjConnector;
    }

    @Override
    public boolean isBurning() {
        return mjBattery.getStored() > 0 && isRedstonePowered;
    }

    public int getFeProductionRate(long mjInput) {
        long mjPerRf = MjAPI.getRfConversion().mjPerRf;
        if (mjPerRf == 0) return 0;
        return (int) (mjInput / mjPerRf);
    }

    @Override
    protected void engineUpdate() {
        // ALWAYS attempt to push FE off to receivers, regardless of generation limits
        sendFeToReceiver();

        currentOutput = 0;

        long mjStored = mjBattery.getStored();
        if (mjStored <= 0) return;

        if (isRedstonePowered) {
            long mjPerRf = MjAPI.getRfConversion().mjPerRf;
            if (mjPerRf == 0) return;

            int genFe = getFeProductionRate(getMjPerTick());
            int maxFe = (int) Math.min(genFe, mjStored / mjPerRf);

            // Dynamically limit generation by available space
            int currentFe = getCurrentFe();
            maxFe = Math.min(maxFe, MAX_FE - currentFe);

            if (maxFe <= 0) return;

            if (mjBattery.extractPower(maxFe * mjPerRf)) {
                currentOutput = maxFe;
                energyStorage.set(currentFe + maxFe);
                addBurnHeat();
            }
        } else {
            currentOutput = 0;
        }
    }

    private void sendFeToReceiver() {
        int currentFe = getCurrentFe();
        if (level == null || currentFe <= 0) return;
        //? if >=1.21.10 {
        EnergyHandler receiver = getFeReceiver(orientation);
        if (receiver == null) return;
        // Because EnergyHandler strictly requires a transaction context:
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = receiver.insert(currentFe, transaction);
            if (accepted > 0) {
                energyStorage.set(currentFe - accepted);
                transaction.commit();
            }
        }
        //?} else {
        /*net.neoforged.neoforge.energy.IEnergyStorage receiver = getFeReceiver(orientation);
        if (receiver == null) return;
        int accepted = receiver.receiveEnergy(currentFe, false);
        if (accepted > 0) {
            energyStorage.set(currentFe - accepted);
        }*/
        //?}
    }

    @Nullable
    //? if >=1.21.10 {
    public EnergyHandler getFeReceiver(Direction side) {
    //?} else {
    /*public net.neoforged.neoforge.energy.IEnergyStorage getFeReceiver(Direction side) {*/
    //?}
        // Engine chaining (1.12.2 parity): hop through up to getMaxChainLength() further Dynamos facing this
        // same way to reach the FE receiver at the end of the line. Same walk as the base engine's MJ
        // getReceiverToPower — only the terminus lookup differs (raw FE handler, no canConnect check).
        return walkChain(side, targetPos -> {
            //? if >=1.21.10 {
            return level.getCapability(Capabilities.Energy.BLOCK, targetPos, side.getOpposite());
            //?} else {
            /*return level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, side.getOpposite());*/
            //?}
        });
    }

    @Nullable
    @Override
    public IMjReceiver getReceiverToPower(Direction side) {
        if (getFeReceiver(side) != null) {
            return new IMjReceiver() {
                @Override public long getPowerRequested() { return 1; }
                @Override public long receivePower(long microJoules, boolean simulate) { return 0; }
                @Override public boolean canConnect(IMjConnector other) { return true; }
            };
        }
        return null;
    }

    @Override
    public long getMaxPower() {
        return MAX_MJ;
    }

    @Override
    public long minPowerReceived() {
        return 0; // Not a generator to buffer
    }

    @Override
    public long maxPowerReceived() {
        return 0;
    }

    @Override
    public long maxPowerExtracted() {
        return 0;
    }

    @Override
    public long extractPower(long min, long max, boolean doExtract) {
        if (!doExtract && currentOutput > 0) {
            return Math.max(min, 1);
        }
        return 0;
    }

    @Override
    protected void sendPower(@Nullable IMjReceiver receiver) {
        // MJ Dynamo pushes FE in engineUpdate(). Do not zero out currentOutput here.
    }

    @Override
    public long getCurrentOutput() {
        return currentOutput; // Returns the generated FE
    }

    @Override
    protected int getMaxChainLength() {
        return 3;
    }

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putLong("mjStored", mjBattery.getStored());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);

        CompoundTag mjTag = new CompoundTag();
        mjTag.putLong("stored", input.getLongOr("mjStored", 0L));
        mjBattery.deserializeNbt(mjTag);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.mj_dynamo");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new buildcraft.energy.container.ContainerDynamoMJ(containerId, playerInv, this);
    }
}
