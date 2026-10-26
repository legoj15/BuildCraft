/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockDistiller;
import buildcraft.transport.BCTransportBlocks;

/**
 * The wrenches as tools (issue #34): what they mine, what wears them, and the zombies that carry or drop one.
 * <p>
 * The rest of the wear rules are asserted where the action already is: a wrench turning a vanilla block
 * ({@code VanillaRotationTester}), a pipe ({@code PipeFilterPersistenceTester#daizuliEditsMarkChunkDirty}) and a robot
 * ({@code RobotWearablesTester}) wears it; removing a volume box ({@code FillerOwnedVolumeBoxTester}) does not.
 */
public class WrenchToolTester {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * A wrench is the right tool for BuildCraft's machines and pipes, at the speed of its tier (iron 6.0 for the
     * Basic and Marker Wrench, diamond 8.0 for the Volume Wrench), and for nothing else: stone takes hand speed and
     * is no drop. Each also repairs with its own tier's material only, which is what tells the tiers apart here.
     */
    public static void miningRules(GameTestHelper helper) {
        BlockState machine = BCCoreBlocks.ENGINE_REDSTONE.get().defaultBlockState();
        BlockState pipe = BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        checkTool("Basic Wrench", new ItemStack(BCCoreItems.WRENCH.get()), 6.0F, Items.IRON_INGOT, Items.DIAMOND, machine, pipe, stone);
        checkTool("Marker Wrench", new ItemStack(BCCoreItems.MARKER_WRENCH.get()), 6.0F, Items.IRON_INGOT, Items.DIAMOND, machine, pipe, stone);
        checkTool("Volume Wrench", new ItemStack(BCCoreItems.VOLUME_WRENCH.get()), 8.0F, Items.DIAMOND, Items.IRON_INGOT, machine, pipe, stone);
        helper.succeed();
    }

    private static void checkTool(String name, ItemStack wrench, float speed, net.minecraft.world.item.Item repairsWith,
            net.minecraft.world.item.Item doesNotRepairWith, BlockState machine, BlockState pipe, BlockState stone) {
        check(wrench.isDamageableItem(), name + " has durability");
        check(wrench.isEnchantable(), name + " can be enchanted");
        check(wrench.getDestroySpeed(machine) == speed, name + " mines a machine at " + speed + ", got " + wrench.getDestroySpeed(machine));
        check(wrench.getDestroySpeed(pipe) == speed, name + " mines a pipe at " + speed + ", got " + wrench.getDestroySpeed(pipe));
        check(wrench.isCorrectToolForDrops(machine), name + " is the right tool for a machine's drops");
        check(wrench.isCorrectToolForDrops(pipe), name + " is the right tool for a pipe's drops");
        check(wrench.getDestroySpeed(stone) == 1.0F, name + " mines stone at hand speed, got " + wrench.getDestroySpeed(stone));
        check(!wrench.isCorrectToolForDrops(stone), name + " is not a tool for stone");
        check(repairs(wrench, new ItemStack(repairsWith)), name + " repairs with " + repairsWith);
        check(!repairs(wrench, new ItemStack(doesNotRepairWith)), name + " does not repair with " + doesNotRepairWith);
    }

    private static boolean repairs(ItemStack tool, ItemStack material) {
        //? if >=1.21.10 {
        return tool.isValidRepairItem(material);
        //?} else {
        /*return tool.getItem().isValidRepairItem(tool, material);*/
        //?}
    }

    /**
     * Every wrench that turns a BuildCraft machine loses exactly one use per turn, and a click that turns nothing
     * costs nothing. The Marker and Volume Wrench inherit the same rule.
     */
    public static void wearFollowsWrenchActions(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(2, 2, 2);
        helper.setBlock(machinePos, BCFactoryBlocks.DISTILLER.get().defaultBlockState().setValue(BlockDistiller.FACING, Direction.NORTH));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        for (var item : List.of(BCCoreItems.WRENCH.get(), BCCoreItems.MARKER_WRENCH.get(), BCCoreItems.VOLUME_WRENCH.get())) {
            ItemStack wrench = new ItemStack(item);
            check(wrench.getDamageValue() == 0, "a new wrench is undamaged");
            check(useOn(helper, item, wrench, player, machinePos) == InteractionResult.SUCCESS, "the wrench turns the distiller");
            check(wrench.getDamageValue() == 1, item + " loses one use for a turn, has lost " + wrench.getDamageValue());
            check(useOn(helper, item, wrench, player, machinePos) == InteractionResult.SUCCESS, "the wrench turns the distiller again");
            check(wrench.getDamageValue() == 2, item + " loses one use per turn, has lost " + wrench.getDamageValue());
        }

        // A click that turns nothing (a stone block has no rotation) wears nothing.
        BlockPos stonePos = new BlockPos(3, 2, 2);
        helper.setBlock(stonePos, Blocks.STONE);
        ItemStack idle = new ItemStack(BCCoreItems.WRENCH.get());
        check(useOn(helper, BCCoreItems.WRENCH.get(), idle, player, stonePos) != InteractionResult.SUCCESS, "a stone block does not turn");
        check(idle.getDamageValue() == 0, "a click that turns nothing wears nothing");
        helper.succeed();
    }

    private static InteractionResult useOn(GameTestHelper helper, ItemWrench_Neptune item, ItemStack wrench, Player player, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return item.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wrench,
            new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
    }

    /**
     * A hit on a mob costs one use, where a vanilla tool costs two. This replays the two calls {@code Player#attack}
     * makes for the held stack (on 1.21.10 and later the wear comes from the weapon component, on 1.21.1 from the
     * item).
     */
    public static void hitOnAMobWearsOneUse(GameTestHelper helper) {
        Mob cow = spawnCow(helper);
        try {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            // Player#attack calls postHurtEnemy only when hurtEnemy answers true.
            check(wrench.hurtEnemy(cow, player), "a hit with the wrench counts as a weapon hit");
            wrench.postHurtEnemy(cow, player);
            check(wrench.getDamageValue() == 1, "one hit costs one use, has lost " + wrench.getDamageValue());
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    // ---- zombies ----

    private static final int TRIALS = 3000;

    /**
     * Zombies carry a wrench only when their spawn was finalized and vanilla left their hand empty (so a mob that never
     * went through a spawn finalize, a mob already holding something, and a mob that is not a zombie never get one),
     * and the events reach the mod through the game bus (the tests post them there, so a handler that was never
     * subscribed fails too),
     * and some of the finalized ones do. The chance is a 1% roll, so the counts are bounded far from both ends over
     * {@value #TRIALS} rolls: a roll that never hits, or hits on most, fails.
     */
    public static void zombiesSometimesCarryAWrench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob zombie = spawnZombie(helper);
        Mob cow = spawnCow(helper);
        try {
            // Settle: a spawn that went through finalize before this test must not still be waiting to join.
            join(level, zombie);
            int withoutFinalize = 0;
            for (int i = 0; i < TRIALS; i++) {
                zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                join(level, zombie);
                if (!zombie.getMainHandItem().isEmpty()) {
                    withoutFinalize++;
                }
            }
            check(withoutFinalize == 0, "a zombie that was never finalized got a wrench " + withoutFinalize + " times");

            int carried = 0;
            for (int i = 0; i < TRIALS; i++) {
                zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                finalizeSpawn(level, zombie);
                join(level, zombie);
                if (zombie.getMainHandItem().is(BCCoreItems.WRENCH.get())) {
                    carried++;
                } else {
                    check(zombie.getMainHandItem().isEmpty(), "only a Basic Wrench may be put in a zombie's hand");
                }
            }
            check(carried >= 1, "no zombie out of " + TRIALS + " finalized spawns carried a wrench");
            check(carried <= TRIALS / 5, carried + " of " + TRIALS + " zombies carried a wrench: far above the vanilla weapon chance");

            // Vanilla's own sword is never replaced.
            for (int i = 0; i < TRIALS; i++) {
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                finalizeSpawn(level, zombie);
                join(level, zombie);
                check(zombie.getMainHandItem().is(Items.IRON_SWORD), "a zombie's iron sword was replaced by a wrench");
            }

            // A mob that is no zombie is never marked, so never given one.
            for (int i = 0; i < TRIALS; i++) {
                finalizeSpawn(level, cow);
                join(level, cow);
                check(cow.getMainHandItem().isEmpty(), "a cow was given a wrench");
            }
        } finally {
            zombie.discard();
            cow.discard();
        }
        helper.succeed();
    }

    /**
     * A zombie killed by a player sometimes drops a wrench, always worn and never one the player did not earn, and
     * Looting makes it likelier. Over {@value #TRIALS} kills at the base 1% chance the count is bounded far from both
     * ends; Looting III (about 4%) must beat the base count. A kill no player earned (not "recently hit") and a mob
     * that is no zombie never drop one.
     */
    public static void zombiesSometimesDropAWornWrench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob zombie = spawnZombie(helper);
        Mob cow = spawnCow(helper);
        try {
            Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
            DamageSource bare = level.damageSources().generic();
            check(drops(level, zombie, bare, false).isEmpty(), "a zombie killed by something else dropped a wrench");
            check(drops(level, cow, bare, true).isEmpty(), "a cow dropped a wrench");
            // A carrier already drops its wrench as equipment, so the extra drop never adds a second one.
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BCCoreItems.WRENCH.get()));
            check(drops(level, zombie, bare, true).isEmpty(), "a zombie carrying a wrench dropped a second one");
            zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

            List<ItemStack> plain = drops(level, zombie, bare, true);
            check(plain.size() >= 1, "no zombie out of " + TRIALS + " player kills dropped a wrench");
            check(plain.size() <= TRIALS / 10, plain.size() + " of " + TRIALS + " zombies dropped a wrench: far above 1%");
            for (ItemStack wrench : plain) {
                check(wrench.is(BCCoreItems.WRENCH.get()), "a zombie drops only the Basic Wrench");
                check(wrench.getDamageValue() >= 1 && wrench.getDamageValue() <= wrench.getMaxDamage(),
                    "a dropped wrench is worn like dropped equipment, damage " + wrench.getDamageValue() + " of " + wrench.getMaxDamage());
            }

            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), 3);
            killer.setItemInHand(InteractionHand.MAIN_HAND, sword);
            List<ItemStack> looted = drops(level, zombie, level.damageSources().playerAttack(killer), true);
            check(looted.size() > plain.size(), "Looting III gave " + looted.size() + " wrenches over " + TRIALS
                + " kills, no more than the " + plain.size() + " without it");
        } finally {
            zombie.discard();
            cow.discard();
        }
        helper.succeed();
    }

    private static List<ItemStack> drops(ServerLevel level, Mob victim, DamageSource source, boolean recentlyHit) {
        List<ItemStack> wrenches = new ArrayList<>();
        for (int i = 0; i < TRIALS; i++) {
            List<ItemEntity> drops = new ArrayList<>();
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(victim, source, drops, recentlyHit));
            for (ItemEntity drop : drops) {
                wrenches.add(drop.getItem());
            }
        }
        return wrenches;
    }

    private static void join(ServerLevel level, Mob mob) {
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(mob, level));
    }

    private static void finalizeSpawn(ServerLevel level, Mob mob) {
        //? if >=1.21.10 {
        net.minecraft.world.entity.EntitySpawnReason reason = net.minecraft.world.entity.EntitySpawnReason.NATURAL;
        //?} else {
        /*net.minecraft.world.entity.MobSpawnType reason = net.minecraft.world.entity.MobSpawnType.NATURAL;*/
        //?}
        NeoForge.EVENT_BUS.post(new FinalizeSpawnEvent(mob, level, mob.getX(), mob.getY(), mob.getZ(),
            level.getCurrentDifficultyAt(mob.blockPosition()), reason, null, null));
    }

    /** A zombie that stays put. Each test runs in one tick, so it never gets to burn in the sun. */
    private static Mob spawnZombie(GameTestHelper helper) {
        //? if >=26.2 {
        Mob mob = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 2, 2, 2);
        //?} else {
        /*Mob mob = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 2, 2, 2);*/
        //?}
        mob.setNoAi(true);
        return mob;
    }

    private static Mob spawnCow(GameTestHelper helper) {
        //? if >=26.2 {
        Mob mob = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 4, 2, 4);
        //?} else {
        /*Mob mob = helper.spawn(net.minecraft.world.entity.EntityType.COW, 4, 2, 4);*/
        //?}
        mob.setNoAi(true);
        return mob;
    }
}
