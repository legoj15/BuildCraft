/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * A machine break is done BY the machine's owner (its fake player, see {@link MachineIdentity}), not by nobody:
 * {@link BlockUtil#breakBlockAndGetDropsWithXp} took the owner and ignored it, so the break's block-destroy game event
 * (what sculk sensors and claim/log mods hear), and the loot and XP context, carried no breaker at all. The observer
 * here is that game event's cause.
 */
public class MachineBreakAttributionTester {

    /** Records the cause of every block-destroy game event at {@code pos} while it is registered. */
    public static final class BreakerObserver implements AutoCloseable {
        public final List<Entity> causes = new ArrayList<>();
        private final Consumer<VanillaGameEvent> listener;

        public BreakerObserver(BlockPos pos) {
            listener = event -> {
                if (event.getVanillaEvent().is(GameEvent.BLOCK_DESTROY.key())
                        && BlockPos.containing(event.getEventPosition()).equals(pos)) {
                    causes.add(event.getCause());
                }
            };
            NeoForge.EVENT_BUS.addListener(VanillaGameEvent.class, listener);
        }

        /** Throws unless exactly one destroy was heard and its cause is {@code expected}'s fake player. */
        public void requireBrokenBy(GameTestHelper helper, GameProfile expected, String what) {
            helper.assertTrue(causes.size() == 1, what + ": one block-destroy game event, heard " + causes.size());
            Entity cause = causes.get(0);
            helper.assertTrue(cause instanceof FakePlayer, what + ": broken by a fake player, got " + cause);
            helper.assertTrue(GameProfileUtil.getId(expected).equals(cause.getUUID()),
                    what + ": broken as " + GameProfileUtil.getName(expected) + ", got " + cause.getName().getString());
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    public static void machineBreakIsDoneByTheOwner(GameTestHelper helper) {
        GameProfile owner = new GameProfile(
                UUID.nameUUIDFromBytes("bc_break_owner".getBytes(StandardCharsets.UTF_8)), "bc_break_owner");
        BlockPos ownedRel = new BlockPos(1, 1, 1);
        BlockPos unownedRel = new BlockPos(3, 1, 1);
        helper.setBlock(ownedRel, Blocks.STONE);
        helper.setBlock(unownedRel, Blocks.STONE);
        BlockPos owned = helper.absolutePos(ownedRel);
        BlockPos unowned = helper.absolutePos(unownedRel);

        try (BreakerObserver observer = new BreakerObserver(owned)) {
            helper.assertTrue(BlockUtil.breakBlockAndGetDropsWithXp(helper.getLevel(), owned,
                    new ItemStack(Items.DIAMOND_PICKAXE), owner).isPresent(), "precondition: the stone breaks");
            helper.assertTrue(helper.getLevel().getBlockState(owned).isAir(), "precondition: the stone is gone");
            observer.requireBrokenBy(helper, owner, "an owned machine's break");
        }
        try (BreakerObserver observer = new BreakerObserver(unowned)) {
            helper.assertTrue(BlockUtil.breakBlockAndGetDropsWithXp(helper.getLevel(), unowned,
                    new ItemStack(Items.DIAMOND_PICKAXE), (GameProfile) null).isPresent(), "precondition: the stone breaks");
            observer.requireBrokenBy(helper, FakePlayerUtil.BUILDCRAFT_PROFILE, "an unowned machine's break");
        }
        helper.succeed();
    }
}
