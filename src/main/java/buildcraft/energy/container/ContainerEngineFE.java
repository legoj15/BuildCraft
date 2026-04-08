package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import net.neoforged.neoforge.items.SlotItemHandler;

import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.gui.ContainerBC_Neptune;

public class ContainerEngineFE extends ContainerBC_Neptune {
    public final TileEngineFE engine;
    private final ContainerData data;

    private static final int DATA_POWER_HI = 0;
    private static final int DATA_POWER_LO = 1;
    private static final int DATA_HEAT = 2;
    private static final int DATA_OUTPUT_HI = 3;
    private static final int DATA_OUTPUT_LO = 4;
    private static final int DATA_POWER_STAGE = 5;
    private static final int DATA_IS_BURNING_ENGINE = 6;
    private static final int DATA_FE_STORED = 7;
    private static final int DATA_COUNT = 8;

    public ContainerEngineFE(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    public ContainerEngineFE(int containerId, Inventory playerInv, TileEngineFE engine) {
        super(BCEnergyMenuTypes.ENGINE_FE.get(), containerId, playerInv.player);
        this.engine = engine;

        if (engine != null && engine.getLevel() != null && !engine.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_POWER_HI -> (int) (engine.getPower() >>> 32);
                        case DATA_POWER_LO -> (int) (engine.getPower() & 0xFFFFFFFFL);
                        case DATA_HEAT -> Float.floatToIntBits(engine.getHeat());
                        case DATA_OUTPUT_HI -> (int) (engine.currentOutput >>> 32);
                        case DATA_OUTPUT_LO -> (int) (engine.currentOutput & 0xFFFFFFFFL);
                        case DATA_POWER_STAGE -> engine.getPowerStage().ordinal();
                        case DATA_IS_BURNING_ENGINE -> engine.isBurning() ? 1 : 0;
                        case DATA_FE_STORED -> engine.getCurrentFe();
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) { }

                @Override
                public int getCount() { return DATA_COUNT; }
            };
        } else {
            SimpleContainerData clientData = new SimpleContainerData(DATA_COUNT);
            clientData.set(DATA_HEAT, Float.floatToIntBits(TileEngineBase_BC8.MIN_HEAT));
            this.data = clientData;
        }

        addDataSlots(this.data);

        if (engine != null) {
            for (int slot = 0; slot < 4; slot++) {
                addSlot(new SlotItemHandler(engine.upgrades, slot, 62 + 18 * slot, 44));
            }
        }

        addFullPlayerInventory(8, 95, playerInv);
    }

    private static TileEngineFE getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileEngineFE eng) return eng;
        }
        return null;
    }

    public long getSyncedPower() {
        return ((long) data.get(DATA_POWER_HI) << 32) | (data.get(DATA_POWER_LO) & 0xFFFFFFFFL);
    }

    public float getSyncedHeat() {
        return Float.intBitsToFloat(data.get(DATA_HEAT));
    }

    public buildcraft.api.enums.EnumPowerStage getSyncedPowerStage() {
        int ordinal = data.get(DATA_POWER_STAGE);
        buildcraft.api.enums.EnumPowerStage[] values = buildcraft.api.enums.EnumPowerStage.values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return buildcraft.api.enums.EnumPowerStage.BLUE;
    }

    public boolean isSyncedBurningEngine() {
        return data.get(DATA_IS_BURNING_ENGINE) != 0;
    }

    public long getSyncedCurrentOutput() {
        return ((long) data.get(DATA_OUTPUT_HI) << 32) | (data.get(DATA_OUTPUT_LO) & 0xFFFFFFFFL);
    }

    public int getSyncedFeStored() {
        return data.get(DATA_FE_STORED);
    }

    @Override
    public boolean stillValid(Player player) {
        if (engine == null || engine.isRemoved()) return false;
        return player.distanceToSqr(
            engine.getBlockPos().getX() + 0.5,
            engine.getBlockPos().getY() + 0.5,
            engine.getBlockPos().getZ() + 0.5
        ) <= 64.0;
    }
}
