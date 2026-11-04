/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.event;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * How a machine standing in for a player asks claim and protection mods for permission: the events a player's own
 * break, placement, swing and clicks post, posted for the machine's fake player so that whatever refuses the player
 * refuses the machine. Every method runs on the server thread, like every caller.
 *
 * <p><b>Probes.</b> {@link #canBreak} and {@link #canPlace} post a PROBE: an event asking about an act that never
 * happens, cancelled or not. BuildCraft's own listeners for those events must therefore not take one for a block
 * changing; {@link #isProbe} and {@link BlockBreak#isProbe} tell them apart.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 */
public interface PlatformProtection {

    /**
     * Whether {@code player} may break the block at {@code pos}: posts the break event a player's break would (a
     * probe; nothing is broken) and answers whether nothing refused it.
     */
    boolean canBreak(Level level, BlockPos pos, BlockState state, Player player);

    /**
     * Whether {@code player} may place a block filling {@code cells} (the first is the cell the block is placed in,
     * any others the cells it fills too, a bed's head or a door's top): posts the placement event a player's
     * placement would (a probe; nothing is placed) and answers whether nothing refused it. Each cell is "placed
     * against" the block below it, as a block set down on the floor of its cell is. Because nothing has been placed,
     * the event's placed state is the cell's current one.
     *
     * @throws IllegalArgumentException for no cells
     */
    boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player);

    /**
     * True while {@code event} (a loader event object, delivered to a listener) is a permission probe posted by
     * {@link #canBreak} or {@link #canPlace} rather than a block changing.
     */
    boolean isProbe(Object event);

    /**
     * Listens for a player's block break, probes included ({@link BlockBreak#isProbe}). The listener runs at the
     * loader's default order and is not told about a break something before it already refused.
     *
     * @return the handle that removes the listener again
     */
    Registration onBreak(Consumer<BlockBreak> listener);

    /**
     * Whether {@code attacker} may attack {@code target}: posts the event a player's own swing posts (which claim
     * mods' animal and PvE protection listen to) and answers whether nothing refused it. Nothing is hit, and the
     * held weapon's own left-click hook is not run.
     */
    boolean canAttack(Player attacker, Entity target);

    /**
     * Whether {@code player} harvests drops from {@code state} at {@code pos}: the right tool, as other mods may
     * decide it for that position.
     */
    boolean canHarvest(Player player, BlockState state, BlockGetter level, BlockPos pos);

    /**
     * Posts the event a player's right-click on the block {@code hit} names posts, before anything is used, and
     * answers what it decided about using the held item.
     */
    ClickVerdict rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit);

    /**
     * Posts the event a player's right-click in the air posts, before the item is used, and answers whether nothing
     * refused the use.
     */
    boolean canUseItem(Player player, InteractionHand hand);

    /** Records the block (state and block entity data) at {@code pos}, to ask {@link #canPlaceOver} about later, or
     *  to put back. */
    CapturedBlock capture(ServerLevel level, BlockPos pos);

    /**
     * Whether {@code placer} may keep the block it has just placed where {@code replaced} was (captured before the
     * placement), placed against the face {@code face} names (the block behind it, as seen from the new block, is
     * in {@code face.getOpposite()}): posts the placement event a player's placement posts once the block is set
     * down. Not a probe: the block is there.
     */
    boolean canPlaceOver(Entity placer, CapturedBlock replaced, Direction face);

    /** What the right-click-block event decided about the held item ({@link #rightClickBlock}). */
    enum ClickVerdict {
        /** Something refused the click: the item is not used. */
        REFUSED,
        /** Nothing decided: the item is used if vanilla's own gate (an empty stack, a cooldown) lets it. */
        DEFAULT,
        /** Something forced the item use: vanilla's gate is skipped. */
        FORCE_ITEM
    }

    /** A block break as a listener sees it ({@link #onBreak}). */
    interface BlockBreak {
        LevelAccessor level();

        BlockPos pos();

        /** The state being broken. */
        BlockState state();

        /** The player breaking the block. */
        Player player();

        /** True when this is a permission probe ({@link #canBreak}) rather than a block breaking. */
        boolean isProbe();

        /** Refuses the break: what a protection mod does. */
        void cancel();
    }

    /** A block recorded by {@link #capture}. */
    interface CapturedBlock {
        BlockPos pos();

        /** The recorded state. */
        BlockState state();

        /** Puts the recorded state and block entity data back with these {@code setBlock} flags; false if the level
         *  refused the state. */
        boolean restore(int flags);
    }

    /** A listener registration; {@link #close} removes the listener, and closing twice is harmless. */
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
