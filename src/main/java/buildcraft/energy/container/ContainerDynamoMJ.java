package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;



import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.gui.ContainerBC_Neptune;

public class ContainerDynamoMJ extends ContainerBC_Neptune {
    public final TileDynamoMJ dynamo;
    private final ContainerData data;

    private static final int DATA_POWER_HI = 0; // MJ stored
    private static final int DATA_POWER_LO = 1;
    private static final int DATA_HEAT = 2;
    private static final int DATA_OUTPUT_HI = 3;
    private static final int DATA_OUTPUT_LO = 4;
    private static final int DATA_POWER_STAGE = 5;
    private static final int DATA_IS_BURNING_ENGINE = 6;
    private static final int DATA_FE_STORED = 7;
    private static final int DATA_COUNT = 8;

    public ContainerDynamoMJ(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    public ContainerDynamoMJ(int containerId, Inventory playerInv, TileDynamoMJ dynamo) {
        super(BCEnergyMenuTypes.DYNAMO_MJ.get(), containerId, playerInv.player);
        this.dynamo = dynamo;

        if (dynamo != null && dynamo.getLevel() != null && !dynamo.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_POWER_HI -> (int) (dynamo.getMjBattery().getStored() >>> 32);
                        case DATA_POWER_LO -> (int) (dynamo.getMjBattery().getStored() & 0xFFFFFFFFL);
                        case DATA_HEAT -> Float.floatToIntBits(dynamo.getHeat());
                        case DATA_OUTPUT_HI -> (int) (dynamo.currentOutput >>> 32);
                        case DATA_OUTPUT_LO -> (int) (dynamo.currentOutput & 0xFFFFFFFFL);
                        case DATA_POWER_STAGE -> dynamo.getPowerStage().ordinal();
                        case DATA_IS_BURNING_ENGINE -> dynamo.isBurning() ? 1 : 0;
                        case DATA_FE_STORED -> dynamo.getCurrentFe();
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

        if (dynamo != null) {
            for (int slot = 0; slot < 4; slot++) {
                addSlot(new buildcraft.lib.gui.slot.SlotBase(dynamo.upgrades, slot, 44 + 18 * slot, 44));
            }
        }

        addFullPlayerInventory(8, 95, playerInv);
    }

    private static TileDynamoMJ getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileDynamoMJ dyn) return dyn;
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
        if (dynamo == null || dynamo.isRemoved()) return false;
        return player.distanceToSqr(
            dynamo.getBlockPos().getX() + 0.5,
            dynamo.getBlockPos().getY() + 0.5,
            dynamo.getBlockPos().getZ() + 0.5
        ) <= 64.0;
    }
}
