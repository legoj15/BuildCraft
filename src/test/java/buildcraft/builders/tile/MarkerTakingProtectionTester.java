/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IPathProvider;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersConfig;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil.Session;
import buildcraft.lib.tile.AbstractBCBlockEntity;
import buildcraft.lib.tile.TileMarker;

/**
 * A machine placed against connected markers takes them: a Quarry, Filler or Architect Table a marker volume, a
 * Builder a marker path. Taking them breaks every marker of the connection (they pop as items), so the machine asks
 * claim and protection mods first, as its owner, about each of those markers. Refused anywhere, it leaves the markers
 * standing and connected, drops nothing, and is what it is with no markers there. Allowed, it still takes them.
 *
 * <p>The claim always refuses the far marker, one no machine stands against, so only an ask about every marker the
 * taking breaks reaches it. What stands behind a machine need not be one of this mod's markers: another mod's block
 * entity may offer an area or a path, and is asked about too ({@link #machinesAskBeforeTakingAnotherModsProvider}).
 */
public class MarkerTakingProtectionTester {

    /** Generous wait for the markers to join the cache (they do on the first level tick); counted from the cell
     *  ticking, so the manifest's max_ticks outlasts {@code EntityArenaUtil.CELL_READY_TICKS + REGISTER_TICKS}. */
    private static final int REGISTER_TICKS = 40;

    private static final BlockPos MARKER_1 = new BlockPos(1, 2, 1);
    /** The far volume marker. */
    private static final BlockPos MARKER_2 = new BlockPos(4, 2, 1);
    private static final BlockPos MARKER_3 = new BlockPos(1, 2, 4);
    /** The box the three volume markers connect into. */
    private static final BlockPos BOX_MAX = new BlockPos(4, 2, 4);

    /** A path of three markers, from the one behind the Builder to the far one, {@link #PATH_3}. */
    private static final BlockPos PATH_1 = new BlockPos(1, 2, 1);
    private static final BlockPos PATH_2 = new BlockPos(4, 2, 1);
    private static final BlockPos PATH_3 = new BlockPos(4, 2, 4);

    /**
     * Both ways a Quarry finds a marker volume are covered: a marker in the cell behind it, and a volume it stands on
     * the edge of with an empty cell behind it. Each Quarry lays out an area that reaches far outside the cell, so each
     * is removed in the tick it was placed in, before it ever ticks (no frame is built, no rig spawned).
     */
    public static void quarryLeavesMarkersItMayNotBreak(GameTestHelper helper) {
        // Both face west, so their backs are to the east: the first Quarry's back is MARKER_1, the second's the empty
        // cell (1,2,2) inside the volume, whose west edge it stands on.
        BlockPos[] quarryRels = {new BlockPos(0, 2, 1), new BlockPos(0, 2, 2)};
        String[] names = {"a Quarry against a marker", "a Quarry beside the marker volume"};
        withVolume(helper, rig -> {
            ServerLevel level = helper.getLevel();
            try {
                // Refused at the far marker: neither Quarry takes the volume.
                try (ClaimProtectionMod claim = new ClaimProtectionMod(rig.markers[1], true)) {
                    for (int i = 0; i < quarryRels.length; i++) {
                        int asksBefore = claim.asks();
                        TileQuarry quarry = place(helper, BCBuildersBlocks.QUARRY.get(), quarryRels[i], Direction.WEST,
                            TileQuarry.class);
                        quarry.onPlacedBy(rig.placer, ItemStack.EMPTY);
                        try {
                            BlockPos q = rig.arena.abs(quarryRels[i]);
                            int top = q.getY() + Math.max(4, BCBuildersConfig.quarryFrameMinHeight.get());
                            checkFrame(quarry, q.offset(1, 0, -5), new BlockPos(q.getX() + 11, top, q.getZ() + 5),
                                names[i] + " refused one of the markers must lay out its default area");
                            rig.checkLeftAlone(names[i]);
                            // One ask per placement: a refused volume is not asked about again for each of its
                            // markers the Quarry comes across.
                            check(claim.asks() == asksBefore + 1, names[i] + " must ask about the far marker once, "
                                + "asked " + (claim.asks() - asksBefore) + " times");
                            checkAskedAs(claim, rig.placer, names[i]);
                        } finally {
                            helper.setBlock(quarryRels[i], Blocks.AIR);
                        }
                    }
                }

                // Allowed: the Quarry beside the volume takes it, and the markers go.
                try (ClaimProtectionMod claim = new ClaimProtectionMod(rig.markers[1], false)) {
                    TileQuarry quarry = place(helper, BCBuildersBlocks.QUARRY.get(), quarryRels[1], Direction.WEST,
                        TileQuarry.class);
                    quarry.onPlacedBy(rig.placer, ItemStack.EMPTY);
                    try {
                        int y = rig.arena.abs(quarryRels[1]).getY();
                        BlockPos min = rig.arena.abs(MARKER_1);
                        BlockPos max = rig.arena.abs(BOX_MAX);
                        int top = Math.max(max.getY(), y + BCBuildersConfig.quarryFrameMinHeight.get());
                        checkFrame(quarry, new BlockPos(min.getX(), y, min.getZ()),
                            new BlockPos(max.getX(), top, max.getZ()),
                            names[1] + " allowed to break the markers must take their volume");
                        rig.checkTaken(names[1], claim);
                    } finally {
                        helper.setBlock(quarryRels[1], Blocks.AIR);
                    }
                }
            } finally {
                for (BlockPos rel : quarryRels) {
                    helper.setBlock(rel, Blocks.AIR);
                }
            }
        });
    }

