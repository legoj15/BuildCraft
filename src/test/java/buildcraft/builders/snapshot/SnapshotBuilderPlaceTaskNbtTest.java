/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import com.mojang.serialization.DynamicOps;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;

/**
 * The saved form of a Builder/Filler's in-flight place tasks. Each task holds the items already taken out of
 * the builder's inventory; they are what gets placed (Template/Filler) or refunded when the task is cancelled
 * (Blueprint). The saved form used to keep only {@code {id, count}}, so a world saved mid-build lost every
 * component on those items: an Iron AND Gate came back as a bare gate plug, and a Blueprint's fluid task (a
 * filled bucket tagged with the fluid it stands for) came back as a plain bucket the cancel path then
 * refunded into the inventory instead of returning the fluid to the tank.
 */
public class SnapshotBuilderPlaceTaskNbtTest extends VanillaSetupBaseTester {

    private static final BlockPos POS = new BlockPos(3, 70, -9);

    /** Never touches its tile while (de)serialising a place task, so a null one is enough. */
    private static final TemplateBuilder BUILDER = new TemplateBuilder(null);

    private static DynamicOps<Tag> ops() {
        return RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static SnapshotBuilder<?>.PlaceTask roundTrip(List<ItemStack> items) {
        SnapshotBuilder<?>.PlaceTask task = BUILDER.new PlaceTask(POS, items, 1234L);
        return BUILDER.new PlaceTask(task.writeToNBT(ops()), ops());
    }

    private static ItemStack ironAndGate(int count) {
        return BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER))
                .copyWithCount(count);
    }

    private static void assertSameStack(ItemStack want, ItemStack got, String what) {
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(want, got),
                what + ": item and components must survive a save — wanted " + want + " "
                        + want.getComponentsPatch() + " but got " + got + " " + got.getComponentsPatch());
        Assertions.assertEquals(want.getCount(), got.getCount(), what + ": count");
    }

    @Test
    public void aGatePlugKeepsItsVariant() {
        ItemStack gate = ironAndGate(1);
        Assertions.assertFalse(gate.getComponentsPatch().isEmpty(),
                "precondition: the gate must really carry a component, or this test proves nothing");

        SnapshotBuilder<?>.PlaceTask got = roundTrip(List.of(gate));

        Assertions.assertEquals(1, got.items.size());
        assertSameStack(gate, got.items.get(0), "iron AND gate");
        Assertions.assertEquals(POS, got.pos);
        Assertions.assertEquals(1234L, got.power);
    }

    @Test
    public void aBlueprintFluidMarkerSurvives() {
        // Shape of BlueprintBuilder's fluid task item: a filled bucket whose CUSTOM_DATA names the fluid.
        CompoundTag fluid = new CompoundTag();
        fluid.putString("fluid", "minecraft:water");
        fluid.putInt("amount", 1000);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("BuilderFluidStack", fluid);
        ItemStack marker = new ItemStack(Items.WATER_BUCKET);
        marker.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));
        ItemStack named = new ItemStack(Items.CHEST);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Loot"));

        SnapshotBuilder<?>.PlaceTask got = roundTrip(List.of(named, marker));

        Assertions.assertEquals(2, got.items.size());
        assertSameStack(named, got.items.get(0), "named chest");
        assertSameStack(marker, got.items.get(1), "fluid marker bucket");
    }

    @Test
    public void aCountAboveTheStackLimitSurvives() {
        // Blueprint required items are merged per item, so one entry can exceed a stack (ItemStack.CODEC
        // itself caps the count it accepts).
        ItemStack lots = new ItemStack(Items.COBBLESTONE, 200);

        SnapshotBuilder<?>.PlaceTask got = roundTrip(List.of(lots));

        Assertions.assertEquals(1, got.items.size());
        assertSameStack(lots, got.items.get(0), "200 cobblestone");
        Assertions.assertTrue(got.items.get(0).getComponentsPatch().isEmpty(),
                "a bare item must not gain components on the way");
    }

    @Test
    public void theClientSyncPayloadKeepsComponentsAndCounts() {
        // The network sync shares the per-item encoding with the save, so pin it too (the flying-item
        // render on the client reads these stacks).
        ItemStack gate = ironAndGate(1);
        ItemStack lots = new ItemStack(Items.COBBLESTONE, 130);
        SnapshotBuilder<?>.PlaceTask task = BUILDER.new PlaceTask(POS, List.of(gate, lots), 99L);
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        try {
            task.writePayload(buf);
            SnapshotBuilder<?>.PlaceTask got = BUILDER.new PlaceTask(buf);

            Assertions.assertEquals(2, got.items.size());
            assertSameStack(gate, got.items.get(0), "synced iron AND gate");
            assertSameStack(lots, got.items.get(1), "synced 130 cobblestone");
            Assertions.assertEquals(POS, got.pos);
            Assertions.assertEquals(99L, got.power);
        } finally {
            buf.release();
        }
    }

    @Test
    public void aTaskSavedInTheOldIdAndCountFormStillLoads() {
        // Worlds saved before the fix hold {id, count} entries; they must keep loading (components were
        // already lost, but the item and count are not).
        CompoundTag legacyItem = new CompoundTag();
        legacyItem.putString("id", "minecraft:stone");
        legacyItem.putInt("count", 3);
        ListTag list = new ListTag();
        list.add(legacyItem);
        CompoundTag nbt = new CompoundTag();
        nbt.putLong("pos_x", POS.getX());
        nbt.putLong("pos_y", POS.getY());
        nbt.putLong("pos_z", POS.getZ());
        nbt.put("items", list);
        nbt.putLong("power", 7L);

        SnapshotBuilder<?>.PlaceTask got = BUILDER.new PlaceTask(nbt, ops());

        Assertions.assertEquals(1, got.items.size());
        assertSameStack(new ItemStack(Items.STONE, 3), got.items.get(0), "legacy stone");
        Assertions.assertEquals(7L, got.power);
    }
}
