/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;

import buildcraft.VanillaSetupBaseTester;

/** The Ph5 world-property predicates that can be driven WITHOUT a live server, through the state-only
 *  seams. This sweep covers the two properties whose whole decision path is tag-free:
 *  {@code fluidSource} (a FluidState check) and {@code harvestable} (BC's own
 *  {@code CropHandlerPlantable.isMature} — instanceof checks and each crop block's own AGE property, no
 *  vanilla tags).
 *
 *  <p>The other four — wood, dirt, replaceable, and the ore tier ladder — are pinned by the
 *  {@code world_properties_match} GAME test instead, on purpose: the FML-JUnit unit-test JVM performs no
 *  server resource load (the same fact that forces {@link VanillaSetupBaseTester} to bind item components
 *  by hand), so vanilla data tags never load and {@code state.is(tag)} is false for every tag on every
 *  node. That is an environment limit, not a property bug — the 26.2 vanilla tag data was verified to
 *  contain exactly what these properties expect (logs = logs_that_burn + stems, dirt = the three dirt
 *  blocks, replaceable = air/water/lava/short grass/…, the eight {@code *_ores} umbrellas), and the ore
 *  property's pickaxe ladder is tag-based TOO: 26.x's {@code Tool} component decides
 *  {@code isCorrectForDrops} by matching its rules' tag HolderSets, so even the ladder needs a live server. */
public class WorldPropertySweepTest extends VanillaSetupBaseTester {

    @Test
    public void harvestableMatchesMatureCrops() {
        WorldPropertyIsHarvestable harvestable = new WorldPropertyIsHarvestable();

        Assertions.assertTrue(harvestable.matches(
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)),
                "a fully grown wheat is harvestable");
        Assertions.assertFalse(harvestable.matches(
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3)),
                "young wheat is not harvestable");
        Assertions.assertTrue(harvestable.matches(
                Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3)),
                "fully grown nether wart is harvestable");
        Assertions.assertFalse(harvestable.matches(
                Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 1)),
                "young nether wart is not harvestable");
        Assertions.assertFalse(harvestable.matches(Blocks.COBBLESTONE.defaultBlockState()),
                "cobblestone is not a crop");
    }

    @Test
    public void fluidSourceMatchesSourceFluids() {
        WorldPropertyIsFluidSource fluidSource = new WorldPropertyIsFluidSource();

        Assertions.assertTrue(fluidSource.matches(Blocks.WATER.defaultBlockState()),
                "standing water is a source");
        Assertions.assertTrue(fluidSource.matches(Blocks.LAVA.defaultBlockState()),
                "standing lava is a source");
        Assertions.assertFalse(fluidSource.matches(Blocks.AIR.defaultBlockState()), "air is not a fluid");
        Assertions.assertFalse(fluidSource.matches(Blocks.STONE.defaultBlockState()), "stone is not a fluid");
    }
}