    /** A Filler refused the markers has no box, as with no markers behind it, and owns no volume box. */
    public static void fillerLeavesMarkersItMayNotBreak(GameTestHelper helper) {
        // Facing west, so the Filler's back (where it looks for an area) is MARKER_1.
        BlockPos fillerRel = new BlockPos(0, 2, 1);
        withVolume(helper, rig -> {
            LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(helper.getLevel());
            BlockPos fillerAbs = rig.arena.abs(fillerRel);
            try {
                try (ClaimProtectionMod claim = new ClaimProtectionMod(rig.markers[1], true)) {
                    TileFiller filler = place(helper, BCBuildersBlocks.FILLER.get(), fillerRel, Direction.WEST,
                        TileFiller.class);
                    filler.onPlacedBy(rig.placer, ItemStack.EMPTY);
                    // getBox() throws on a Filler with no box, so the message names it only when there is one.
                    check(!filler.hasBox(), "a Filler refused one of the markers must have no box, got "
                        + (filler.hasBox() ? filler.getBox() : "none"));
                    check(boxes.getOwnedBy(fillerAbs) == null,
                        "a Filler refused one of the markers must own no volume box");
                    rig.checkLeftAlone("a Filler");
                    check(claim.asks() == 1, "a Filler must ask about the far marker once, asked " + claim.asks()
                        + " times");
                    checkAskedAs(claim, rig.placer, "a Filler");
                    helper.setBlock(fillerRel, Blocks.AIR);
                }

                try (ClaimProtectionMod claim = new ClaimProtectionMod(rig.markers[1], false)) {
                    TileFiller filler = place(helper, BCBuildersBlocks.FILLER.get(), fillerRel, Direction.WEST,
                        TileFiller.class);
                    filler.onPlacedBy(rig.placer, ItemStack.EMPTY);
                    check(filler.hasBox() && filler.getBox().min().equals(rig.arena.abs(MARKER_1))
                            && filler.getBox().max().equals(rig.arena.abs(BOX_MAX)),
                        "a Filler allowed to break the markers must take their volume, got "
                            + (filler.hasBox() ? filler.getBox() : "no box"));
                    check(boxes.getOwnedBy(fillerAbs) != null,
                        "a Filler that took the markers must own the volume box they become");
                    rig.checkTaken("a Filler", claim);
                }
            } finally {
                helper.setBlock(fillerRel, Blocks.AIR);
                if (boxes.volumeBoxes.removeIf(box -> box.isOwnedBy(fillerAbs) || box.box.contains(rig.markers[0]))) {
                    boxes.markDirtyAndBroadcast();
                }
            }
        });
    }

