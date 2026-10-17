/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.tile;

import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
//?}

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.RegistryUtilBC;

public class TileEngineStone_BC8 extends TileEngineBase_BC8 {
    private static final net.minecraft.resources.Identifier ADVANCEMENT_POWERING_UP
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:powering_up");
    private static final net.minecraft.resources.Identifier ADVANCEMENT_LAVA_POWER
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:lava_power");

    private static final long MAX_OUTPUT = MjAPI.MJ;
    private static final long MIN_OUTPUT = MAX_OUTPUT / 3;
    private static final long eLimit = (MAX_OUTPUT - MIN_OUTPUT) * 20;

    // --- Fuel state ---
    public int burnTime = 0;
    public int totalBurnTime = 0;
    private long esum = 0;

    // Fuel slot: single ItemStack managed manually
    private ItemStack fuelStack = ItemStack.EMPTY;

    /**
     * ResourceHandler wrapper for the fuel slot.
     * In 1.12.2, ItemHandlerSimple registered with EnumAccess.BOTH exposed
     * this as a capability from all sides, allowing item pipes to insert fuel.
     * NeoForge 1.21.11 uses ItemStackResourceHandler (transaction-safe single-slot handler).
     */
    //? if >=1.21.10 {
    public final ItemStackResourceHandler fuelItemHandler = new ItemStackResourceHandler() {
        @Override
        protected ItemStack getStack() {
            return fuelStack;
        }

        @Override
        protected void setStack(ItemStack stack) {
            fuelStack = stack;
        }

        @Override
        protected boolean isValid(ItemResource resource) {
            // Only accept burnable items
            return isValidFuel(resource.toStack(1));
        }

        @Override
        protected void onRootCommit(ItemStack originalState) {
            setChanged();
        }
    };
    //?} else {
    /*// 1.21.1 has no Transfer API: expose the fuel slot as a classic single-slot IItemHandler.
    // Mirrors the modern ItemStackResourceHandler — insert only accepts burnable fuel and caps at
    // min(item max stack, 99); extract pulls whatever is in the slot; both mark the BE changed.
    public final net.neoforged.neoforge.items.IItemHandlerModifiable fuelItemHandler =
            new net.neoforged.neoforge.items.IItemHandlerModifiable() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? fuelStack : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot == 0) {
                fuelStack = stack;
                setChanged();
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && isValidFuel(stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !isValidFuel(stack)) return stack;
            int cap = Math.min(stack.getMaxStackSize(), net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE);
            if (fuelStack.isEmpty()) {
                int moved = Math.min(stack.getCount(), cap);
                if (!simulate) {
                    fuelStack = stack.copyWithCount(moved);
                    setChanged();
                }
                return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
            }
            if (!ItemStack.isSameItemSameComponents(fuelStack, stack)) return stack;
            int space = cap - fuelStack.getCount();
            if (space <= 0) return stack;
            int moved = Math.min(space, stack.getCount());
            if (!simulate) {
                fuelStack.grow(moved);
                setChanged();
            }
            return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || fuelStack.isEmpty()) return ItemStack.EMPTY;
            int extracted = Math.min(amount, fuelStack.getCount());
            ItemStack result = fuelStack.copyWithCount(extracted);
            if (!simulate) {
                fuelStack.shrink(extracted);
                setChanged();
            }
            return result;
        }
    };*/
    //?}

