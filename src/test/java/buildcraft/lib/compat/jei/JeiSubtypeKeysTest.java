/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.jei;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.core.BCCoreItems;
import buildcraft.core.compat.jei.CoreJeiSubtypes;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.compat.jei.SiliconJeiSubtypes;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * Pins the JEI subtype keys of the lens, gate, facade and fragile fluid shard — the identity JEI uses to tell two
 * stacks of one item apart (equal keys merge into one entry, different keys get their own entry and recipe lookup).
 *
 * <p>Two contracts, both checked on every node:
 * <ul>
 * <li><b>Identity</b>: two independently built stacks of the same variant give EQUAL keys, and different variants
 * give different keys — otherwise JEI either lists duplicates or merges variants into one entry.</li>
 * <li><b>Legacy text</b>: each key's {@code toString()} is the exact string the pre-2026-09-26 interpreters returned.
 * On 1.21.1, {@code JeiSubtypes} hands JEI 19.x that string as the legacy subtype info (what an old bookmark file is
 * matched against); a changed string silently orphans those bookmarks. JEI 19.27's current bookmark file stores the
 * full stack (its BookmarkCodec goes through the typed-ingredient codec; checked with javap on the pinned jar), so it
 * survives regardless — this pins the older import path.</li>
 * </ul>
 * The redstone board and robot keys are pinned separately ({@code ProgrammingRecipeCollectorTester}).
 */
public class JeiSubtypeKeysTest extends VanillaSetupBaseTester {

    // ── Lens / filter ───────────────────────────────────────────────────────

    @Test
    public void lensKeyIsColourAndMode() {
        var lens = BCSiliconItems.PLUG_LENS.get();
        Assertions.assertEquals("red:false", SiliconJeiSubtypes.lensKey(lens.getStack(DyeColor.RED, false)));
        Assertions.assertEquals("red:true", SiliconJeiSubtypes.lensKey(lens.getStack(DyeColor.RED, true)));
        Assertions.assertEquals("clear:false", SiliconJeiSubtypes.lensKey(lens.getStack(null, false)));
        Assertions.assertEquals("clear:true", SiliconJeiSubtypes.lensKey(lens.getStack(null, true)));
        Assertions.assertEquals("light_blue:false",
            SiliconJeiSubtypes.lensKey(lens.getStack(DyeColor.LIGHT_BLUE, false)),
            "multi-word colours key on the dye's serialized name");
    }

    // ── Gate ────────────────────────────────────────────────────────────────

    @Test
    public void gateKeyIsTheVariantName() {
        var gate = BCSiliconItems.PLUG_GATE.get();
        Assertions.assertEquals("clay_brick", SiliconJeiSubtypes.gateKey(gate.getStack(
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER))),
            "the basic gate cannot be modified, so its key is the material alone");
        Assertions.assertEquals("iron_and_no_modifier", SiliconJeiSubtypes.gateKey(gate.getStack(
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER))));
        Assertions.assertEquals("iron_or_diamond", SiliconJeiSubtypes.gateKey(gate.getStack(
            new GateVariant(EnumGateLogic.OR, EnumGateMaterial.IRON, EnumGateModifier.DIAMOND))));
    }

    // ── Facade ──────────────────────────────────────────────────────────────

    private static ItemStack facade(net.minecraft.world.level.block.Block block, boolean hollow) {
        FacadeStateManager.ensureInitialized();
        FacadeBlockStateInfo info = FacadeStateManager.getInfoForBlock(block);
        Assertions.assertNotNull(info, block + " must be a valid facade state");
        return BCSiliconItems.PLUG_FACADE.get().createItemStack(FacadeInstance.createSingle(info, hollow));
    }

    @Test
    public void facadeKeyIsStructuralAndSeparatesVariants() {
        Object stoneA = SiliconJeiSubtypes.facadeKey(facade(Blocks.STONE, false));
        Object stoneB = SiliconJeiSubtypes.facadeKey(facade(Blocks.STONE, false));
        Object obsidian = SiliconJeiSubtypes.facadeKey(facade(Blocks.OBSIDIAN, false));
        Object hollowStone = SiliconJeiSubtypes.facadeKey(facade(Blocks.STONE, true));

        Assertions.assertEquals(stoneA, stoneB, "two stone facades must share one JEI entry");
        Assertions.assertEquals(stoneA.hashCode(), stoneB.hashCode());
        Assertions.assertEquals(stoneA.toString(), stoneB.toString(), "the legacy text must be stable too");
        Assertions.assertNotEquals(stoneA, obsidian, "different blocks must be different JEI entries");
        Assertions.assertNotEquals(stoneA, hollowStone, "hollow and solid must be different JEI entries");
    }

    @Test
    public void facadeKeyIsTheWrittenFacadeCompound() {
        // The legacy text is the "facade" compound's SNBT — the exact tag the item stores — so an old bookmark's
        // string equals the key of a freshly made facade of the same block.
        FacadeStateManager.ensureInitialized();
        FacadeInstance instance = FacadeInstance.createSingle(FacadeStateManager.getInfoForBlock(Blocks.STONE), false);
        ItemStack stack = BCSiliconItems.PLUG_FACADE.get().createItemStack(instance);
        Assertions.assertEquals(instance.writeToNbt(), SiliconJeiSubtypes.facadeKey(stack));
    }

    // ── Fragile fluid shard ─────────────────────────────────────────────────

    private static ItemStack shard(FluidStack fluid) {
        ItemStack stack = new ItemStack(BCCoreItems.FRAGILE_FLUID_CONTAINER.get());
        ItemFragileFluidContainer.setFluid(stack, fluid);
        return stack;
    }

    @Test
    public void fragileShardKeysOnFluidIdOnly() {
        Object full = CoreJeiSubtypes.fragileFluidKey(shard(new FluidStack(Fluids.WATER, 1000)));
        Object half = CoreJeiSubtypes.fragileFluidKey(shard(new FluidStack(Fluids.WATER, 500)));
        Assertions.assertEquals(full, half, "a shard's amount must not split it into a new JEI entry");
        Assertions.assertEquals("minecraft:water", full.toString());
        Assertions.assertNotEquals(full, CoreJeiSubtypes.fragileFluidKey(shard(new FluidStack(Fluids.LAVA, 1000))));
        Assertions.assertNull(CoreJeiSubtypes.fragileFluidKey(shard(FluidStack.EMPTY)),
            "an empty shard has no subtype (JeiSubtypes maps null to JEI's 'none' on every node)");
    }
}