    /** An Architect Table refused the markers is not valid, as with no markers behind it, and owns no volume box. */
    public static void architectTableLeavesMarkersItMayNotBreak(GameTestHelper helper) {
        // Facing north, so the table's back (where it looks for an area) is MARKER_1.
        BlockPos tableRel = new BlockPos(1, 2, 0);
        withVolume(helper, rig -> {
            LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(helper.getLevel());
            BlockPos tableAbs = rig.arena.abs(tableRel);
            try {
                try (ClaimProtectionMod claim = new ClaimProtectionMod(rig.markers[1], true)) {
                    TileArchitectTable table = place(helper, BCBuildersBlocks.ARCHITECT.get(), tableRel,
                        Direction.NORTH, TileArchitectTable.class);
                    table.onPlacedBy(rig.placer, ItemStack.EMPTY);
                    check(!table.getIsValid(), "an Architect Table refused one of the markers must not be valid");
                    check(boxes.getOwnedBy(tableAbs) == null,
                        "an Architect Table refused one of the markers must own no volume box");
                    rig.checkLeftAlone("an Architect Table");
                    check(claim.asks() == 1, "an Architect Table must ask about the far marker once, asked "
                        + claim.asks() + " times");
                    checkAskedAs(claim, rig.placer, "an Architect Table");
                    helper.setBlock(tableRel, Blocks.AIR);
                }

                try (ClaimProtectionMod claim = new ClaimProtectionMod(rig.markers[1], false)) {
                    TileArchitectTable table = place(helper, BCBuildersBlocks.ARCHITECT.get(), tableRel,
                        Direction.NORTH, TileArchitectTable.class);
                    table.onPlacedBy(rig.placer, ItemStack.EMPTY);
                    check(table.getIsValid(), "an Architect Table allowed to break the markers must be valid");
                    check(boxes.getOwnedBy(tableAbs) != null,
                        "an Architect Table that took the markers must own the volume box they become");
                    rig.checkTaken("an Architect Table", claim);
                }
            } finally {
                helper.setBlock(tableRel, Blocks.AIR);
                if (boxes.volumeBoxes.removeIf(box -> box.isOwnedBy(tableAbs) || box.box.contains(rig.markers[0]))) {
                    boxes.markDirtyAndBroadcast();
                }
            }
        });
    }

