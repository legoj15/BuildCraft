/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.MjAPI;

import buildcraft.core.BCCoreConfig;

public class BlockUtil {
    /** Mining multiplier, set by BCCoreConfig on init. Default 1.0. */
    public static double miningMultiplier = 1.0;

    /**
     * The blocks that are a mining machine's own plumbing: the tube a Mining Well or Pump lays between itself and its
     * target. Machines treat them as transparent when they probe, flood-fill or count neighbours (a bore drilled
     * through a pond is waterlogged, a water source that is not the pond's), and robots leave them alone. A data tag
     * (it holds {@code buildcraftunofficial:tube}) so code outside the factory package can ask without importing the
     * block.
     */
    public static final TagKey<Block> MINER_TUBES =
            TagKey.create(Registries.BLOCK, Identifier.parse("buildcraftunofficial:miner_tubes"));

    /** Whether {@code state} is a miner's tube; see {@link #MINER_TUBES}. */
    public static boolean isMinerTube(BlockState state) {
        return state.is(MINER_TUBES);
    }

    /**
     * Whether the chunk holding {@code pos} is actually in memory, a check that never loads it: a block read there
     * ({@code getBlockState}, {@code getFluidState}, {@code getBlockEntity}) is then answered from the loaded chunk, not
     * by a synchronous load-or-generate on the server thread. A machine that reads cells it was not told about (a
     * flood fill, a probe along a line) asks first and treats an absent chunk as out of reach.
     *
     * <p>Presence, not {@code Level.hasChunkAt}/{@code isLoaded}: on a live {@code ServerLevel} those only test that
     * the chunk's ticket level <em>could</em> reach {@code FULL}, which holds for a chunk that is queued but not yet
     * loaded (the same trap {@code OilGenStructure.isChunkLoaded} documents), so the next read would load it anyway.
     * {@code getChunkNow} returns the chunk only when it is genuinely there, and never schedules a load; it answers
     * null off the server thread, which reads as absent.
     */
    public static boolean isChunkPresent(LevelAccessor level, BlockPos pos) {
        return isChunkPresent(level, pos.getX(), pos.getZ());
    }

