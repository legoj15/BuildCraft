/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersConfig;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil.Session;

/**
 * A Quarry placed beside a connected marker volume takes it as its area, and taking it breaks every marker of the
 * connection (they pop as items). It asks claim and protection mods first, as its owner, about each of those markers:
 * refused anywhere, it leaves the markers standing and connected, drops nothing, and lays out its default area. Both
 * ways it finds the markers are covered: a marker in the cell behind it, and a volume it stands on the edge of with
 * an empty cell behind it. Allowed, it still takes them.
 *
 * <p>Each Quarry lays out an area that reaches far outside the cell, so each is removed in the tick it was placed
 * in, before it ever ticks (no frame is built, no rig spawned).
 */
public class TileQuarryMarkerProtectionTester {

    /** Generous wait for the markers to join the cache (they do on the first level tick); counted from the cell
     *  ticking, so the manifest's max_ticks outlasts {@code EntityArenaUtil.CELL_READY_TICKS + REGISTER_TICKS}. */
    private static final int REGISTER_TICKS = 40;

    private static final BlockPos MARKER_1 = new BlockPos(1, 2, 1);
    /** The far marker: no Quarry stands against it, so only an ask about every marker the taking breaks reaches it. */
    private static final BlockPos MARKER_2 = new BlockPos(4, 2, 1);
    private static final BlockPos MARKER_3 = new BlockPos(1, 2, 4);
    /** The box the three markers connect into. */
    private static final BlockPos BOX_MAX = new BlockPos(4, 2, 4);