    /** A Builder refused the path markers has no path, as with no markers behind it. */
    public static void builderLeavesPathMarkersItMayNotBreak(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        // Facing west, so the Builder's back (where it looks for a path) is PATH_1.
        BlockPos builderRel = new BlockPos(0, 2, 1);
        arena.guard(() -> {
            arena.place(PATH_1, BCCoreBlocks.MARKER_PATH.get());
            arena.place(PATH_2, BCCoreBlocks.MARKER_PATH.get());
            arena.place(PATH_3, BCCoreBlocks.MARKER_PATH.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(PathCache.INSTANCE, PATH_1)
                && arena.isRegistered(PathCache.INSTANCE, PATH_2)
                && arena.isRegistered(PathCache.INSTANCE, PATH_3), () -> {
                ServerLevel level = helper.getLevel();
                BlockPos[] markers = {arena.abs(PATH_1), arena.abs(PATH_2), arena.abs(PATH_3)};
                Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
                try {
                    check(!BCCoreConfig.minePlayerProtected.get(),
                        "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
                    if (!(level.getBlockEntity(markers[0]) instanceof TileMarkerPath path)) {
                        throw new IllegalStateException("no path marker tile at " + PATH_1);
                    }
                    path.getCache().getSubCache(level).tryConnect(markers[0], markers[1]);
                    path.getCache().getSubCache(level).tryConnect(markers[1], markers[2]);
                    check(path.getPath().size() == 3, "precondition: the path markers did not connect, got "
                        + path.getPath());
                    Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
                    Rig rig = new Rig(helper, arena, markers, BCCoreBlocks.MARKER_PATH.get(), path, placer,
                        itemsBefore);

                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[2], true)) {
                        TileBuilder builder = place(helper, BCBuildersBlocks.BUILDER.get(), builderRel,
                            Direction.WEST, TileBuilder.class);
                        builder.onPlacedBy(placer, ItemStack.EMPTY);
                        check(builder.path == null, "a Builder refused one of the path markers must have no path, got "
                            + builder.path);
                        rig.checkLeftAlone("a Builder");
                        check(claim.asks() == 1, "a Builder must ask about the far path marker once, asked "
                            + claim.asks() + " times");
                        checkAskedAs(claim, placer, "a Builder");
                        helper.setBlock(builderRel, Blocks.AIR);
                    }

                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[2], false)) {
                        TileBuilder builder = place(helper, BCBuildersBlocks.BUILDER.get(), builderRel,
                            Direction.WEST, TileBuilder.class);
                        builder.onPlacedBy(placer, ItemStack.EMPTY);
                        check(builder.path != null && builder.path.size() == 3
                                && builder.path.containsAll(List.of(markers)),
                            "a Builder allowed to break the path markers must take their path, got " + builder.path);
                        rig.checkTaken("a Builder", claim);
                    }
                } finally {
                    helper.setBlock(builderRel, Blocks.AIR);
                    EntityArenaUtil.discardNewItems(helper, itemsBefore);
                }
                arena.finish();
            }, "Path markers never registered in the cache");
        });
    }

    /**
     * A Builder PLACED against a CLOSED loop of path markers takes the loop's path, and asks about each marker of it
     * once. A looped connection lists its first marker twice (the closing segment back to it), so the loop's FIRST
     * marker is the one a naive list of the positions to remove would ask about twice; the claim watches that marker
     * and must see exactly one ask. The path the Builder holds keeps the closing position (its first again), which
     * its base positions are laid out along.
     */
    public static void builderTakesLoopedPathAskingOnce(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        // Facing west, so the Builder's back (where it looks for a path) is PATH_1, the loop's first marker.
        BlockPos builderRel = new BlockPos(0, 2, 1);
        arena.guard(() -> {
            arena.place(PATH_1, BCCoreBlocks.MARKER_PATH.get());
            arena.place(PATH_2, BCCoreBlocks.MARKER_PATH.get());
            arena.place(PATH_3, BCCoreBlocks.MARKER_PATH.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(PathCache.INSTANCE, PATH_1)
                && arena.isRegistered(PathCache.INSTANCE, PATH_2)
                && arena.isRegistered(PathCache.INSTANCE, PATH_3), () -> {
                ServerLevel level = helper.getLevel();
                BlockPos[] markers = {arena.abs(PATH_1), arena.abs(PATH_2), arena.abs(PATH_3)};
                Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
                try {
                    check(!BCCoreConfig.minePlayerProtected.get(),
                        "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
                    if (!(level.getBlockEntity(markers[0]) instanceof TileMarkerPath path)) {
                        throw new IllegalStateException("no path marker tile at " + PATH_1);
                    }
                    path.getCache().getSubCache(level).tryConnect(markers[0], markers[1]);
                    path.getCache().getSubCache(level).tryConnect(markers[1], markers[2]);
                    // Connecting the last marker back to the first closes the loop: the connection's positions run
                    // around it and name the first marker again at the end, as the segment that closes it.
                    path.getCache().getSubCache(level).tryConnect(markers[2], markers[0]);
                    List<BlockPos> loop = path.getPath();
                    check(loop.size() == 4 && loop.get(0).equals(markers[0]) && loop.get(3).equals(markers[0]),
                        "precondition: the path markers did not close into a loop, got " + loop);
                    Player placer = helper.makeMockPlayer(GameType.SURVIVAL);

                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[0], false)) {
                        TileBuilder builder = place(helper, BCBuildersBlocks.BUILDER.get(), builderRel,
                            Direction.WEST, TileBuilder.class);
                        builder.onPlacedBy(placer, ItemStack.EMPTY);
                        check(builder.path != null && builder.path.size() == 4
                                && builder.path.get(0).equals(markers[0]) && builder.path.get(3).equals(markers[0]),
                            "a Builder placed at a closed loop must take its path, first position last to close it, "
                                + "got " + builder.path);
                        for (BlockPos marker : markers) {
                            check(level.getBlockState(marker).isAir(),
                                "the Builder took the loop but left the marker at " + arena.rel(marker));
                        }
                        check(claim.asks() == 1, "a Builder must ask about the loop's first marker exactly once "
                            + "(a loop names it twice, once to break it), asked " + claim.asks() + " times");
                        checkAskedAs(claim, placer, "a Builder");
                    }
                } finally {
                    helper.setBlock(builderRel, Blocks.AIR);
                    EntityArenaUtil.discardNewItems(helper, itemsBefore);
                }
                arena.finish();
            }, "Path markers never registered in the cache");
        });
    }

    /** A block entity of another mod that offers an area and a path through the API, standing where a lectern is (a
     *  vanilla block entity type with no ticker, that nothing reads and anyone may break). It only records being
     *  taken. */
    private static final class ForeignProvider extends BlockEntity implements IAreaProvider, IPathProvider {
        private final BlockPos areaMax;
        private final BlockPos pathEnd;
        boolean removed;

        /** {@code type} is the type of the block entity it replaces (the vanilla constants moved in 26.2). */
        ForeignProvider(BlockEntityType<?> type, BlockPos pos, BlockState state, BlockPos areaMax, BlockPos pathEnd) {
            super(type, pos, state);
            this.areaMax = areaMax;
            this.pathEnd = pathEnd;
        }

        @Override
        public BlockPos min() {
            return getBlockPos();
        }

        @Override
        public BlockPos max() {
            return areaMax;
        }

        @Override
        public List<BlockPos> getPath() {
            return List.of(getBlockPos(), pathEnd);
        }

        @Override
        public void removeFromWorld() {
            removed = true;
        }
    }

    /** One of the four machines, and whether it holds what the provider behind it offered. */
    private record Taker(String who, Supplier<? extends Block> block, Predicate<BlockEntity> tookIt) {}

    /**
     * The API lets another mod's block entity offer a machine its area or path, and {@code removeFromWorld} is that
     * mod's own code: the one block this mod knows it removes is the provider itself. Each of the four machines asks
     * about that block, as its owner, before it takes such a provider; refused, the provider is not removed and the
     * machine is what it is with nothing behind it, and allowed, it takes what the provider offers.
     */
    public static void machinesAskBeforeTakingAnotherModsProvider(GameTestHelper helper) {
        // Facing west, so each machine's back (where it looks for an area or a path) is the provider.
        BlockPos machineRel = new BlockPos(0, 2, 1);
        BlockPos providerRel = new BlockPos(1, 2, 1);
        ServerLevel level = helper.getLevel();
        BlockPos machineAbs = helper.absolutePos(machineRel);
        BlockPos providerAbs = helper.absolutePos(providerRel);
        LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
        // A Quarry's default area starts five blocks to the side of it; the provider's starts in the Quarry's row.
        List<Taker> takers = List.of(
            new Taker("a Quarry", BCBuildersBlocks.QUARRY::get, tile -> ((TileQuarry) tile).frameBox.isInitialized()
                && ((TileQuarry) tile).frameBox.min().getZ() == providerAbs.getZ()),
            new Taker("a Filler", BCBuildersBlocks.FILLER::get, tile -> ((TileFiller) tile).hasBox()),
            new Taker("an Architect Table", BCBuildersBlocks.ARCHITECT::get,
                tile -> ((TileArchitectTable) tile).getIsValid()),
            new Taker("a Builder", BCBuildersBlocks.BUILDER::get, tile -> ((TileBuilder) tile).path != null));
        Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
        try {
            check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            helper.setBlock(providerRel, Blocks.LECTERN);
            BlockEntity lectern = level.getBlockEntity(providerAbs);
            check(lectern != null, "precondition: the lectern has no block entity to stand in for");
            ForeignProvider provider = new ForeignProvider(lectern.getType(), providerAbs,
                level.getBlockState(providerAbs), helper.absolutePos(new BlockPos(4, 2, 4)),
                helper.absolutePos(new BlockPos(4, 2, 1)));
            level.setBlockEntity(provider);
            check(level.getBlockEntity(providerAbs) == provider, "precondition: the provider is not in the world");
            Player placer = helper.makeMockPlayer(GameType.SURVIVAL);

            for (Taker taker : takers) {
                for (boolean refuse : new boolean[] {true, false}) {
                    String who = taker.who() + (refuse ? " refused" : " allowed") + " another mod's provider";
                    provider.removed = false;
                    try (ClaimProtectionMod claim = new ClaimProtectionMod(providerAbs, refuse)) {
                        // Removed in the tick it was placed in, before it ever ticks (a Quarry builds no frame).
                        AbstractBCBlockEntity machine = place(helper, taker.block().get(), machineRel, Direction.WEST,
                            AbstractBCBlockEntity.class);
                        machine.onPlacedBy(placer, ItemStack.EMPTY);
                        check(claim.asks() == 1, who + " must ask about the provider's block once, asked "
                            + claim.asks() + " times");
                        checkAskedAs(claim, placer, who);
                        check(provider.removed == !refuse, who + (refuse ? " must not remove it" : " must remove it"));
                        check(taker.tookIt().test(machine) == !refuse,
                            who + (refuse ? " must hold nothing of it" : " must hold what it offered"));
                    } finally {
                        helper.setBlock(machineRel, Blocks.AIR);
                        if (boxes.volumeBoxes.removeIf(box -> box.isOwnedBy(machineAbs)
                                || box.box.contains(providerAbs))) {
                            boxes.markDirtyAndBroadcast();
                        }
                    }
                }
            }
        } finally {
            helper.setBlock(machineRel, Blocks.AIR);
            helper.setBlock(providerRel, Blocks.AIR);
            EntityArenaUtil.discardNewItems(helper, itemsBefore);
        }
        helper.succeed();
    }

    /**
     * The three volume markers, connected into a box, and a mock placer; {@code body} runs with them once the markers
     * are registered, and every marker and new dropped item is cleared after it, whatever failed.
     */
    private static void withVolume(GameTestHelper helper, Consumer<Rig> body) {
        Session arena = MarkerArenaUtil.open(helper);
        arena.guard(() -> {
            arena.place(MARKER_1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(MARKER_2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(MARKER_3, BCCoreBlocks.MARKER_VOLUME.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, MARKER_1)
                && arena.isRegistered(VolumeCache.INSTANCE, MARKER_2)
                && arena.isRegistered(VolumeCache.INSTANCE, MARKER_3), () -> {
                ServerLevel level = helper.getLevel();
                BlockPos[] markers = {arena.abs(MARKER_1), arena.abs(MARKER_2), arena.abs(MARKER_3)};
                Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
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
                    body.accept(new Rig(helper, arena, markers, BCCoreBlocks.MARKER_VOLUME.get(), volume, placer,
                        itemsBefore));
                } finally {
                    EntityArenaUtil.discardNewItems(helper, itemsBefore);
                }
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    /** Connected markers, the marker whose connection they are, and the player who places the machines. */
    private record Rig(GameTestHelper helper, Session arena, BlockPos[] markers, Block markerBlock,
                       TileMarker<?> first, Player placer, Set<Integer> itemsBefore) {

        /** The markers all stand, still connected, and nothing dropped. */
        void checkLeftAlone(String who) {
            ServerLevel level = helper.getLevel();
            for (BlockPos marker : markers) {
                check(level.getBlockState(marker).is(markerBlock),
                    who + " refused one of the markers broke the marker at " + arena.rel(marker));
            }
            MarkerConnection<?> connection = first.getCurrentConnection();
            check(connection != null && connection.getMarkerPositions().size() == markers.length,
                "after " + who + " the markers must still be connected, got "
                    + (connection == null ? "no connection" : connection.getMarkerPositions()));
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                check(itemsBefore.contains(item.getId()),
                    who + " refused one of the markers dropped " + item.getItem());
            }
        }

        /** The markers are gone, and the far one was asked about as the placer first. */
        void checkTaken(String who, ClaimProtectionMod claim) {
            ServerLevel level = helper.getLevel();
            for (BlockPos marker : markers) {
                check(level.getBlockState(marker).isAir(),
                    who + " took the markers but left the marker at " + arena.rel(marker));
            }
            check(claim.asks() >= 1, who + " must ask about the far marker before breaking it");
            checkAskedAs(claim, placer, who);
        }
    }

    /** Places a machine facing {@code facing} at {@code rel}; the caller then runs its {@code onPlacedBy} with the
     *  placer, the way a player's placement does, so the placer becomes its owner. */
    private static <T extends BlockEntity> T place(GameTestHelper helper, Block block, BlockPos rel, Direction facing,
            Class<T> type) {
        helper.setBlock(rel, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        BlockEntity tile = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        if (!type.isInstance(tile)) {
            throw new IllegalStateException("no " + type.getSimpleName() + " at " + rel);
        }
        return type.cast(tile);
    }

    /** The last ask the claim saw came from the machine's owner, {@code placer}. */
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
