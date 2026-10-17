/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.container;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * The wire format of the JEI "+" transfer into the Assembly Table. Its payload used to carry each needed
 * stack as {@code {id, count}} only, so a server that compares the player's stacks on item AND components
 * (as it must) never found a component-carrying ingredient — an Iron AND Gate, a named or enchanted tool —
 * and the "+" button silently did nothing for any such recipe. These tests pin that every stack survives
 * the round trip with its components intact.
 */
public class AssemblyTableTransferTest extends VanillaSetupBaseTester {

    /** Static registries only: enough for every component used here, no server or level needed. */
    private static RegistryAccess registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static AssemblyTableTransfer.Request roundTrip(boolean max, List<ItemStack> stacks) {
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        try {
            AssemblyTableTransfer.write(buf, registries(), max, stacks);
            return AssemblyTableTransfer.read(buf, registries());
        } finally {
            buf.release();
        }
    }

    private static ItemStack ironAndGate(int count) {
        return BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER))
                .copyWithCount(count);
    }

    private static void assertSameStack(ItemStack want, ItemStack got, String what) {
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(want, got),
                what + ": item and components must survive the round trip — wanted " + want + " "
                        + want.getComponentsPatch() + " but got " + got + " " + got.getComponentsPatch());
        Assertions.assertEquals(want.getCount(), got.getCount(), what + ": count");
    }

    @Test
    public void aBuildCraftComponentSurvivesTheRoundTrip() {
        ItemStack gate = ironAndGate(2);
        Assertions.assertFalse(gate.getComponentsPatch().isEmpty(),
                "precondition: the gate must really carry a component, or this test proves nothing");

        AssemblyTableTransfer.Request got = roundTrip(false, List.of(gate));

        Assertions.assertEquals(1, got.stacks().size());
        assertSameStack(gate, got.stacks().get(0), "iron AND gate");
    }

    @Test
    public void vanillaComponentsSurviveTheRoundTrip() {
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Programmed"));
        named.set(DataComponents.DAMAGE, 17);
        CompoundTag blob = new CompoundTag();
        blob.putString("buildcraft_test", "marker");
        ItemStack tagged = new ItemStack(Items.PAPER, 5);
        tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(blob));

        AssemblyTableTransfer.Request got = roundTrip(false, List.of(named, tagged));

        Assertions.assertEquals(2, got.stacks().size());
        assertSameStack(named, got.stacks().get(0), "named pickaxe");
        assertSameStack(tagged, got.stacks().get(1), "custom-data paper");
    }

    @Test
    public void aBareStackStaysBareAndKeepsItsCount() {
        ItemStack redstone = new ItemStack(Items.REDSTONE, 48);

        AssemblyTableTransfer.Request got = roundTrip(false, List.of(redstone));

        Assertions.assertEquals(1, got.stacks().size());
        assertSameStack(redstone, got.stacks().get(0), "plain redstone");
        Assertions.assertTrue(got.stacks().get(0).getComponentsPatch().isEmpty(),
                "a bare ingredient must not gain components on the way");
    }

    @Test
    public void aComponentCarrierDoesNotMatchItsBareTwin() {
        // The server matches on item AND components; the decoded stack must therefore be distinguishable
        // from a plain one, or "+" would drain plain stacks the recipe never asked for.
        ItemStack gate = ironAndGate(1);
        AssemblyTableTransfer.Request got = roundTrip(false, List.of(gate));

        Assertions.assertFalse(ItemStack.isSameItemSameComponents(got.stacks().get(0),
                new ItemStack(BCSiliconItems.PLUG_GATE.get())),
                "the decoded gate must not equal a default (component-less) gate");
    }

    @Test
    public void theMaxTransferFlagRoundTripsBothWays() {
        Assertions.assertTrue(roundTrip(true, List.of(new ItemStack(Items.STICK))).maxTransfer());
        Assertions.assertFalse(roundTrip(false, List.of(new ItemStack(Items.STICK))).maxTransfer());
    }

    @Test
    public void anEmptyRequestIsEmpty() {
        Assertions.assertTrue(roundTrip(false, List.of()).stacks().isEmpty());
    }

    @Test
    public void aRequestNamingMoreStacksThanTheTableHasSlotsIsDropped() {
        List<ItemStack> tooMany = new ArrayList<>();
        for (int i = 0; i <= AssemblyTableTransfer.MAX_STACKS; i++) {
            tooMany.add(new ItemStack(Items.STICK, i + 1));
        }

        AssemblyTableTransfer.Request got = roundTrip(true, tooMany);

        Assertions.assertTrue(got.stacks().isEmpty(),
                "an oversized request cannot be a real recipe and must be dropped whole, not truncated");

        List<ItemStack> exactlyFull = tooMany.subList(0, AssemblyTableTransfer.MAX_STACKS);
        Assertions.assertEquals(AssemblyTableTransfer.MAX_STACKS, roundTrip(false, exactlyFull).stacks().size(),
                "a request naming exactly one stack per table slot is still honoured");
    }
}
