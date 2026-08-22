/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.core.BCCoreItems;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.lib.misc.GameProfileUtil;

/**
 * Regression coverage for the flood gate's wrench-toggle path.
 * <p>
 * The original 26.1.1 port handled wrench clicks in {@code useWithoutItem}, which
 * correctly toggled the side but bypassed {@code ItemWrench_Neptune.useOn} entirely —
 * so the {@code wrenched} advancement was never granted. The fix moves wrench handling
 * to {@code useItemOn} and manually calls {@code wrench.wrenchUsed(...)} (the
 * advancement entry point), matching the {@code BlockEngineCreative} pattern.
 */
public class FloodGateTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /**
     * Wrenching a non-UP side must flip that side's open/closed state in the blockstate
     * AND in the tile's {@code openSides} set, AND return SUCCESS so the wrench-item
     * interaction is consumed (and {@code wrenchUsed} is reached on the same code path).
     */
    public static void testWrenchTogglesSide(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.FLOOD_GATE.get());
        //? if >=1.21.10 {
        TileFloodGate floodGate = helper.getBlockEntity(pos, TileFloodGate.class);
        //?} else {
        /*TileFloodGate floodGate = helper.getBlockEntity(pos);*/
        //?}
        BlockFloodGate block = (BlockFloodGate) BCFactoryBlocks.FLOOD_GATE.get();
        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        BlockPos absPos = helper.absolutePos(pos);

        // Default state has NORTH open.
        assertTrue(floodGate.openSides.contains(Direction.NORTH),
                "NORTH should start open in the default flood gate state");
        assertTrue(helper.getBlockState(pos).getValue(BlockFloodGate.CONNECTED_MAP.get(Direction.NORTH)),
                "Blockstate NORTH should start true");

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.NORTH, absPos, false);
        Object result = invokeUseItemOn(block, wrench, helper.getBlockState(pos),
                helper, absPos, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), hit);

        assertTrue(isSuccess(result),
                "useItemOn with a wrench on a togglable side must return SUCCESS, got " + result);
        assertTrue(!floodGate.openSides.contains(Direction.NORTH),
                "openSides should no longer contain NORTH after wrench toggle");
        assertTrue(!helper.getBlockState(pos).getValue(BlockFloodGate.CONNECTED_MAP.get(Direction.NORTH)),
                "Blockstate NORTH should be false after wrench toggle");

        // Toggling again puts it back.
        BlockHitResult hit2 = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.NORTH, absPos, false);
        Object result2 = invokeUseItemOn(block, wrench, helper.getBlockState(pos),
                helper, absPos, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), hit2);
        assertTrue(isSuccess(result2), "Second toggle should also return SUCCESS");
        assertTrue(floodGate.openSides.contains(Direction.NORTH),
                "openSides should contain NORTH again after second toggle");

        helper.succeed();
    }

    /**
     * Clicking the UP face with a wrench has no togglable property (UP is excluded from
     * {@code CONNECTED_MAP}), so {@code useItemOn} must return {@code TRY_WITH_EMPTY_HAND}
     * — falling through to default block behaviour without consuming the interaction or
     * spuriously granting the {@code wrenched} advancement.
     */
    public static void testWrenchOnTopFaceFallsThrough(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.FLOOD_GATE.get());
        //? if >=1.21.10 {
        TileFloodGate floodGate = helper.getBlockEntity(pos, TileFloodGate.class);
        //?} else {
        /*TileFloodGate floodGate = helper.getBlockEntity(pos);*/
        //?}
        BlockFloodGate block = (BlockFloodGate) BCFactoryBlocks.FLOOD_GATE.get();
        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        BlockPos absPos = helper.absolutePos(pos);

        var sidesBefore = java.util.EnumSet.copyOf(floodGate.openSides);

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.UP, absPos, false);
        Object result = invokeUseItemOn(block, wrench, helper.getBlockState(pos),
                helper, absPos, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), hit);

        assertTrue(isFallThrough(result),
                "useItemOn on UP face must fall through to the default block interaction, got " + result);
        assertTrue(floodGate.openSides.equals(sidesBefore),
                "openSides must be unchanged after UP click");

        helper.succeed();
    }

    /**
     * Non-wrench items must pass through to default block interaction
     * (i.e. {@code TRY_WITH_EMPTY_HAND}); the floodgate has no item-tank GUI, so this
     * is the no-op path.
     */
    public static void testNonWrenchItemFallsThrough(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.FLOOD_GATE.get());
        //? if >=1.21.10 {
        TileFloodGate floodGate = helper.getBlockEntity(pos, TileFloodGate.class);
        //?} else {
        /*TileFloodGate floodGate = helper.getBlockEntity(pos);*/
        //?}
        BlockFloodGate block = (BlockFloodGate) BCFactoryBlocks.FLOOD_GATE.get();
        ItemStack stick = new ItemStack(Items.STICK);
        BlockPos absPos = helper.absolutePos(pos);

        var sidesBefore = java.util.EnumSet.copyOf(floodGate.openSides);

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absPos), Direction.NORTH, absPos, false);
        Object result = invokeUseItemOn(block, stick, helper.getBlockState(pos),
                helper, absPos, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), hit);

        assertTrue(isFallThrough(result),
                "useItemOn with a non-wrench must fall through to the default block interaction, got " + result);
        assertTrue(floodGate.openSides.equals(sidesBefore),
                "openSides must be unchanged when clicked with a non-wrench item");

        helper.succeed();
    }

    /**
     * {@code useItemOn} is {@code protected} on {@code BlockBehaviour}. Reflection here
     * is the simplest way to drive it from test code without standing up a real player
     * interaction sequence — same pattern as {@link DistillerTester#testWrenchPassesThroughUseItemOn}.
     * <p>
     * Returns {@code Object} because {@code useItemOn}'s result type differs across the
     * cliff — {@code InteractionResult} on 26.x, {@code ItemInteractionResult} on 1.21.1 —
     * so the per-node predicates ({@link #isSuccess} / {@link #isFallThrough}) interpret it.
     */
    private static Object invokeUseItemOn(BlockFloodGate block, ItemStack stack,
            BlockState state, GameTestHelper helper, BlockPos absPos,
            net.minecraft.world.entity.player.Player player, BlockHitResult hit) {
        try {
            java.lang.reflect.Method m = net.minecraft.world.level.block.state.BlockBehaviour.class
                    .getDeclaredMethod("useItemOn",
                            ItemStack.class, BlockState.class, net.minecraft.world.level.Level.class,
                            BlockPos.class, net.minecraft.world.entity.player.Player.class,
                            InteractionHand.class, BlockHitResult.class);
            m.setAccessible(true);
            return m.invoke(block, stack, state, helper.getLevel(), absPos, player,
                    InteractionHand.MAIN_HAND, hit);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke useItemOn reflectively", e);
        }
    }

    /** True when the interaction consumed the item — {@code InteractionResult.SUCCESS} on both nodes. */
    private static boolean isSuccess(Object result) {
        //? if >=1.21.10 {
        return result == InteractionResult.SUCCESS;
        //?} else {
        /*return result == net.minecraft.world.ItemInteractionResult.SUCCESS;*/
        //?}
    }

    /** True for "fall through to the default block interaction" — 26.x {@code TRY_WITH_EMPTY_HAND}
     *  vs 1.21.1 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} (BlockUtil.itemUseTryWithEmptyHand maps
     *  both). */
    private static boolean isFallThrough(Object result) {
        //? if >=1.21.10 {
        return result == InteractionResult.TRY_WITH_EMPTY_HAND;
        //?} else {
        /*return result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;*/
        //?}
    }

    /**
     * Pins the "Flooding the world" advancement wiring.
     * <p>
     * (a) The advancement is granted from {@link TileFloodGate#serverTick} via
     * {@link buildcraft.lib.misc.AdvancementUtil}, which awards the {@code code_trigger}
     * criterion — so the JSON must keep a criterion under exactly that name.
     * <p>
     * (b) {@link TileFloodGate#onPlacedBy} must record the placing player as the owner and
     * {@code saveAdditional} must persist it, so the grant has a recipient that survives a
     * chunk reload.
     */
    public static void testFloodingTheWorldAdvancement(GameTestHelper helper) {
        // (a) JSON contract — advancement loaded with a 'code_trigger' criterion.
        net.minecraft.advancements.AdvancementHolder holder =
                helper.getLevel().getServer().getAdvancements().get(
                        net.minecraft.resources.Identifier.parse("buildcraftunofficial:flooding_the_world"));
        assertTrue(holder != null, "flooding_the_world advancement is not loaded");
        assertTrue(holder.value().criteria().containsKey("code_trigger"),
                "flooding_the_world must keep a 'code_trigger' criterion — "
                        + "AdvancementUtil.unlockAdvancement awards that criterion name");

        // (b) Owner tracking — onPlacedBy records the placer, saveAdditional persists it.
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCFactoryBlocks.FLOOD_GATE.get());
        //? if >=1.21.10 {
        TileFloodGate gate = helper.getBlockEntity(pos, TileFloodGate.class);
        //?} else {
        /*TileFloodGate gate = helper.getBlockEntity(pos);*/
        //?}
        net.minecraft.world.entity.player.Player placer =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        gate.onPlacedBy(placer);
        assertTrue(gate.getOwner() != null, "onPlacedBy must record the placer as owner");
        assertTrue(GameProfileUtil.getId(gate.getOwner()).equals(GameProfileUtil.getId(placer.getGameProfile())),
                "recorded owner UUID must match the placing player");

        net.minecraft.nbt.CompoundTag saved = gate.saveCustomOnly(helper.getLevel().registryAccess());
        String ownerUuid = GameProfileUtil.getId(placer.getGameProfile()).toString();
        //? if >=1.21.10 {
        assertTrue(saved.getString("ownerUUID").orElse("").equals(ownerUuid),
                "owner UUID must be persisted to NBT by saveAdditional");
        //?} else {
        /*assertTrue(saved.getString("ownerUUID").equals(ownerUuid),
                "owner UUID must be persisted to NBT by saveAdditional");*/
        //?}

        helper.succeed();
    }
}
