/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.mj.MjAPI;

import buildcraft.core.BCCoreConfig;

public class BlockUtil {
    /** Mining multiplier, set by BCCoreConfig on init. Default 1.0. */
    public static double miningMultiplier = 1.0;

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

    /** Fallback profile used when a mining tile has no recorded owner (e.g. set-block, worldgen).
     *  Same UUID seed and display name as BCCore's BC_PROFILE so protection mods see one identity. */
    private static final GameProfile MACHINE_FAKE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("BuildCraft".getBytes(StandardCharsets.UTF_8)),
            "[BuildCraft]"
    );

    /**
     * Returns true if a BuildCraft mining machine is permitted to break the block at {@code pos}.
     * <p>
     * If {@link BCCoreConfig#minePlayerProtected} is true, always returns true (the option is
     * an "override protection" toggle). Otherwise, posts the running version's block-break event with an
     * owner-bound FakePlayer positioned at {@code pos} and returns {@code !event.isCanceled()},
     * so third-party protection mods (FTB Chunks, GriefPrevention, OpenPartiesAndClaims, server
     * protection plugins) can gate the break by cancelling the event.
     */
    public static boolean canMachineBreak(ServerLevel level, BlockPos pos, GameProfile owner) {
        if (BCCoreConfig.minePlayerProtected.get()) {
            return true;
        }
        GameProfile profile = (owner != null && GameProfileUtil.getName(owner) != null) ? owner : MACHINE_FAKE_PROFILE;
        Player fp = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, profile, pos);
        BlockState state = level.getBlockState(pos);
        return BreakEventCompat.canBreak(level, pos, state, fp);
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
     * Drains a fluid source block from the world.
     *
     * @param world    the level
     * @param pos      the position to drain
     * @param doDrain  if true, actually removes the block; if false, simulates only
     * @return a FluidStack of 1000mB if a source block was present, or null
     */
    @Nullable
    public static FluidStack drainBlock(Level world, BlockPos pos, boolean doDrain) {
        FluidState fluidState = world.getFluidState(pos);
        if (fluidState.isEmpty() || !fluidState.isSource()) {
            return null;
        }
        Fluid fluid = fluidState.getType();
        if (doDrain) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
     * Breaks a block in the world and returns its drops, or empty if the block could not be broken.
     * Backwards-compatible wrapper around {@link #breakBlockAndGetDropsWithXp} that discards the XP.
     *
     * @param world  the server world
     * @param pos    the position of the block
     * @param tool   the tool to use for breaking (affects drop calculation)
     * @param owner  the player profile responsible for the break
     * @return an Optional containing the list of dropped items, or empty if the break failed
     */
    public static Optional<List<ItemStack>> breakBlockAndGetDrops(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, GameProfile owner) {
        return breakBlockAndGetDropsWithXp(world, pos, tool, owner).map(BreakResult::drops);
    }

    /**
     * Like {@link #breakBlockAndGetDrops} but also returns the XP the wielded tool would have
     * awarded. Callers spawn the orb wherever they want (e.g. the machine block instead of the
     * broken position) — this helper never creates an orb itself.
     */
    public static Optional<BreakResult> breakBlockAndGetDropsWithXp(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, GameProfile owner) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return Optional.of(new BreakResult(List.of(), 0, FluidStack.EMPTY));
        }
        if (state.getDestroySpeed(world, pos) < 0) {
            return Optional.empty();
        }

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
        // The 4-arg overload defaults TOOL to ItemStack.EMPTY and loses both. Entity is
        // null — none of our machines are "the breaker" in a way the loot context cares
        // about (THIS_ENTITY is only read by entity-specific drop rules like player kills).
        List<ItemStack> drops = tierGated
                ? new ArrayList<>()
                : new ArrayList<>(Block.getDrops(state, world, pos, be, (Entity) null, tool));

        // XP via NeoForge's IBlockStateExtension.getExpDrop — same tool/breaker shape as
        // getDrops above. Zeroed under a tier-gated break to parallel the empty drops list
        // (no harvest, no XP), matching the order vanilla `Block.dropFromBlock` uses.
        int xp = tierGated ? 0 : state.getExpDrop(world, pos, be, (Entity) null, tool);

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
            world.destroyBlock(pos, false);
        }

        return Optional.of(new BreakResult(drops, xp, capturedFluid));
    }
}
