/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.crops;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.crops.CropManager;

/** The seed half of sugar-cane planting: {@link CropHandlerReeds} claims the cane item (7.1.x's
 *  {@code stack.getItem() == Items.reeds}) and nothing else, and the mod boot registers it with
 *  {@link CropManager} so the Planter robot's and the Stripes pipe's shared seed predicate
 *  ({@link CropManager#isSeed}) sees cane. The soil half needs a real level and lives in
 *  {@code CropHandlerReedsTester}. */
public class CropHandlerReedsTest extends VanillaSetupBaseTester {

    @Test
    public void sugarCaneIsTheReedsHandlersSeed() {
        Assertions.assertTrue(CropHandlerReeds.INSTANCE.isSeed(new ItemStack(Items.SUGAR_CANE)),
                "the cane item is what the reeds handler plants");
        Assertions.assertFalse(CropHandlerReeds.INSTANCE.isSeed(new ItemStack(Items.WHEAT_SEEDS)),
                "wheat seeds are the default handler's, not the reeds handler's");
        Assertions.assertFalse(CropHandlerReeds.INSTANCE.isSeed(ItemStack.EMPTY),
                "an empty stack is nobody's seed");
    }

    @Test
    public void theReedsHandlerIsRegisteredSoCropManagerSeesCane() {
        Assertions.assertTrue(CropManager.isSeed(new ItemStack(Items.SUGAR_CANE)),
                "CropManager must recognise cane — the reeds handler has to be registered at boot, "
                        + "or the Planter robot never fetches it");
        Assertions.assertTrue(CropManager.isSeed(new ItemStack(Items.WHEAT_SEEDS)),
                "registering the reeds handler must not hide the default handler's seeds");
    }

    @Test
    public void theReedsHandlerNeverClaimsAHarvest() {
        // Mature cane is the default handler's stacked-on-its-own-kind rule; the reeds handler only plants
        // (7.1.x's isMature returned false), so it must not shadow that rule in CropManager.harvestCrop.
        Assertions.assertFalse(CropHandlerReeds.INSTANCE.isMature(null,
                net.minecraft.world.level.block.Blocks.SUGAR_CANE.defaultBlockState(), null),
                "the reeds handler is plant-only");
    }
}
