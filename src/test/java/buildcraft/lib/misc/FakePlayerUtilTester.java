/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.api.core.BuildCraftAPI;

/**
 * Game tests for BuildCraft's cached fake players ({@link BuildCraftAPI#fakePlayerProvider}).
 * <p>
 * The provider used to construct a brand-new {@code FakePlayer} on every call — including the per-block
 * protection check inside quarry / mining-well / builder scan loops. It now caches one player per
 * (level, profile), so these tests pin the three things that make the cache safe: the same instance comes
 * back, whatever the previous caller did to it is gone (measured against a genuinely fresh
 * {@code FakePlayer}), and the position overload still places the player at the block. BuildCraft's own callers
 * lease the player ({@link FakePlayerUtil#lease}); the lease tests pin that a nested fetch never resets a player
 * that is still in use, and that closing a lease drops the stacks the caller left on it.
 */
public class FakePlayerUtilTester {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static GameProfile uniqueProfile() {
        return new GameProfile(UUID.randomUUID(), "bc_fake_player_test");
    }

    /** Repeated fetches for the same (level, profile) return one instance — the whole point of the change. */
    public static void testReusesInstance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = uniqueProfile();

        FakePlayer generic = BuildCraftAPI.fakePlayerProvider.getBuildCraftPlayer(level);
        check(generic == BuildCraftAPI.fakePlayerProvider.getBuildCraftPlayer(level),
                "the generic BuildCraft player is cached");
        check("[BuildCraft]".equals(GameProfileUtil.getName(generic.getGameProfile())),
                "the generic player carries the [BuildCraft] identity");

