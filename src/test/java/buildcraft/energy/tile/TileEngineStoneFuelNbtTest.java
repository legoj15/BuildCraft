/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The Stirling engine's fuel slot across a save. The fuel used to be persisted as {@code fuelId} + {@code fuelCount}
 * only, so a world reload stripped every data component off the fuel waiting in (or about to be burned from) the
 * slot: a named bucket came back bare, and the container item handed back when the fuel burns (its crafting
 * remainder, which can depend on the stack's components) was computed from the bare stack.
 */
public class TileEngineStoneFuelNbtTest extends VanillaSetupBaseTester {

    private static TileEngineStone_BC8 newEngine() {
        return new TileEngineStone_BC8(BlockPos.ZERO, BCEnergyBlocks.ENGINE_STONE.get().defaultBlockState());
    }

    private static CompoundTag save(TileEngineStone_BC8 engine) {
        return ValueIoTestUtil.write(engine::writeData);
    }

    private static TileEngineStone_BC8 load(CompoundTag tag) {
        TileEngineStone_BC8 engine = newEngine();
        engine.readData(ValueIoTestUtil.inputOf(tag));
        return engine;
    }

    private static ItemStack roundTrip(ItemStack fuel) {
        TileEngineStone_BC8 engine = newEngine();
        engine.setFuelStack(fuel);
        return load(save(engine)).getFuelStack();
    }

    private static void assertSameStack(ItemStack want, ItemStack got, String what) {
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(want, got),
                what + ": item and components must survive a save — wanted " + want + " "
                        + want.getComponentsPatch() + " but got " + got + " " + got.getComponentsPatch());
        Assertions.assertEquals(want.getCount(), got.getCount(), what + ": count");
    }

    @Test
    public void fuelKeepsItsDataComponents() {
        CompoundTag custom = new CompoundTag();
        custom.putString("origin", "test");
        ItemStack fuel = new ItemStack(Items.COAL, 5);
        fuel.set(DataComponents.CUSTOM_NAME, Component.literal("Stoker's coal"));
        fuel.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        Assertions.assertFalse(fuel.getComponentsPatch().isEmpty(),
                "precondition: the fuel must really carry components, or this test proves nothing");

        assertSameStack(fuel, roundTrip(fuel), "named coal");
    }

    @Test
    public void aBareFuelStaysBareWithItsCount() {
        ItemStack fuel = new ItemStack(Items.COAL, 17);

        ItemStack got = roundTrip(fuel);

        assertSameStack(fuel, got, "17 coal");
        Assertions.assertTrue(got.getComponentsPatch().isEmpty(), "a bare item must not gain components on the way");
    }

    @Test
    public void aCountAboveWhatTheItemCodecCarriesSurvives() {
        // ItemStack.CODEC bounds a count to 1..99, so a stack of more (an item whose stack limit a mod or datapack
        // raised) would fail to encode and the slot would save empty. The count is saved beside the stack instead.
        ItemStack fuel = new ItemStack(Items.COAL, 128);

        assertSameStack(fuel, roundTrip(fuel), "128 coal");
    }

    @Test
    public void anEmptySlotSavesNoFuelAndClearsOnLoad() {
        TileEngineStone_BC8 empty = newEngine();
        CompoundTag saved = save(empty);
        Assertions.assertFalse(saved.toString().contains("fuel"), "an empty slot writes no fuel entry: " + saved);

        TileEngineStone_BC8 stocked = newEngine();
        stocked.setFuelStack(new ItemStack(Items.COAL, 3));
        stocked.readData(ValueIoTestUtil.inputOf(saved));

        Assertions.assertTrue(stocked.getFuelStack().isEmpty(), "loading a save with no fuel empties the slot");
    }

    @Test
    public void aSaveInTheOldIdAndCountFormStillLoads() {
        // Worlds saved before the fix hold fuelId + fuelCount (components were already lost; item and count are not).
        CompoundTag legacy = ValueIoTestUtil.write(out -> {
            out.putInt("burnTime", 40);
            out.putString("fuelId", "minecraft:coal");
            out.putInt("fuelCount", 12);
        });

        TileEngineStone_BC8 engine = load(legacy);

        assertSameStack(new ItemStack(Items.COAL, 12), engine.getFuelStack(), "legacy coal");
        Assertions.assertEquals(40, engine.burnTime, "the rest of the save loads alongside");
    }

    @Test
    public void aLegacyFuelWithAnUnknownItemLoadsAsEmpty() {
        CompoundTag legacy = ValueIoTestUtil.write(out -> {
            out.putString("fuelId", "removedmod:fancy_fuel");
            out.putInt("fuelCount", 4);
        });

        Assertions.assertTrue(load(legacy).getFuelStack().isEmpty());
    }
}
