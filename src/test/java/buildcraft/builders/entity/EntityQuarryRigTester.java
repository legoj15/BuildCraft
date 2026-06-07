/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.entity;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.builders.BCBuildersEntities;

/**
 * Guards that the quarry's collision rig entity is fire-immune. It is an invisible structural entity
 * for the drill arms; if it caught fire in lava, the entity render dispatcher would draw the fire
 * overlay sprite across the (invisible) entity — i.e. floating fire through the middle of the drill
 * arm. Fire-immunity skips lava ignition entirely, so {@code isOnFire()} (which gates the overlay)
 * stays false even if something tries to set it alight.
 */
public class EntityQuarryRigTester {

    public static void testRigIsFireImmune(GameTestHelper helper) {
        EntityQuarryRig rig = new EntityQuarryRig(BCBuildersEntities.QUARRY_RIG.get(), helper.getLevel());

        helper.assertTrue(rig.fireImmune(),
                "Quarry rig collision entity must be fire-immune (no fire overlay over the drill arm in lava)");

        // Even forced alight, a fire-immune entity reports not-on-fire, so the fire overlay never draws.
        rig.setRemainingFireTicks(100);
        helper.assertFalse(rig.isOnFire(),
                "Fire-immune quarry rig must report not on fire even after setRemainingFireTicks");

        helper.succeed();
    }
}
