package buildcraft.energy.tile;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import buildcraft.api.mj.MjRfConversion;
import net.neoforged.neoforge.items.ItemStackHandler;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.mj.MjBatteryReceiver;

public class TileDynamoMJ extends TileEngineBase_BC8 {
    public static final int MAX_FE = 10_000;
    public static final long MAX_MJ = 1000 * MjAPI.MJ;

    public static final float HEAT_RATE = 0.06f;
    public static final float COOLDOWN_RATE = 0.01f;

    public static final Map<Item, Long> UPGRADE_VALUES = new LinkedHashMap<>();

    // Map gears to increased FE/t output capacity
    public static void initUpgrades() {
        if (UPGRADE_VALUES.isEmpty()) {
            UPGRADE_VALUES.put(BCCoreItems.GEAR_IRON.get(), MjAPI.MJ * 2);
            UPGRADE_VALUES.put(BCCoreItems.GEAR_GOLD.get(), MjAPI.MJ * 3);
        }
    }

    private final MjBattery mjBattery;
    private final MjBatteryReceiver mjConnector;

    private int currentFe;

    public final ItemStackHandler upgrades = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            initUpgrades();
            return UPGRADE_VALUES.containsKey(stack.getItem());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public final EnergyHandler energyStorage = new EnergyHandler() {
        @Override
        public int insert(int maxReceive, TransactionContext transaction) {
            return 0; // Extract only
        }

        @Override
        public int extract(int maxExtract, TransactionContext transaction) {
            int max = Math.min(currentFe, maxExtract);
            if (max <= 0) return 0;
            // Simulated transactions are supported via SnapshotJournal, but for this simple
            // block we will just extract directly.
            currentFe -= max;
            setChanged();
            return max;
        }

        @Override
        public long getAmountAsLong() {
            return currentFe;
        }

        @Override
        public long getCapacityAsLong() {
            return MAX_FE;
        }
    };

    public TileDynamoMJ(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.DYNAMO_MJ.get(), pos, state);
        mjBattery = new MjBattery(MAX_MJ);
        mjConnector = new MjBatteryReceiver(mjBattery);
    }

    public int getCurrentFe() {
        return currentFe;
    }

    public void setCurrentFe(int fe) {
        this.currentFe = Math.max(0, Math.min(MAX_FE, fe));
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

    public long getMjPerTick() {
        initUpgrades();
        long value = MjAPI.MJ * 4;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Long add = UPGRADE_VALUES.get(stack.getItem());
            if (add != null) {
                value += add;
            }
        }
        return value;
    }

    public int getFeProductionRate(long mjInput) {
        long mjPerRf = MjRfConversion.createParsed(BCLibConfig.mjRfConversionAmount.get()).mjPerRf;
        if (mjPerRf == 0) return 0;
        return (int) (mjInput / mjPerRf);
    }

    @Override
    protected void engineUpdate() {
        long mjStored = mjBattery.getStored();
        if (mjStored <= 0) return;

        if (isRedstonePowered) {
            long mjPerRf = MjRfConversion.createParsed(BCLibConfig.mjRfConversionAmount.get()).mjPerRf;
            if (mjPerRf == 0) return;

            int genFe = getFeProductionRate(getMjPerTick());
            int maxFe = (int) Math.min(genFe, mjStored / mjPerRf);

            // Dynamically limit generation by available space
            maxFe = Math.min(maxFe, MAX_FE - currentFe);

            if (maxFe <= 0) return;

            if (mjBattery.extractPower(maxFe * mjPerRf)) {
                currentOutput = maxFe;
                currentFe += maxFe;
                heat += HEAT_RATE;
                if (heat >= 200) {
                    heat = 200;
                }

                // Push FE to adjacent tiles
                sendFeToReceiver();
            }
        } else {
            currentOutput = 0;
        }
    }

    private void sendFeToReceiver() {
        if (level == null || currentFe <= 0) return;
        EnergyHandler receiver = getFeReceiver(orientation);
        if (receiver != null) {
            // Because EnergyHandler strictly requires a transaction context:
            try (net.neoforged.neoforge.transfer.transaction.Transaction transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int accepted = receiver.insert(currentFe, transaction);
                if (accepted > 0) {
                    currentFe -= accepted;
                    transaction.commit();
                }
            }
        }
    }

    @Nullable
    public EnergyHandler getFeReceiver(Direction side) {
        if (level == null) return null;
        BlockPos targetPos = getBlockPos().relative(side);
        BlockEntity tile = level.getBlockEntity(targetPos);
        if (tile == null) return null;

        if (tile.getClass() == getClass() && ((TileDynamoMJ) tile).orientation != orientation) {
             return null;
        }

        return level.getCapability(Capabilities.Energy.BLOCK, targetPos, side.getOpposite());
    }

    @Override
    public void updateHeatLevel() {
        if (heat > MIN_HEAT) {
            heat -= COOLDOWN_RATE;
        }
        if (heat <= MIN_HEAT) {
            heat = MIN_HEAT;
        }
        getPowerStage();
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
    public long getCurrentOutput() {
        return currentOutput; // Returns the generated FE
    }

    @Override
    public float explosionRange() {
        return 4;
    }

    @Override
    protected int getMaxChainLength() {
        return 3;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("currentFe", currentFe);
        upgrades.serialize(output);
        output.putLong("mjStored", mjBattery.getStored());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        currentFe = input.getIntOr("currentFe", 0);
        upgrades.deserialize(input);
        
        CompoundTag mjTag = new CompoundTag();
        mjTag.putLong("stored", input.getLongOr("mjStored", 0L));
        mjBattery.deserializeNBT(mjTag);
    }
}
