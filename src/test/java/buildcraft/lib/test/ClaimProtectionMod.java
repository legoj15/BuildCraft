/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.Platform;
import buildcraft.platform.event.PlatformProtection;

/**
 * A stand-in for a claim/protection mod: a break listener that records every block-break event at one position and,
 * when {@code refuse} is set, cancels them all — exactly how claim mods refuse a machine's break probe. Register it
 * in a try-with-resources so it is always unregistered, and filter on one position so concurrent tests in other arena
 * cells are never refused or counted.
 */
public final class ClaimProtectionMod implements AutoCloseable {

    /** The player each break event at the watched position was posted for, in order. */
    public final List<Player> askedBy = new ArrayList<>();
    /** Where that player stood (its block) when it asked, in order: the player is shared and moves on afterwards. */
    public final List<BlockPos> askedFrom = new ArrayList<>();
    private final PlatformProtection.Registration token;

    public ClaimProtectionMod(BlockPos pos, boolean refuse) {
        token = Platform.protection().onBreak(event -> {
            if (event.pos().equals(pos)) {
                Player asker = event.player();
                askedBy.add(asker);
                askedFrom.add(asker == null ? null : asker.blockPosition());
                if (refuse) {
                    event.cancel();
                }
            }
        });
    }

    /** How many times machines asked about the watched position. */
    public int asks() {
        return askedBy.size();
    }

    /** A genuine (not probe) break of {@code state} at {@code pos} by {@code player}, as a break listener sees one,
     *  delivered by nobody: for handing straight to a listener. */
    public static PlatformProtection.BlockBreak genuineBreak(Level level, BlockPos pos, BlockState state,
            Player player) {
        return new PlatformProtection.BlockBreak() {
            @Override
            public LevelAccessor level() {
                return level;
            }

            @Override
            public BlockPos pos() {
                return pos;
            }

            @Override
            public BlockState state() {
                return state;
            }

            @Override
            public Player player() {
                return player;
            }

            @Override
            public boolean isProbe() {
                return false;
            }

            @Override
            public void cancel() {}
        };
    }

    @Override
    public void close() {
        token.close();
    }
}