    /** {@link #isChunkPresent(LevelAccessor, BlockPos)} for a column given by its <em>block</em> coordinates (not
     *  chunk coordinates). */
    public static boolean isChunkPresent(LevelAccessor level, int blockX, int blockZ) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(blockX),
                SectionPos.blockToSectionCoord(blockZ)) != null;
    }

    /** {@link #isChunkPresent(LevelAccessor, BlockPos)}, telling {@code absentChunk} the {@link #chunkKey} of the chunk
     *  when it is not in memory (null: tell no one). */
    public static boolean isChunkPresent(LevelAccessor level, BlockPos pos, @Nullable LongConsumer absentChunk) {
        return isChunkPresent(level, pos.getX(), pos.getZ(), absentChunk);
    }

    private static boolean isChunkPresent(LevelAccessor level, int blockX, int blockZ,
            @Nullable LongConsumer absentChunk) {
        if (isChunkPresent(level, blockX, blockZ)) {
            return true;
        }
        if (absentChunk != null) {
            absentChunk.accept(chunkKey(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ)));
        }
        return false;
    }

    /** A chunk's coordinates as one long, x in the low half and z in the high half. (Not {@code ChunkPos.asLong}: the
     *  26.3 node has no such method.) */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /**
     * How far, horizontally, the world's reaction to a drain reads from the drained cell: three blocks. Taking the
     * fluid sets the cell to air (or dries it), and the cells around it get {@code neighborChanged}. A block that asks
     * whether it is powered ({@code hasNeighborSignal}: a lamp, a piston, a door, a rail, a dropper, TNT) reads the
     * signal of the cells around itself, which for a solid block is the direct signal of the cells around that one
     * (1 + 1 + 1). A fluid or observer update only schedules a tick, which runs in a ticking chunk (vanilla keeps its
     * surroundings loaded), and {@code updateIndirectNeighbourShapes} is redstone wire's alone (a drained cell is
     * never wire).
     *
     * <p>That is the reach of the drain's own update, not of everything it can set off. A neighbour that changes in
     * response sends its own update, and one kind does so on the spot: a lava cell with water beside it that has not
     * turned to stone yet becomes obsidian, cobblestone or basalt inside {@code LiquidBlock.neighborChanged}
     * ({@code FluidInteractionRegistry.canInteract} calls {@code setBlockAndUpdate}), and the cells around it get
     * their update in turn: four blocks from the drained cell for one such change, one more for each further one in
     * a row. Lava and water only sit side by side like that when world generation (or a command) left them so, never
     * because a machine took a bucket, so the guard does not chase it: a drain beside such lava can still touch an
     * absent chunk that lies just beyond the guarded reach.
     */
    public static final int DRAIN_UPDATE_REACH = 3;

    /** The offsets, along each horizontal axis, of the corners of the square of cells within {@link #DRAIN_UPDATE_REACH}
     *  of a drained cell. */
    private static final int[] DRAIN_REACH_CORNER_OFFSETS = {-DRAIN_UPDATE_REACH, DRAIN_UPDATE_REACH};

    /**
     * Whether draining the cell at {@code pos} stays inside chunks that are in memory: the chunk holding the cell,
     * and every chunk within {@link #DRAIN_UPDATE_REACH} blocks of it. Taking the fluid updates the cells around it
     * out to that reach, and a read in an absent chunk loads it (generating it, if it was never made) on the server
     * thread. Reads no block itself. A cell at least that far in from every chunk border cannot reach another chunk,
     * so only a cell near a border asks about more than its own; the square of cells within reach is narrower than a
     * chunk, so the chunks it overlaps are exactly the ones its four corners lie in (the diagonal chunk across a
     * corner included).
     *
     * <p>Ask at the moment of draining, not only when the cell is chosen: a chunk beside it can unload in between.
     *
     * @param absentChunk told the {@link #chunkKey} of every absent chunk the check meets, not just the first (so a
     *                    machine can remember each and wake when one loads); null when that is not wanted
     */
    public static boolean drainStaysInLoadedChunks(LevelAccessor level, BlockPos pos,
            @Nullable LongConsumer absentChunk) {
        boolean allPresent = isChunkPresent(level, pos, absentChunk);
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (localX >= DRAIN_UPDATE_REACH && localX <= 15 - DRAIN_UPDATE_REACH
                && localZ >= DRAIN_UPDATE_REACH && localZ <= 15 - DRAIN_UPDATE_REACH) {
            return allPresent;
        }
        for (int dx : DRAIN_REACH_CORNER_OFFSETS) {
            for (int dz : DRAIN_REACH_CORNER_OFFSETS) {
                allPresent &= isChunkPresent(level, pos.getX() + dx, pos.getZ() + dz, absentChunk);
            }
        }
        return allPresent;
    }

    // --- Version-neutral useItemOn result values ---
    // BlockBehaviour#useItemOn returns InteractionResult on 1.21.10+ but the older ItemInteractionResult
    // enum on 1.21.1. These return the node-appropriate value so block useItemOn bodies stay shared across
    // nodes; only each override's declared return type is gated.
    //
    // Cross-node mapping of the two "I didn't handle the item" signals (they are NOT interchangeable):
    //   itemUsePass()            -> 26.1: PASS ;             1.21.1: SKIP_DEFAULT_BLOCK_INTERACTION
    //       "skip the block's empty-hand interaction, fall straight through to Item#useOn". On 26.1
    //       plain PASS already skips useWithoutItem (only TRY_WITH_EMPTY_HAND triggers it), so the
    //       1.21.1 equivalent is SKIP_DEFAULT — NOT PASS_TO_DEFAULT (which would run useWithoutItem
    //       first and, for a block whose useWithoutItem opens a GUI, swallow the click before the
    //       held item's useOn — e.g. a wrench could never reach its rotation path). Use this when a
    //       held item must reach its own useOn.
    //   itemUseTryWithEmptyHand() -> 26.1: TRY_WITH_EMPTY_HAND ; 1.21.1: PASS_TO_DEFAULT_BLOCK_INTERACTION
    //       "run the block's empty-hand interaction (useWithoutItem) for this item too". Use when the
    //       block's no-item behaviour should also apply while holding this item.
    //? if >=1.21.10 {
    public static net.minecraft.world.InteractionResult itemUsePass() { return net.minecraft.world.InteractionResult.PASS; }
    public static net.minecraft.world.InteractionResult itemUseSuccess() { return net.minecraft.world.InteractionResult.SUCCESS; }
    public static net.minecraft.world.InteractionResult itemUseConsume() { return net.minecraft.world.InteractionResult.CONSUME; }
    public static net.minecraft.world.InteractionResult itemUseFail() { return net.minecraft.world.InteractionResult.FAIL; }
    public static net.minecraft.world.InteractionResult itemUseTryWithEmptyHand() { return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND; }

    /** Adapt an {@link net.minecraft.world.InteractionResult} (e.g. from a shared GUI-open helper that
     *  both useItemOn and useWithoutItem call) to the useItemOn return type. Identity on 1.21.10+; maps
     *  to the {@code ItemInteractionResult} enum on 1.21.1 (PASS -> SKIP_DEFAULT_BLOCK_INTERACTION, so a
     *  helper that returns PASS lets the held item's useOn run, matching 26.1's PASS semantics). */
    public static net.minecraft.world.InteractionResult itemUseFrom(net.minecraft.world.InteractionResult result) { return result; }
    //?} else {
    /*public static net.minecraft.world.ItemInteractionResult itemUsePass() { return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; }
    public static net.minecraft.world.ItemInteractionResult itemUseSuccess() { return net.minecraft.world.ItemInteractionResult.SUCCESS; }
    public static net.minecraft.world.ItemInteractionResult itemUseConsume() { return net.minecraft.world.ItemInteractionResult.CONSUME; }
    public static net.minecraft.world.ItemInteractionResult itemUseFail() { return net.minecraft.world.ItemInteractionResult.FAIL; }
    public static net.minecraft.world.ItemInteractionResult itemUseTryWithEmptyHand() { return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }

    public static net.minecraft.world.ItemInteractionResult itemUseFrom(net.minecraft.world.InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> net.minecraft.world.ItemInteractionResult.SUCCESS;
            case CONSUME -> net.minecraft.world.ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> net.minecraft.world.ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> net.minecraft.world.ItemInteractionResult.FAIL;
            case PASS -> net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }*/
    //?}

    /**
     * Reads a {@link Direction} facing property off a block state, returning {@code null} when the
     * state does not carry that property instead of throwing.
     * <p>
     * Block-entity renderers run for one more frame after their block is destroyed or replaced. In
     * that frame {@code level.getBlockState(pos)} at the old position has already become
     * {@code minecraft:air} (or some other block), so a bare {@link BlockState#getValue} throws
     * {@code IllegalArgumentException: Cannot get property ... as it does not exist in
     * Block{minecraft:air}}. Routing every facing-dependent render read through this guard turns that
     * crash into a clean skip — callers {@code return} early on {@code null} — instead of repeating an
     * ad-hoc {@code state.hasProperty(prop) ? state.getValue(prop) : fallback} at each site.
     *
     * @param state          the block state to read (typically a live {@code level.getBlockState(pos)})
     * @param facingProperty the direction property, e.g. {@code BlockStateProperties.HORIZONTAL_FACING}
     * @return the facing, or {@code null} when {@code state} lacks {@code facingProperty}
     */
    @Nullable
    public static Direction facingOrNull(BlockState state, Property<Direction> facingProperty) {
        return state.hasProperty(facingProperty) ? state.getValue(facingProperty) : null;
    }

    /**
     * Drives an {@link buildcraft.api.blocks.ICustomRotationHandler} rotation on behalf of a
     * foreign (tag-only) wrench. BuildCraft's own wrench rotates these blocks from its
     * {@code useOn()} via {@link buildcraft.api.blocks.CustomRotationHelper}; a third-party wrench
     * in the {@code c:tools/wrench} tag has no such hook, so the block must drive the rotation
     * itself. Mirrors {@code ItemWrench_Neptune.useOn} (rotate + slide sound + arm swing) minus the
     * BuildCraft advancement, which stays reserved for BuildCraft's own wrench.
     * <p>
     * Returns {@link net.minecraft.world.InteractionResult#CONSUME}; wrap with {@link #itemUseFrom}
     * for the node-correct {@code useItemOn} return type. Callers should only invoke this for a
     * non-{@link buildcraft.api.tools.IToolWrench} wrench (BuildCraft's own wrench keeps its
     * {@code useOn} rotation path via a {@code PASS}).
     */
    public static net.minecraft.world.InteractionResult rotateByForeignWrench(
            Level level, BlockPos pos, BlockState state, Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.core.Direction side) {
        net.minecraft.world.InteractionResult result =
                buildcraft.api.blocks.CustomRotationHelper.INSTANCE.attemptRotateBlock(level, pos, state, side);
        SoundUtil.playSlideSound(level, pos, state, result);
        EntityUtil.swing(player, hand);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /**
     * Returns true if a BuildCraft mining machine is permitted to break the block at {@code pos}.
     * <p>
     * If {@link BCCoreConfig#minePlayerProtected} is true, always returns true (the option is
     * an "override protection" toggle). Otherwise, posts the running version's block-break event with an
     * owner-bound FakePlayer positioned at {@code pos} and returns {@code !event.isCanceled()},
     * so third-party protection mods (FTB Chunks, GriefPrevention, OpenPartiesAndClaims, server
     * protection plugins) can gate the break by cancelling the event. A machine with no recorded
     * owner (or an owner without a name) asks as {@link FakePlayerUtil#BUILDCRAFT_PROFILE}: see
     * {@link MachineIdentity}, the rule the machine's own fake player follows too.
     * <p>
     * Runs once per scanned block in quarry / mining-well / builder loops (and per break for stripes
     * pipes and robots), so the player is the cached one, leased for the duration of the event. The
     * event is a probe — {@link BreakEventCompat#isProbe} tells BuildCraft's own break listeners to
     * ignore it.
     */
    public static boolean canMachineBreak(ServerLevel level, BlockPos pos, @Nullable GameProfile owner) {
        if (BCCoreConfig.minePlayerProtected.get()) {
            return true;
        }
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, MachineIdentity.of(owner), pos)) {
            return BreakEventCompat.canBreak(level, pos, level.getBlockState(pos), lease.player());
        }
    }

    /**
     * {@link #canMachineBreak} for a machine that already holds its fake player (a stripes handler's, leased by the
     * pipe for the whole drop), with no second lease: the break event is posted for {@code machine} standing where
     * {@link #canMachineBreak}'s own player stands, the centre of {@code pos}, so a claim that judges where the actor
     * stands answers both alike. The player is put back where it was, facing the same way, afterwards.
     */
    public static boolean canMachineBreakAs(ServerLevel level, BlockPos pos, Player machine) {
        if (BCCoreConfig.minePlayerProtected.get()) {
            return true;
        }
        Vec3 feet = machine.position();
        float yRot = machine.getYRot();
        float xRot = machine.getXRot();
        FakePlayerUtil.pose(machine, Vec3.atCenterOf(pos), yRot, xRot);
        try {
            return BreakEventCompat.canBreak(level, pos, level.getBlockState(pos), machine);
        } finally {
            FakePlayerUtil.pose(machine, feet, yRot, xRot);
        }
    }

    /**
     * {@link #canMachineBreak}'s twin for placing: returns true if a BuildCraft machine that sets a block down without
     * using an item (the Blueprint Builder) may place a block filling {@code cells} (the cell the block goes in first,
     * then any other cell it fills, such as a bed's head). Always true while {@link BCCoreConfig#minePlayerProtected}
     * is on; otherwise posts a placement event for the owner-bound fake player standing on the first cell, through
     * {@link PlaceEventProbe}, and answers whether no protection mod cancelled it. The event is a probe that places
     * nothing. A machine that places by using an item needs no probe: the item's use posts the right-click and
     * placement events itself (see {@link #tryUseItemOnBlock}).
     */
    public static boolean canMachinePlace(ServerLevel level, List<BlockPos> cells, @Nullable GameProfile owner) {
        if (BCCoreConfig.minePlayerProtected.get()) {
            return true;
        }
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, MachineIdentity.of(owner), cells.get(0))) {
            return PlaceEventProbe.canPlace(level, cells, lease.player());
        }
    }

    /** {@link #canMachinePlace(ServerLevel, List, GameProfile)} for a block that fills one cell. */
    public static boolean canMachinePlace(ServerLevel level, BlockPos pos, @Nullable GameProfile owner) {
        return canMachinePlace(level, List.of(pos), owner);
    }

    /**
     * Right-clicks {@code stack} (the item {@code player} holds in {@code hand}) on the block {@code hit} names, for
     * a machine standing in for a player: the right-click-block event is posted first, so a claim or protection mod
     * can refuse the use; then vanilla's gate (an empty stack, or an item on the player's cooldown, is not used
     * unless the event forced the item use); then the item's {@code useOn} runs. That is the item path of
     * {@code ServerPlayerGameMode.useItemOn} and no more: the block's own use, {@code Item#onItemUseFirst}, the
     * block's feature-flag check, the infinite-materials count restore and the item-used-on-block advancement
     * trigger are not run. On 26.3, where vanilla's {@code useItemOn} puts the stack a use transformed the held one
     * into in the hand, so does this; on 1.21.10 through 26.2 vanilla reads the use's transformed stack nowhere in
     * that method (only {@code useItem} does), and 1.21.1 has no such stack at all, so on those the hand is left as
     * the item left it. That 26.3 guard is a behaviour boundary, not API availability: the accessor is there from
     * 1.21.10. On 26.3 the event is the ONLY ask a hoe's till gets: hoes became a block-transformer component, and
     * tilling no longer posts the tool-modification event.
     *
     * @return {@code true} when the item's use took the action; {@code false} when the event refused it, the gate
     *         stopped it, or the item passed
     * @see #tryUseItemOnBlock for a caller that must tell a refusal from an item that simply passed
     */
    public static boolean useItemOnBlock(
            Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        return tryUseItemOnBlock(level, player, hand, stack, hit) == ItemUse.USED;
    }

    /** What a machine's stand-in click on a block came to. */
    public enum ItemUse {
        /** The item's use took the action. */
        USED,
        /** The click was allowed and the item did nothing with it, or answered with its own FAIL (a block item with no
         *  room, flint and steel aimed at a cell it cannot light): another click may do better. A player's click ends
         *  at a FAIL, but a machine's first click names the block in front and its second names the pipe's own side,
         *  and items such as flint and steel only work from the second. A claim that cancels only the placement
         *  event lands here too, as the same FAIL, and is asked again by the next click. */
        PASSED,
        /** A claim mod cancelled the right-click event or denied the item's use, or vanilla's item gate stopped the
         *  item: the machine stops trying clicks. Stricter than a player, whose click after a cancelled event whose
         *  result was left at the default PASS goes on to use the item in the air; failing closed costs a pipe an
         *  air use the claim never refused, and never lets one through. */
        REFUSED
    }

    /** {@link #useItemOnBlock} with the outcome kept apart: a refusal is not an item that passed. */
    public static ItemUse tryUseItemOnBlock(
            Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        PlayerInteractEvent.RightClickBlock ask = CommonHooks.onRightClickBlock(player, hand, hit.getBlockPos(), hit);
        if (ask.isCanceled() || ask.getUseItem().isFalse()) {
            return refused(player);
        }
        if (!ask.getUseItem().isTrue() && itemGateStops(player, stack)) {
            return refused(player);
        }
        InteractionResult result = stack.useOn(new UseOnContext(level, player, hand, stack, hit));
        // Behaviour, not API: vanilla's useItemOn swaps the hand only from 26.3 (see the Javadoc above).
        //? if >=26.3 {
        if (result instanceof InteractionResult.Success success) {
            ItemStack transformed = success.heldItemTransformedTo();
            // Against the stack the use started from, as ServerPlayerGameMode#useItemOn compares: an item that put
            // a stack in the hand itself and answered with the original keeps the stack it put there.
            if (transformed != null && transformed != stack) {
                player.setItemInHand(hand, transformed);
            }
        }
        //?}
        return result.consumesAction() ? ItemUse.USED : ItemUse.PASSED;
    }

    private static ItemUse refused(Player player) {
        MachineRefusals.record(player);
        return ItemUse.REFUSED;
    }

    /**
     * Uses {@code stack} (the item {@code player} holds in {@code hand}) in the air, for a machine standing in for a
     * player: the item right-click event is posted first, so a claim or protection mod can refuse the use; then
     * vanilla's gate (an empty stack or an item on the player's cooldown is not used); then the item's use runs
     * through {@code ItemStack#use}, so the after-use side effects (a use remainder, a cooldown) apply. That is the
     * front of {@code ServerPlayerGameMode.useItem}, and what that method does with the answer is done too: the
     * stack the use transformed the held one into goes into {@code hand}, the way a bucket that took a water source
     * leaves the water bucket in a player's hand. (The caller finds it where it finds everything else the player
     * holds. The refresh of a real player's screen is not run.) Vanilla's {@code useItem} does this on every line, so
     * there is no behaviour boundary in here; the 1.21.10 split below is API availability only: before it the use
     * answers with a holder carrying the resulting stack, from it with a result whose success carries the transformed
     * stack (null when the use left the hand alone).
     *
     * @return {@code true} when the item's use took the action; {@code false} when the event refused it, the gate
     *         stopped it, or the item passed
     */
    public static boolean useItemInAir(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (itemGateStops(player, stack) || CommonHooks.onItemRightClick(player, hand) != null) {
            refused(player);
            return false;
        }
        // API availability, not behaviour: both forms put the same stack in the hand (see the Javadoc above).
        //? if >=1.21.10 {
        InteractionResult result = stack.use(level, player, hand);
        ItemStack transformed =
                result instanceof InteractionResult.Success success ? success.heldItemTransformedTo() : null;
        //?} else {
        /*net.minecraft.world.InteractionResultHolder<ItemStack> used = stack.use(level, player, hand);
        InteractionResult result = used.getResult();
        ItemStack transformed = used.getObject();*/
        //?}
        // Against the stack the use started from, as ServerPlayerGameMode#useItem compares (1.21.1's holder carries
        // that very stack when the use left it alone), and a hand stack the use emptied is cleared as there.
        if (transformed != null && transformed != stack) {
            player.setItemInHand(hand, transformed);
        }
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        return result.consumesAction();
    }

    /** Vanilla's check ahead of any item use: nothing to use, or the item is cooling down for the player. */
    private static boolean itemGateStops(Player player, ItemStack stack) {
        //? if >=1.21.10 {
        boolean cooling = player.getCooldowns().isOnCooldown(stack);
        //?} else {
        /*boolean cooling = player.getCooldowns().isOnCooldown(stack.getItem());*/
        //?}
        return stack.isEmpty() || cooling;
    }

    /** Returns the fluid associated with a block if it is a fluid block, or null otherwise. */
    @Nullable
    public static Fluid getFluidWithFlowing(Block block) {
        if (block instanceof LiquidBlock liquidBlock) {
            Fluid fluid = liquidBlock.fluid;
            if (fluid != null && fluid != Fluids.EMPTY) {
                return fluid;
            }
        }
        return null;
    }

    /** Fluids at or below this viscosity (water, light fuel, …) are passable to miners — the Mining Well's tube and
     *  the Quarry's drill go straight through them; thicker fluids (lava, oil) stop them. 1.12.2 parity. */
    public static final int PASSABLE_FLUID_MAX_VISCOSITY = 1000;

    /** True for a fluid thin enough that miners pass through it; see {@link #PASSABLE_FLUID_MAX_VISCOSITY}. */
    public static boolean isPassableFluid(Fluid fluid) {
        return fluid.getFluidType().getViscosity() <= PASSABLE_FLUID_MAX_VISCOSITY;
    }

    /**
     * True for a real block sharing its cell with a fluid — a waterlogged slab or stairs, kelp, seagrass: any
     * {@link LiquidBlockContainer} whose fluid state is not empty. {@link #getFluidWithFlowing(Level, BlockPos)}
     * reports such a cell as fluid, but miners should see the block: breaking it leaves the fluid behind.
     */
    public static boolean isFluidloggedBlock(BlockState state) {
        return state.getBlock() instanceof LiquidBlockContainer && !state.getFluidState().isEmpty();
    }

    /** Returns the fluid at a world position, including flowing fluids, or null if none. */
    @Nullable
    public static Fluid getFluidWithFlowing(Level world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        if (!fluidState.isEmpty()) {
            return fluidState.getType();
        }
        return getFluidWithFlowing(world.getBlockState(pos).getBlock());
    }

    /**
     * Returns the fluid at a world position only if it is a full source block.
     * Returns null for flowing fluid or non-fluid blocks.
     */
    @Nullable
    public static Fluid getFluid(Level world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        if (!fluidState.isEmpty() && fluidState.isSource()) {
            return fluidState.getType();
        }
        return null;
    }

    /**
     * Returns the fluid from a BlockState only if it is a full source block.
     * Returns null for flowing fluid or non-fluid blockstates.
     */
    @Nullable
    public static Fluid getFluidWithoutFlowing(BlockState state) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty() && fluidState.isSource()) {
            return fluidState.getType();
        }
        return null;
    }

    /**
     * The source fluid {@link #drainBlock} can take from this state, or null. Drainable means what a bucket
     * could take: the state holds a source fluid AND its block is a {@link BucketPickup} — a fluid block, or
     * a waterlogged block (drained by un-waterlogging it, never by deleting it). A source fluid state on any
     * other block (kelp, seagrass: always-waterlogged plants a bucket cannot empty) is not drainable. Pumps
     * and the robot pump board must pick their targets with this, or they would stall on (or, before this
     * existed, delete) such a block.
     *
     * <p>This is the state-only half: it cannot tell whether the block survives drying out, so a caller that has
     * the world must use {@link #getDrainableFluid(Level, BlockPos)}, which also refuses such a block.
     */
    @Nullable
    public static Fluid getDrainableFluid(BlockState state) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty() || !fluidState.isSource() || !(state.getBlock() instanceof BucketPickup)) {
            return null;
        }
        return fluidState.getType();
    }

    /**
     * {@link #getDrainableFluid(BlockState)} at a world position, minus a waterlogged block whose survival depends on
     * its water. Vanilla's {@code SimpleWaterloggedBlock.pickupBlock} dries the block and then destroys it with drops
     * when the state it had <em>before</em> drying fails {@code canSurvive}; a block that passes that but fails as it
     * stands dry is kept, in a state it cannot hold (it pops at its next shape update, or never should have been
     * dry). A machine must offer neither: the block would be lost to what is meant to take only the water.
     */
    @Nullable
    public static Fluid getDrainableFluid(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Fluid fluid = getDrainableFluid(state);
        if (fluid == null || !survivesDrying(world, pos, state)) {
            return null;
        }
        return fluid;
    }

    /** False for a waterlogged block that drying would kill: its waterlogged state fails {@code canSurvive}
     *  (vanilla's pickup destroys it) or its dry state does (it could not stand dry). True for everything else, so a
     *  fluid block or a modded {@link BucketPickup} keeps its own pickup logic. Both tests read the world as it is
     *  now; vanilla's runs with the dry state already in the cell, which only a block whose survival reads its own
     *  cell could tell apart. */
    private static boolean survivesDrying(Level world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof LiquidBlock || !state.hasProperty(BlockStateProperties.WATERLOGGED)
                || !state.getValue(BlockStateProperties.WATERLOGGED)) {
            return true;
        }
        return state.canSurvive(world, pos)
                && state.setValue(BlockStateProperties.WATERLOGGED, false).canSurvive(world, pos);
    }

    /**
     * Drains one bucket of source fluid from the world, as a bucket would: a fluid block becomes air, a
     * waterlogged block is un-waterlogged and kept (block entity, contents and all). Anything
     * {@link #getDrainableFluid(Level, BlockPos)} rejects is left untouched, including a waterlogged block that
     * drying would kill (a bucket's pickup could destroy it).
     *
     * @param world    the level
     * @param pos      the position to drain
     * @param doDrain  if true, actually takes the fluid; if false, simulates only
     * @return a FluidStack of 1000mB if a drainable source was present, or null
     */
    @Nullable
    public static FluidStack drainBlock(Level world, BlockPos pos, boolean doDrain) {
        BlockState state = world.getBlockState(pos);
        Fluid fluid = getDrainableFluid(world, pos);
        if (fluid == null) {
            return null;
        }
        if (doDrain) {
            if (state.getBlock() instanceof LiquidBlock) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (((BucketPickup) state.getBlock()).pickupBlock(null, world, pos, state).isEmpty()) {
                return null;
            }
        }
        return new FluidStack(fluid, 1000);
    }

    /** Creates a comparator that falls back to coordinate comparison when the parent reports equality,
     * ensuring a total ordering for use with sorted streams. */
    public static Comparator<BlockPos> uniqueBlockPosComparator(Comparator<BlockPos> parent) {
        return (a, b) -> {
            int parentValue = parent.compare(a, b);
            if (parentValue != 0) {
                return parentValue;
            } else if (a.getX() != b.getX()) {
                return Integer.compare(a.getX(), b.getX());
            } else if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            } else if (a.getZ() != b.getZ()) {
                return Integer.compare(a.getZ(), b.getZ());
            } else {
                return 0;
            }
        };
    }

    /** Returns true if the block at the given position is unbreakable (hardness < 0). */
    public static boolean isUnbreakableBlock(Level world, BlockPos pos, GameProfile owner) {
        BlockState state = world.getBlockState(pos);
        return state.getDestroySpeed(world, pos) < 0;
    }

    /** Computes the MJ power required to break a block, based on its hardness. */
    public static long computeBlockBreakPower(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        // Vanilla pure-fluid blocks (water/lava and modded LiquidBlock instances) ship with
        // strength=100 — that's their *explosion resistance*, not break time; vanilla treats
        // fluids as free-to-break (clicking a water source clears it instantly with any tool or
        // empty hand). Reading 100 here as "time to break" gives a target of 3232 MJ per fluid
        // block (40× stone, more than obsidian), which makes the Builder's CLEAR-mode mopping
        // appear stuck — the break beam fires for ~13 ticks per single water block while power
        // accumulates, then clears it for one tick before vanilla flow refills the position. The
        // user-visible symptom is "laser fires forever, water never clears." Charge a flat 1 MJ
        // for any LiquidBlock so the cost lines up with vanilla's "free" intent and the mop is
        // visibly fast.
        if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return MjAPI.MJ;
        }
        float hardness = state.getDestroySpeed(world, pos);
        return (long) Math.floor(16 * MjAPI.MJ * ((hardness + 1) * 2) * miningMultiplier);
    }

    /**
     * Bundle returned by {@link #breakBlockAndGetDropsWithXp}: the loot the block produced under
     * the wielded tool, the XP the block would have awarded for that break, and (if the broken
     * block was a {@link LiquidBlock} source) the captured fluid that the position no longer
     * holds.
     * <p>
     * XP is captured here (rather than spawned at {@code pos}) so callers can re-anchor the orb
     * at the machine that fired the laser. Doing it this way side-steps the duplication concerns
     * a "spawn at pos then teleport to machine" approach has — the orb is only ever created at
     * the machine's chosen location, with {@code Block.popExperience} / {@code ExperienceOrb.award}
     * doing the splitting.
     * <p>
     * Captured fluid is reported (rather than auto-tanked) so the Builder can absorb 1000 mB per
     * cleared source under CLEAR mode without other callers (Quarry / Filler / Stripes / Mining
     * Well) inheriting the behaviour by accident. {@code capturedFluid} is empty for non-fluid
     * blocks and for flowing (non-source) fluid breaks — flowing fluid is transient world-state
     * with no meaningful volume to bucket up.
     */
    public record BreakResult(List<ItemStack> drops, int xp, FluidStack capturedFluid) {}

    /**
     * Records the block states around {@code pos} that a removal there can invalidate indirectly —
     * the direct neighbours plus the vertical columns beside them (vine curtains and hanging chains
     * are the deep case: a jungle strand can hang dozens of blocks below the block it was growing
     * on). Call this BEFORE removing the block, pass the result to
     * {@link #resyncChangedBlocks} after, and every support-driven block the removal cascaded
     * through gets pushed to clients explicitly.
     *
     * <p>Why this exists: the support cascade (a vine popping when its last anchor is broken) runs
     * on the server through shape updates that strip {@code UPDATE_CLIENTS} (vanilla relies on each
     * client replaying the same cascade locally), and {@code sendBlockUpdated} is skipped outright
     * for chunks that are not block-ticking. A machine that breaks blocks with no player predicting
     * the break — robots above all — therefore leaves the client rendering blocks the server no
     * longer has: the "lumberjack leaves floating vines" report. Vanilla's own client resyncs the
     * moment the chunk reloads, so this is purely a sync repair, not a world change.
     */
    public static Long2ObjectOpenHashMap<BlockState> snapshotSupportsForResync(Level world, BlockPos pos) {
        Long2ObjectOpenHashMap<BlockState> snapshot = new Long2ObjectOpenHashMap<>();
        for (BlockPos p : resyncRegion(pos)) {
            snapshot.put(p.asLong(), world.getBlockState(p));
        }
        return snapshot;
    }

    /**
     * Pushes every snapshotted position whose state no longer matches to clients via
     * {@code sendBlockUpdated} — deliberately bypassing the block-ticking gate vanilla applies to
     * cascade updates, so machines working in player-away chunks still stay visually in sync.
     * Returns the positions that were resynced (in snapshot order; empty when nothing changed).
     */
    public static List<BlockPos> resyncChangedBlocks(ServerLevel world, Long2ObjectOpenHashMap<BlockState> snapshot) {
        List<BlockPos> resynced = new ArrayList<>();
        for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<BlockState> entry : snapshot.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            BlockState before = entry.getValue();
            BlockState current = world.getBlockState(pos);
            if (current != before) {
                world.sendBlockUpdated(pos, before, current, Block.UPDATE_ALL);
                resynced.add(pos);
            }
        }
        return resynced;
    }

    /** The resync region for {@code pos}: the block, its six neighbours, and the vertical columns
     *  above/below the four side neighbours (the shapes support cascades actually travel). */
    private static Iterable<BlockPos> resyncRegion(BlockPos pos) {
        List<BlockPos> cells = new ArrayList<>(7 + 4 * 51);
        cells.add(pos);
        for (Direction direction : Direction.values()) {
            cells.add(pos.relative(direction));
        }
        for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos column = pos.relative(side);
            for (int i = 1; i <= 40; i++) {
                cells.add(column.above(i));
            }
            for (int i = 1; i <= 10; i++) {
                cells.add(column.below(i));
            }
        }
        return cells;
    }

    /**
     * Breaks a block in the world and returns its drops, or empty if the block could not be broken.
     * Backwards-compatible wrapper around {@link #breakBlockAndGetDropsWithXp} that discards the XP.
     *
     * @param world  the server world
     * @param pos    the position of the block
     * @param tool   the tool to use for breaking (affects drop calculation)
     * @param owner  the machine's owner: the break is done as their fake player (see {@link MachineIdentity})
     * @return an Optional containing the list of dropped items, or empty if the break failed
     */
    public static Optional<List<ItemStack>> breakBlockAndGetDrops(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, @Nullable GameProfile owner) {
        return breakBlockAndGetDropsWithXp(world, pos, tool, owner).map(BreakResult::drops);
    }

    /** {@link #breakBlockAndGetDrops(ServerLevel, BlockPos, ItemStack, GameProfile)} by a player the caller already
     *  holds (a robot's leased fake player, mid-cycle). */
    public static Optional<List<ItemStack>> breakBlockAndGetDrops(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, Player breaker) {
        return breakBlockAndGetDropsWithXp(world, pos, tool, breaker).map(BreakResult::drops);
    }

    /**
     * Like {@link #breakBlockAndGetDrops} but also returns the XP the wielded tool would have
     * awarded. Callers spawn the orb wherever they want (e.g. the machine block instead of the
     * broken position) — this helper never creates an orb itself.
     *
     * <p>The break is done by the owner's leased fake player (the "[BuildCraft]" one for an unowned
     * or nameless owner, {@link MachineIdentity}): it is the breaker of the loot and XP context and
     * the cause of the block-destroy game event, so sculk sensors, loot modifiers and claim or log
     * mods see who broke the block, as 1.12.2's fake-player harvest did.
     */
    public static Optional<BreakResult> breakBlockAndGetDropsWithXp(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, @Nullable GameProfile owner) {
        if (world.getBlockState(pos).isAir()) {
            return Optional.of(new BreakResult(List.of(), 0, FluidStack.EMPTY));
        }
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(world, MachineIdentity.of(owner), pos)) {
            return breakBlockAndGetDropsWithXp(world, pos, tool, lease.player());
        }
    }

    /** {@link #breakBlockAndGetDropsWithXp(ServerLevel, BlockPos, ItemStack, GameProfile)} by a player the caller
     *  already holds. */
    public static Optional<BreakResult> breakBlockAndGetDropsWithXp(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, Player breaker) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return Optional.of(new BreakResult(List.of(), 0, FluidStack.EMPTY));
        }
        if (state.getDestroySpeed(world, pos) < 0) {
            return Optional.empty();
        }

        // Capture the support-cascade neighbourhood before the break so every block the removal
        // invalidated gets explicitly re-synced to clients afterwards (see
        // snapshotSupportsForResync — the "lumberjack leaves floating vines" client-ghost fix).
        Long2ObjectOpenHashMap<BlockState> supportsBefore = snapshotSupportsForResync(world, pos);

        // Snapshot the BlockEntity BEFORE destroying — getDrops and getExpDrop both read it
        // (sculk-family blocks compute XP off BE state, container blocks include contents in
        // drops, etc.) and `world.destroyBlock` below clears the BE as a side effect.
        BlockEntity be = world.getBlockEntity(pos);

        // Replicate vanilla's tier gate. NEEDS_STONE_TOOL / NEEDS_IRON_TOOL /
        // NEEDS_DIAMOND_TOOL block tags flip `requiresCorrectToolForDrops()` on; on the
        // player path, `Block.dropFromBlock` short-circuits to empty drops + 0 XP before
        // calling getDrops when the wielded item fails `isCorrectToolForDrops`. Vanilla
        // loot tables only carry `match_tool` for enchantment branches (silk_touch /
        // fortune) — they do NOT encode the tier check — so a raw `Block.getDrops` call
        // would drop raw_iron under a wooden pickaxe and obsidian under an iron pickaxe.
        // Each break-laser caller declares its tier via `getBreakingTool()`; this gate
        // enforces it (Mining Well iron, Quarry diamond, Builder iron, Filler iron,
        // Stripes diamond — obsidian and ancient debris destroy without dropping under
        // iron). 1.12.2's pipeline achieved the same via canHarvestBlock + harvestBlock
        // through a fake player wielding the tool.
        boolean tierGated = state.requiresCorrectToolForDrops()
                && !tool.isCorrectToolForDrops(state);

        // 6-arg getDrops so the loot context's TOOL parameter carries the wielded tool's
        // enchantments — silk_touch returns the block itself, fortune multiplies ore drops.
        // The 4-arg overload defaults TOOL to ItemStack.EMPTY and loses both. The breaker is
        // the loot context's THIS_ENTITY, as a player's own break would be.
        List<ItemStack> drops = tierGated
                ? new ArrayList<>()
                : new ArrayList<>(Block.getDrops(state, world, pos, be, breaker, tool));

        // XP via NeoForge's IBlockStateExtension.getExpDrop — same tool/breaker shape as
        // getDrops above. Zeroed under a tier-gated break to parallel the empty drops list
        // (no harvest, no XP), matching the order vanilla `Block.dropFromBlock` uses.
        int xp = tierGated ? 0 : state.getExpDrop(world, pos, be, breaker, tool);

        // Pure-fluid blocks (LiquidBlock instances — Blocks.WATER, Blocks.LAVA, BC oil/fuel,
        // etc.) need a hard setBlock(AIR), NOT world.destroyBlock(). Vanilla destroyBlock calls
        // setBlock(pos, fluidState.createLegacyBlock(), 3) — for a position whose only "block"
        // IS the fluid, createLegacyBlock returns the same fluid block, so destroyBlock is a
        // silent no-op. The Builder's CLEAR-mode break tasks were "succeeding" against water
        // (Optional.of(emptyList) returned, task removed from queue) but the water never
        // actually disappeared, producing the user-visible "laser fires, water stays, beam
        // re-queues forever" loop. For non-fluid blocks we keep destroyBlock so its sound /
        // particle / level-event side effects run; waterlogged blocks (block != LiquidBlock,
        // fluid via WATERLOGGED) are handled correctly by destroyBlock — the legacy-fluid
        // restore is the right call there because we're breaking the block, not the fluid.
        // Capture sourced fluid before destroying. Source-only because flowing fluid is a
        // transient steady-state expression of nearby sources — it has no "bucket volume" we
        // can meaningfully extract. Callers that care (Builder under CLEAR) absorb this into
        // their tanks; callers that don't (Quarry / Mining Well / Stripes / Filler) leave it
        // in the result and let it fall on the floor.
        FluidStack capturedFluid = FluidStack.EMPTY;
        if (state.getBlock() instanceof LiquidBlock) {
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty() && fluidState.isSource()) {
                capturedFluid = new FluidStack(fluidState.getType(), 1000);
            }
        }

        if (state.getBlock() instanceof LiquidBlock) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } else {
            world.destroyBlock(pos, false, breaker);
        }
        resyncChangedBlocks(world, supportsBefore);

        return Optional.of(new BreakResult(drops, xp, capturedFluid));
    }
}