    public TileEngineStone_BC8(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.ENGINE_STONE.get(), pos, state);
    }


    // --- Fuel slot accessors (for container data sync) ---

    @Nonnull
    public ItemStack getFuelStack() {
        return fuelStack;
    }

    public void setFuelStack(@Nonnull ItemStack stack) {
        fuelStack = stack;
        setChanged();
    }

    public boolean isValidFuel(@Nonnull ItemStack stack) {
        return getBurnTime(stack) > 0;
    }

    private int getBurnTime(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || level == null) return 0;
        //? if >=1.21.10 {
        return stack.getBurnTime(null, level.fuelValues());
        //?} else {
        /*// 1.21.1 predates the FuelValues lookup (MC 1.21.2); getBurnTime is single-arg.
        return stack.getBurnTime(null);*/
        //?}
    }

    // --- Engine overrides ---

    @Nonnull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return burnTime > 0;
    }

    @Override
    protected void engineUpdate() {
        // Burn fuel
        if (burnTime > 0) {
            burnTime--;
            if (getPowerStage() != EnumPowerStage.OVERHEAT) {
                long output = getCurrentOutput();
                addPower(output);
            }
        }

        // Try to consume new fuel
        if (burnTime == 0 && isRedstonePowered) {
            int newBurn = getBurnTime(fuelStack);
            if (newBurn > 0) {
                burnTime = newBurn;
                totalBurnTime = newBurn;
                if (getOwner() != null && level != null) {
                    AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_POWERING_UP);
                    if (fuelStack.getItem() == net.minecraft.world.item.Items.LAVA_BUCKET) {
                        AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_LAVA_POWER);
                    }
                }

                // Consume one fuel item
                ItemStack consumed = fuelStack.copy();
                consumed.setCount(1);

                fuelStack.shrink(1);
                if (fuelStack.isEmpty()) {
                    fuelStack = ItemStack.EMPTY;
                }

                // Handle container items (e.g., empty bucket from lava bucket)
                //? if >=26.1 {
                // NeoForge's stack-sensitive remainder (a template, null if none), not the deprecated item-level
                // Item#getCraftingRemainder(), which ignores a stack-dependent remainder. Compiles against both
                // nodes' floors (26.1.0.1-beta, 26.2.0.0-beta) and pins; the declaring interface moved
                // mid-26.1.x but the ItemStack call site and descriptor are unchanged.
                net.minecraft.world.item.ItemStackTemplate containerTemplate = consumed.getCraftingRemainder();
                ItemStack container = containerTemplate != null ? containerTemplate.create() : ItemStack.EMPTY;
                //?} elif >=1.21.10 {
                /*// 1.21.10 / 1.21.11: NeoForge's stack-sensitive remainder (EMPTY if none), like 1.21.1 below — deliberately
                // not the deprecated item-level Item#getCraftingRemainder(), which ignores a stack-dependent remainder.
                ItemStack container = consumed.getCraftingRemainder();*/
                //?} else {
                /*// 1.21.1 has no Item.getCraftingRemainder(); use NeoForge's ItemStack-sensitive remainder.
                ItemStack container = consumed.getCraftingRemainingItem();*/
                //?}
                if (!container.isEmpty()) {
                    if (fuelStack.isEmpty()) {
                        fuelStack = container;
                    } else {
                        // Drop the container item — no room
                        if (level != null) {
                            net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                                level,
                                getBlockPos().getX() + 0.5,
                                getBlockPos().getY() + 1.0,
                                getBlockPos().getZ() + 0.5,
                                container
                            );
                            level.addFreshEntity(entity);
                        }
                    }
                }
                setChanged();
            }
        }
    }

    /** Add power to the engine's internal buffer. */
    protected void addPower(long microMj) {
        power = Math.min(power + microMj, getMaxPower());
    }

    @Override
    public long maxPowerReceived() {
        return 200 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 100 * MjAPI.MJ;
    }

    @Override
    public long getMaxPower() {
        return 1000 * MjAPI.MJ;
    }

    @Override
    public float explosionRange() {
        return 2;
    }

    @Override
    public long getCurrentOutput() {
        // PID output control
        long e = 3 * getMaxPower() / 8 - power;
        esum = clamp(esum + e, -eLimit, eLimit);
        return clamp(e + esum / 20, MIN_OUTPUT, MAX_OUTPUT);
    }

    @Override
    public long minPowerReceived() {
        return MjAPI.MJ / 10;
    }

    private static long clamp(long val, long min, long max) {
        return Math.max(min, Math.min(max, val));
    }

    // --- NBT ---

    /** Saved fuel stack, data components included (see {@link #writeData}). */
    private static final String FUEL_KEY = "fuel";
    /** Saved fuel count; also half of the pre-codec {@code fuelId} + {@code fuelCount} form. */
    private static final String FUEL_COUNT_KEY = "fuelCount";
    /** Item id of the pre-codec form, read back for worlds saved before the fuel went through the codec. */
    private static final String LEGACY_FUEL_ID_KEY = "fuelId";

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putInt("burnTime", burnTime);
        output.putInt("totalBurnTime", totalBurnTime);
        output.putLong("esum", esum);
        if (!fuelStack.isEmpty()) {
            // The full stack, data components included: a named or otherwise tagged fuel (and the container item its
            // stack-sensitive crafting remainder hands back) must come out of a reload as it went in. ItemStack.CODEC
            // rejects a count above the item's stack limit, which would delete the slot of an item whose limit was
            // lowered since the save, so the stack goes in at count 1 with the real count beside it.
            output.store(FUEL_KEY, ItemStack.CODEC, fuelStack.copyWithCount(1));
            output.putInt(FUEL_COUNT_KEY, fuelStack.getCount());
        }
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        burnTime = input.getIntOr("burnTime", 0);
        totalBurnTime = input.getIntOr("totalBurnTime", 0);
        esum = input.getLongOr("esum", 0L);
        fuelStack = readFuel(input);
    }

    private static ItemStack readFuel(BCValueInput input) {
        int count = input.getIntOr(FUEL_COUNT_KEY, 1);
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Optional<ItemStack> stack = input.read(FUEL_KEY, ItemStack.CODEC);
        if (stack.isPresent()) {
            return stack.get().isEmpty() ? ItemStack.EMPTY : stack.get().copyWithCount(count);
        }
        // Saved before the fuel went through the codec: item id and count only (components were already lost).
        Identifier id = Identifier.tryParse(input.getStringOr(LEGACY_FUEL_ID_KEY, ""));
        Item item = id == null ? null : RegistryUtilBC.getValue(BuiltInRegistries.ITEM, id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // --- IBCMenuProvider (GUI) ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.engine_stone");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerEngineStone(containerId, playerInventory, this);
    }
}
