package buildcraft.energy.tile;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import buildcraft.api.mj.MjRfConversion;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;

public class TileEngineFE extends TileEngineBase_BC8 {
    public static final int MAX_FE = 10_000;
    public static final float HEAT_RATE = 0.06f;
    public static final float COOLDOWN_RATE = 0.01f;

    public static final Map<Item, Long> UPGRADE_VALUES = new LinkedHashMap<>();

    // Map gears to increased MJ/t output
    public static void initUpgrades() {
        if (UPGRADE_VALUES.isEmpty()) {
            UPGRADE_VALUES.put(BCCoreItems.GEAR_IRON.get(), MjAPI.MJ * 2);
            UPGRADE_VALUES.put(BCCoreItems.GEAR_GOLD.get(), MjAPI.MJ * 3);
        }
    }


    public final buildcraft.lib.tile.item.ItemHandlerSimple upgrades = 
        new buildcraft.lib.tile.item.ItemHandlerSimple(4, (handler, slot, bef, aft) -> setChanged());

    {
        upgrades.setChecker((slot, stack) -> {
            initUpgrades();
            return UPGRADE_VALUES.containsKey(stack.getItem());
        });
        upgrades.setLimitedInsertor(1);
    }

    public final SimpleEnergyHandler energyStorage = new SimpleEnergyHandler(MAX_FE, MAX_FE, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };

    public TileEngineFE(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.ENGINE_FE.get(), pos, state);
    }

    public int getCurrentFe() {
        return (int) energyStorage.getAmountAsLong();
    }

    public void setCurrentFe(int fe) {
        energyStorage.set(Math.max(0, Math.min(MAX_FE, fe)));
    }

    @Override
    public boolean isBurning() {
        return getCurrentFe() > 0 && isRedstonePowered;
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

    public int getFeConsumptionRate() {
        final long mjPerTick = getMjPerTick();
        long mjPerRf = MjRfConversion.createParsed(BCLibConfig.mjRfConversionAmount.get()).mjPerRf;
        if (mjPerRf == 0) return 0;
        return (int) (mjPerTick / mjPerRf);
    }

    @Override
    protected void engineUpdate() {
        // Actively pull FE from adjacent tiles (including FE pipe sections)
        pullFeFromNeighbors();

        int currentFe = getCurrentFe();
        if (currentFe <= 0) return;

        if (isRedstonePowered) {
            long mjPerRf = MjRfConversion.createParsed(BCLibConfig.mjRfConversionAmount.get()).mjPerRf;
            int maxFe = getFeConsumptionRate();

            int feConsumed = Math.min(currentFe, maxFe);
            long mjGenerated = feConsumed * mjPerRf;

            if (power + mjGenerated >= getMaxPower()) {
                return;
            }

            currentOutput = mjGenerated;
            power += mjGenerated;
            energyStorage.set(currentFe - feConsumed);
            heat += HEAT_RATE;
            if (heat >= 200) {
                heat = 200;
            }
        }
    }

    /** Pull FE from adjacent blocks on non-facing sides. */
    private void pullFeFromNeighbors() {
        int currentFe = getCurrentFe();
        if (level == null || currentFe >= MAX_FE) return;
        for (Direction dir : Direction.values()) {
            if (dir == orientation) continue; // facing side is MJ output
            if (currentFe >= MAX_FE) break;
            BlockPos neighborPos = getBlockPos().relative(dir);
            EnergyHandler handler = level.getCapability(
                Capabilities.Energy.BLOCK, neighborPos, dir.getOpposite());
            if (handler == null) continue;
            int want = MAX_FE - currentFe;
            if (want <= 0) break;
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(want, transaction);
                if (extracted > 0) {
                    currentFe += extracted;
                    energyStorage.set(currentFe);
                    transaction.commit();
                }
            }
        }
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

    @Nonnull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public long getMaxPower() {
        return 1000 * MjAPI.MJ;
    }

    @Override
    public long minPowerReceived() {
        return 0;
    }

    @Override
    public long maxPowerReceived() {
        return 200 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 500 * MjAPI.MJ;
    }

    @Override
    public long getCurrentOutput() {
        if (getCurrentFe() > 0) {
            return getMjPerTick();
        } else {
            return 0;
        }
    }

    @Override
    public float explosionRange() {
        return 4;
    }

    @Override
    protected int getMaxChainLength() {
        return 4;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("currentFe", getCurrentFe());
        output.store("upgrades", net.minecraft.nbt.CompoundTag.CODEC, upgrades.serializeNBT());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setCurrentFe(input.getIntOr("currentFe", 0));
        upgrades.deserializeNBT(input.read("upgrades", net.minecraft.nbt.CompoundTag.CODEC).orElseGet(net.minecraft.nbt.CompoundTag::new));
    }
}
