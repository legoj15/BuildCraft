/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import buildcraft.lib.misc.GameProfileUtil;

import buildcraft.builders.BCBuildersBlocks;

/**
 * Pins the owner-on-placement contract for {@link TileQuarry}. The quarry overrides
 * {@code onPlacedBy} to do marker/IAreaProvider resolution, and must still chain to
 * {@code super.onPlacedBy} so {@code TileBC_Neptune} records the placer. Forgetting
 * the {@code super} call silently breaks both quarry advancements — Diggy Diggy Hole
 * gates on {@code getOwner() != null}, and Destroying the World pairs by owner UUID
 * — and neither failure logs anything: the trigger just never fires.
 */
public class TileQuarryOwnerTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void onPlacedByRecordsOwner(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(2, 2, 2);
            helper.setBlock(local, BCBuildersBlocks.QUARRY.get());
            //? if >=1.21.10 {
            TileQuarry quarry = helper.getBlockEntity(local, TileQuarry.class);
            //?} else {
            /*TileQuarry quarry = helper.getBlockEntity(local);*/
            //?}
            assertTrue(quarry != null, "quarry block-entity must be present at " + local);
            assertTrue(quarry.getOwner() == null,
                    "owner must be null before onPlacedBy runs");

            Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
            GameProfile expected = placer.getGameProfile();
            quarry.onPlacedBy(placer, ItemStack.EMPTY);

            GameProfile actual = quarry.getOwner();
            assertTrue(actual != null,
                    "TileQuarry.onPlacedBy must chain to super.onPlacedBy so the placer "
                            + "is recorded — without this, getOwner() stays null and the "
                            + "Diggy Diggy Hole + Destroying the World advancements never fire");
            assertTrue(GameProfileUtil.getId(expected).equals(GameProfileUtil.getId(actual)),
                    "recorded owner UUID must match the placer (expected "
                            + GameProfileUtil.getId(expected) + ", got " + GameProfileUtil.getId(actual) + ")");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