        FakePlayer first = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
        check(first == BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner), "an owner's player is cached");
        check(first == BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner, helper.absolutePos(BlockPos.ZERO)),
                "the position overload shares the owner's cached player");
        // An owner profile re-read from NBT is a different GameProfile object (and on 26.x may differ in
        // properties); it must still resolve to the same player rather than fragmenting the cache.
        GameProfile reloaded = new GameProfile(GameProfileUtil.getId(owner), GameProfileUtil.getName(owner));
        check(first == BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, reloaded),
                "an equal id+name profile resolves to the same cached player");
        check(first != generic, "different profiles get different players");
        check(GameProfileUtil.getId(owner).equals(first.getUUID()), "the owner's player carries the owner's UUID");

        helper.succeed();
    }

    /**
     * Everything a previous caller did to the shared player — items in both hands, the inventory, armor, the
     * cursor stack, the selected slot, an item in use, a cooldown, rotations, ground/shift/sprint flags, fire,
     * position — must not survive into the next fetch. "Fresh" is measured against a real new FakePlayer.
     */
    public static void testResetsState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = uniqueProfile();

        FakePlayer player = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
        Vec3 home = player.position();
        // A fresh player's yaw is not a constant (LivingEntity's constructor rolls a small random one on every node),
        // so the rotations are compared to what this player was created with.
        float homeYRot = player.getYRot();
        float homeXRot = player.getXRot();
        float homeYHeadRot = player.getYHeadRot();
        float homeYBodyRot = player.yBodyRot;

        ItemStack apples = new ItemStack(Items.APPLE, 3);
        ItemStack shield = new ItemStack(Items.SHIELD);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 7);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        ItemStack carried = new ItemStack(Items.DIRT, 2);
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);

        player.setItemInHand(InteractionHand.MAIN_HAND, apples);
        player.setItemInHand(InteractionHand.OFF_HAND, shield);
        player.getInventory().setItem(9, cobble);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.inventoryMenu.setCarried(carried);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        check(player.isUsingItem(), "precondition: the apple is being eaten");
        //? if >=1.21.10 {
        player.getCooldowns().addCooldown(pearl, 200);
        check(player.getCooldowns().isOnCooldown(pearl), "precondition: the pearl is on cooldown");
        player.getInventory().setSelectedSlot(4);
        //?} else {
        /*player.getCooldowns().addCooldown(pearl.getItem(), 200);
        check(player.getCooldowns().isOnCooldown(pearl.getItem()), "precondition: the pearl is on cooldown");
        player.getInventory().selected = 4;*/
        //?}
        player.setYRot(180);
        player.setXRot(45);
        player.setYHeadRot(90);
        player.setYBodyRot(90);
        player.setOnGround(true);
        player.setShiftKeyDown(true);
        player.setSprinting(true);
        player.setRemainingFireTicks(100);
        player.setPos(home.x + 100, home.y + 20, home.z - 100);

        FakePlayer again = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
        check(again == player, "the same cached player comes back");

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            check(again.getItemBySlot(slot).isEmpty(), "equipment slot " + slot + " is empty");
        }
        for (int i = 0; i < again.getInventory().getContainerSize(); i++) {
            check(again.getInventory().getItem(i).isEmpty(), "inventory slot " + i + " is empty");
        }
        check(again.inventoryMenu.getCarried().isEmpty(), "the cursor stack is gone");
        check(again.containerMenu.getCarried().isEmpty(), "the open menu's cursor stack is gone");
        check(!again.isUsingItem(), "no item is in use");
        check(again.getUseItem().isEmpty(), "the in-use stack reference is dropped");
        //? if >=1.21.10 {
        check(!again.getCooldowns().isOnCooldown(pearl), "item cooldowns are cleared");
        check(again.getInventory().getSelectedSlot() == 0, "the selected hotbar slot is back to 0");
        //?} else {
        /*check(!again.getCooldowns().isOnCooldown(pearl.getItem()), "item cooldowns are cleared");
        check(again.getInventory().selected == 0, "the selected hotbar slot is back to 0");*/
        //?}

        // The caller's own stacks are untouched: the reset drops references, it never shrinks or empties them.
        check(apples.getCount() == 3 && cobble.getCount() == 7 && carried.getCount() == 2,
                "the reset leaves the previous caller's stacks intact");

        FakePlayer fresh = new FakePlayer(level, owner);
        check(again.getYRot() == homeYRot, "yRot is back to its creation value (" + again.getYRot() + ")");
        check(again.getXRot() == homeXRot, "xRot is back to its creation value (" + again.getXRot() + ")");
        check(again.getYHeadRot() == homeYHeadRot, "head rotation is back to its creation value");
        check(again.yBodyRot == homeYBodyRot, "body rotation is back to its creation value");
        check(again.yRotO == homeYRot && again.xRotO == homeXRot && again.yHeadRotO == homeYHeadRot
                && again.yBodyRotO == homeYBodyRot, "previous-tick rotations follow");
        check(again.onGround() == fresh.onGround(), "onGround matches a fresh player");
        check(again.isShiftKeyDown() == fresh.isShiftKeyDown(), "shift matches a fresh player");
        check(again.isSprinting() == fresh.isSprinting(), "sprint matches a fresh player");
        check(again.getRemainingFireTicks() == fresh.getRemainingFireTicks(), "fire matches a fresh player");
        check(again.getDeltaMovement().equals(fresh.getDeltaMovement()), "motion matches a fresh player");
        check(again.position().equals(home), "the no-position fetch puts the player back where it was created");

        helper.succeed();
    }

    /** The position overload centres the player on the given block every time, and the plain overload undoes it. */
    public static void testPositionsPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = uniqueProfile();

        Vec3 home = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner).position();

        BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
        FakePlayer atA = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner, a);
        check(atA.position().equals(new Vec3(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5)),
                "the player is centred on the first block, was " + atA.position());
        check(atA.xo == atA.getX() && atA.yo == atA.getY() && atA.zo == atA.getZ(),
                "the previous-tick position follows, so nothing interpolates from a stale spot");

        BlockPos b = helper.absolutePos(new BlockPos(4, 1, 5));
        FakePlayer atB = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner, b);
        check(atB == atA, "the same cached player is re-positioned");
        check(atB.position().equals(new Vec3(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)),
                "the player is centred on the second block, was " + atB.position());

        FakePlayer back = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
        check(back.position().equals(home), "the plain overload restores the creation position, was " + back.position());

        helper.succeed();
    }

    /**
     * While a lease is open its player is reserved: a nested lease or API fetch for the same (level, profile) gets a
     * separate player instead of resetting the outer caller's hand and rotation under it. Closing the lease scrubs
     * what the caller stashed and hands the cached player out again.
     */
    public static void testLeaseIsolatesNestedFetches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = uniqueProfile();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);

        FakePlayer cached;
        try (FakePlayerUtil.Lease outer = FakePlayerUtil.lease(level, owner, helper.absolutePos(BlockPos.ZERO))) {
            cached = outer.player();
            cached.setItemInHand(InteractionHand.MAIN_HAND, tool);
            cached.setYRot(90);

            try (FakePlayerUtil.Lease inner = FakePlayerUtil.lease(level, owner, helper.absolutePos(new BlockPos(2, 1, 2)))) {
                check(inner.player() != cached, "a nested lease gets a separate player");
                check(GameProfileUtil.getId(owner).equals(inner.player().getUUID()),
                        "the separate player still acts as the owner");
                check(inner.player().getMainHandItem().isEmpty(), "the separate player starts empty-handed");
            }
            FakePlayer viaApi = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
            check(viaApi != cached, "an API fetch during a lease does not hand out the leased player");

            check(cached.getMainHandItem() == tool, "the outer caller's tool survives the nested fetches");
            check(cached.getYRot() == 90, "the outer caller's rotation survives the nested fetches");
        }
        check(cached.getMainHandItem().isEmpty(), "closing the lease takes the stashed tool back out");
        check(tool.getCount() == 1, "closing the lease leaves the tool itself intact");

        try (FakePlayerUtil.Lease again = FakePlayerUtil.lease(level, owner, null)) {
            check(again.player() == cached, "after release, the cached player is leased again");
        }
        check(BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner) == cached,
                "after release, API fetches get the cached player again");

        helper.succeed();
    }

    /** A level unload forgets that level's players, so the cache never keeps a level alive. */
    public static void testUnloadDropsLevelPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile owner = uniqueProfile();

        FakePlayer before = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
        // Posted on the game bus (as the server does on unload), so the listener's registration is covered too.
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        FakePlayer after = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner);
        check(after != before, "the unloaded level's player is gone");
        check(after == BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner), "the replacement is cached again");

        helper.succeed();
    }
}