    public static void quarryLeavesMarkersItMayNotBreak(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        // Both face west, so their backs are to the east: the first Quarry's back is MARKER_1, the second's the empty
        // cell (1,2,2) inside the volume, whose west edge it stands on.
        BlockPos[] quarryRels = {new BlockPos(0, 2, 1), new BlockPos(0, 2, 2)};
        String[] names = {"a Quarry against a marker", "a Quarry beside the marker volume"};
        arena.guard(() -> {
            arena.place(MARKER_1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(MARKER_2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(MARKER_3, BCCoreBlocks.MARKER_VOLUME.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, MARKER_1)
                && arena.isRegistered(VolumeCache.INSTANCE, MARKER_2)
                && arena.isRegistered(VolumeCache.INSTANCE, MARKER_3), () -> {
                ServerLevel level = helper.getLevel();
                BlockPos[] markers = {arena.abs(MARKER_1), arena.abs(MARKER_2), arena.abs(MARKER_3)};
                Set<Integer> itemsBefore = new HashSet<>();
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                    itemsBefore.add(item.getId());
                }
                try {
                    check(!BCCoreConfig.minePlayerProtected.get(),
                        "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
                    if (!(level.getBlockEntity(markers[0]) instanceof TileMarkerVolume volume)) {
                        throw new IllegalStateException("no volume marker tile at " + MARKER_1);
                    }
                    volume.onManualConnectionAttempt(null);
                    check(volume.max().equals(arena.abs(BOX_MAX)),
                        "precondition: the markers did not connect into a box");
                    Player placer = helper.makeMockPlayer(GameType.SURVIVAL);

                    // Refused at the far marker: neither Quarry takes the volume.
                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[1], true)) {
                        for (int i = 0; i < quarryRels.length; i++) {
                            int asksBefore = claim.asks();
                            TileQuarry quarry = placeQuarry(helper, quarryRels[i], placer);
                            try {
                                BlockPos q = arena.abs(quarryRels[i]);
                                int top = q.getY() + Math.max(4, BCBuildersConfig.quarryFrameMinHeight.get());
                                checkFrame(quarry, q.offset(1, 0, -5), new BlockPos(q.getX() + 11, top, q.getZ() + 5),
                                    names[i] + " refused one of the markers must lay out its default area");
                                for (BlockPos marker : markers) {
                                    check(level.getBlockState(marker).is(BCCoreBlocks.MARKER_VOLUME.get()),
                                        names[i] + " refused one of the markers broke the marker at "
                                            + arena.rel(marker));
                                }
                                VolumeConnection connection = volume.getCurrentConnection();
                                check(connection != null && connection.getMarkerPositions().size() == 3,
                                    "after " + names[i] + " the markers must still be connected, got "
                                        + (connection == null ? "no connection" : connection.getMarkerPositions()));
                                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                                        EntityArenaUtil.cellBounds(helper))) {
                                    check(itemsBefore.contains(item.getId()),
                                        names[i] + " refused one of the markers dropped " + item.getItem());
                                }
                                // One ask per placement: a refused volume is not asked about again for each of its
                                // markers the Quarry comes across.
                                check(claim.asks() == asksBefore + 1, names[i] + " must ask about the far marker once, "
                                    + "asked " + (claim.asks() - asksBefore) + " times");
                                checkAskedAs(claim, placer, names[i]);
                            } finally {
                                helper.setBlock(quarryRels[i], Blocks.AIR);
                            }
                        }
                    }

                    // Allowed: the Quarry beside the volume takes it, and the markers go.
                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[1], false)) {
                        TileQuarry quarry = placeQuarry(helper, quarryRels[1], placer);
                        try {
                            int y = arena.abs(quarryRels[1]).getY();
                            BlockPos min = arena.abs(MARKER_1);
                            BlockPos max = arena.abs(BOX_MAX);
                            int top = Math.max(max.getY(), y + BCBuildersConfig.quarryFrameMinHeight.get());
                            checkFrame(quarry, new BlockPos(min.getX(), y, min.getZ()),
                                new BlockPos(max.getX(), top, max.getZ()),
                                names[1] + " allowed to break the markers must take their volume");
                            for (BlockPos marker : markers) {
                                check(level.getBlockState(marker).isAir(),
                                    names[1] + " took the volume but left the marker at " + arena.rel(marker));
                            }
                            check(claim.asks() >= 1, names[1] + " must ask about the far marker before breaking it");
                            checkAskedAs(claim, placer, names[1]);
                        } finally {
                            helper.setBlock(quarryRels[1], Blocks.AIR);
                        }
                    }
                } finally {
                    // Never leave a Quarry or a dropped marker behind, whatever failed.
                    for (BlockPos rel : quarryRels) {
                        helper.setBlock(rel, Blocks.AIR);
                    }
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                            EntityArenaUtil.cellBounds(helper))) {
                        if (!itemsBefore.contains(item.getId())) {
                            item.discard();
                        }
                    }
                }
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    /** Places a west-facing Quarry at {@code rel} the way a player does: {@code placer} becomes its owner, then it
     *  lays out its area. */
    private static TileQuarry placeQuarry(GameTestHelper helper, BlockPos rel, Player placer) {
        helper.setBlock(rel, BCBuildersBlocks.QUARRY.get().defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof TileQuarry quarry)) {
            throw new IllegalStateException("no Quarry tile at " + rel);
        }
        quarry.onPlacedBy(placer, ItemStack.EMPTY);
        return quarry;
    }

    /** The last ask the claim saw came from the Quarry's owner, {@code placer}. */
    private static void checkAskedAs(ClaimProtectionMod claim, Player placer, String who) {
        Player asker = claim.askedBy.get(claim.asks() - 1);
        check(asker != null && GameProfileUtil.getId(placer.getGameProfile()).equals(asker.getUUID()),
            who + " must ask as its owner " + GameProfileUtil.getName(placer.getGameProfile()) + ", asked as "
                + (asker == null ? "nobody" : asker.getGameProfile()));
    }

    private static void checkFrame(TileQuarry quarry, BlockPos min, BlockPos max, String what) {
        check(quarry.frameBox.isInitialized() && quarry.frameBox.min().equals(min) && quarry.frameBox.max().equals(max),
            what + ": expected " + min + " to " + max + ", got " + quarry.frameBox);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
