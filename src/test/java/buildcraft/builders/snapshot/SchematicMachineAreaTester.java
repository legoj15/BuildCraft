/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersConfig;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.BuilderRefloodTester;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil;
import buildcraft.lib.test.MarkerArenaUtil.Session;
import buildcraft.lib.tile.AbstractBCBlockEntity;
import buildcraft.lib.tile.BlueprintOwner;

/**
 * A machine built from a blueprint starts with no working area of its own. The Filler, the Architect Table, the Builder,
 * the Mining Well and the Laser save the absolute world positions of the area they work on, and a copy built somewhere
 * else would load them as they are and go on working the original's area; the rules that rebuild a machine drop those
 * keys on the build path, so a blueprint captured before they did is covered too. {@link SchematicReplaceNbtTester}
 * pins the data every machine's rules produce. This pins the other half for the two machines with the most to lose:
 * the placed block entity, read after the real {@link SchematicBlockDefault#build}. A Filler loses its box. A Builder
 * loses its path and the index into it (an index past the single base position it falls back to would leave the copy
 * unable to resolve its blueprint at all) and works out its own box from its blueprint and its own position.
 *
 * <p>A Quarry takes its area when it is placed, and a blueprint build calls no placement hook, so whoever built it
 * tells it once its owner is set ({@link BlueprintOwner#finishBuild} calls {@code BuiltFromBlueprint}): it lays out
 * the area of one a player placed, the volume box or markers behind it if there are any and its default area
 * otherwise, and asks claim mods as that owner before it breaks any markers. A Filler, an Architect Table and a
 * Builder take the volume box, the markers or the path behind them the same way, and have no default. The tests of a
 * built machine below finish each build the way the Builder and the single schematic do, and read the built tile,
 * never the original's.
 */
public class SchematicMachineAreaTester {

    private static final GameProfile BUILDER =
            new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Bob");

    /** Generous wait for the markers to join the cache (they do on the first level tick); counted from the cell
     *  ticking, so the manifest's max_ticks outlasts {@code EntityArenaUtil.CELL_READY_TICKS + REGISTER_TICKS}. */
    private static final int REGISTER_TICKS = 40;

    /**
     * A Filler with a box, captured and built elsewhere, has none and is not finished: the box and the finished flag are
     * not copied, and what is not its area (the invert flag) is. The scan holds the box, so the stored blueprint is the one an older capture would have produced.
     */
    public static void machineSchematicBuildsFillerWithoutItsBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        BlockPos builtPos = new BlockPos(4, 2, 4);
        helper.setBlock(sourcePos, BCBuildersBlocks.FILLER.get());
        TileFiller source = (TileFiller) level.getBlockEntity(helper.absolutePos(sourcePos));
        helper.assertTrue(source != null, "The placed Filler must have its block entity");
        // The area a Filler placed against markers holds: the same public box TileFiller.onPlacedBy fills in.
        source.box.reset();
        source.box.setMin(helper.absolutePos(new BlockPos(0, 3, 0)));
        source.box.setMax(helper.absolutePos(new BlockPos(2, 4, 2)));
        // What onPlacedBy leaves on a Filler placed against a volume box. A copy that kept it would take any volume
        // box behind it on its first tick, the box of the Filler it was copied from included.
        source.markerBox = false;
        source.inverted = true;
        try {
            SchematicBlockDefault captured = capture(helper, sourcePos);
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "box_initialized", false),
                    "The scan of a Filler with a box must hold it, got " + captured.tileNbt);
            // A Filler that has filled its box saves finished (TileFiller.writeData), which has no public setter.
            captured.tileNbt.putBoolean("finished", true);

            helper.assertTrue(captured.build(level, helper.absolutePos(builtPos)), "The Filler should build into the empty cell");
            TileFiller built = (TileFiller) level.getBlockEntity(helper.absolutePos(builtPos));
            helper.assertTrue(built != null, "The built Filler must have its block entity");
            helper.assertFalse(built.box.isInitialized(), "The built Filler must start with no box, got " + built.box);
            helper.assertTrue(built.markerBox,
                    "The built Filler must not be marked as owing its box to a volume box behind it");
            helper.assertFalse(built.isFinished(),
                    "The built Filler must not start out finished: once it had a box of its own it would stay parked");
            helper.assertTrue(built.inverted, "The built Filler must keep its invert flag");
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "box_initialized", false),
                    "Building must not edit the stored blueprint (it still holds the box)");
        } finally {
            helper.setBlock(sourcePos, Blocks.AIR);
            helper.setBlock(builtPos, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * A Builder that had a path of base positions, captured with its blueprint in the slot and built elsewhere, has no
     * path, and works out its own box from its blueprint relative to its own position: the original's box moved by the
     * distance between the two, which is where the copy's base position stands. The stored index into the path goes with
     * it. Left behind it points past the one base position a Builder without a path falls back to, the copy resolves no
     * blueprint and keeps no box, which is what this checks the box is back for.
     */
    public static void machineSchematicBuildsBuilderWithItsOwnBoxAndNoPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder source = BuilderRefloodTester.setUpBuilder(helper,
                new int[] {BuilderRefloodTester.STONE, BuilderRefloodTester.STONE}, new BlockPos[0]);
        BlockPos sourcePos = BuilderRefloodTester.BUILDER;
        BlockPos builtPos = new BlockPos(4, 2, 5);
        BlockPos sourceAbs = helper.absolutePos(sourcePos);
        BlockPos builtAbs = helper.absolutePos(builtPos);
        TileBuilder[] built = {null};
        try {
            helper.assertTrue(source.getBox().isInitialized(), "The source Builder must have worked out its box");
            // The path a path-marker chain gives a Builder, through the public field the tile fills in at placement.
            source.path = List.of(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(0, 2, 5)));
            SchematicBlockDefault captured = capture(helper, sourcePos);
            helper.assertTrue(NBTUtilBC.getInt(captured.tileNbt, "path_count", 0) == 2,
                    "The scan of a Builder with a path must hold it, got " + captured.tileNbt);
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "box_initialized", false),
                    "The scan of a Builder with a blueprint must hold its box, got " + captured.tileNbt);
            // A Builder that finished its first base position saves the index of the next one (TileBuilder.writeData).
            captured.tileNbt.putInt("currentBasePosIndex", 1);

            helper.assertTrue(captured.build(level, builtAbs), "The Builder should build into the empty cell");
            built[0] = (TileBuilder) level.getBlockEntity(builtAbs);
            helper.assertTrue(built[0] != null, "The built Builder must have its block entity");
            helper.assertTrue(built[0].path == null, "The built Builder must start with no path, got " + built[0].path);
            helper.assertTrue(built[0].getBox().isInitialized(),
                    "The built Builder must work out its own box from its blueprint");
            BlockPos shift = builtAbs.subtract(sourceAbs);
            helper.assertTrue(built[0].getBox().min().equals(source.getBox().min().offset(shift))
                            && built[0].getBox().max().equals(source.getBox().max().offset(shift)),
                    "The built Builder's box must be the original's moved with it, got " + built[0].getBox()
                            + " against " + source.getBox() + " moved by " + shift);
            helper.assertTrue(NBTUtilBC.getInt(captured.tileNbt, "path_count", 0) == 2,
                    "Building must not edit the stored blueprint (it still holds the path)");
        } finally {
            clear(level, source, sourceAbs);
            if (built[0] != null) {
                clear(level, built[0], builtAbs);
            }
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                }
            }
        }
        helper.succeed();
    }

    /**
     * A Quarry that a blueprint builds lays out its area as one a player placed does, and with nothing in the world to
     * take, that is its default area behind it. The original's area is gone from what the blueprint builds (a copy must
     * never work the original's pit), so the box a built Quarry holds is its own: the default box for ITS position and
     * ITS facing, in a turned build too, where the block state's facing was turned before the build. Each built Quarry
     * has no task and no drill, the original still has its own area, and the stored blueprint still holds it. A
     * finished build that placed nothing (the Quarry standing there is the one that stood there before) gives that
     * Quarry neither an area nor an owner.
     *
     * <p>The default area is 11 blocks wide and reaches far outside this cell. Nothing here ticks (the body is one
     * tick), so no frame block is placed, no rig spawned, nothing mined; the chunk tickets the area takes at once are
     * released by the removal, and every Quarry is removed before the test ends.
     */
    public static void machineSchematicBuildsQuarryWithItsDefaultArea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceRel = new BlockPos(1, 2, 1);
        BlockPos sourceAbs = helper.absolutePos(sourceRel);
        BlockPos builtRel = new BlockPos(4, 2, 5);
        BlockPos turnedRel = new BlockPos(3, 2, 5);
        try {
            helper.setBlock(sourceRel, quarryState(Direction.SOUTH));
            TileQuarry source = (TileQuarry) level.getBlockEntity(sourceAbs);
            helper.assertTrue(source != null, "The placed Quarry must have its block entity");
            // What a player's placement does: nothing is behind it, so it takes the default area.
            source.onPlacedBy(null, ItemStack.EMPTY);
            BlockPos[] sourceArea = defaultQuarryArea(sourceAbs, Direction.SOUTH);
            assertFrame(helper, source, sourceArea, "The source Quarry must hold the default area from its placement");
            SchematicBlockDefault captured = capture(helper, sourceRel);
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "frame_init", false),
                    "The scan of a Quarry with an area must hold it, got " + captured.tileNbt);

            // A SOUTH quarry lays its area out to the north, and the same blueprint turned a quarter clockwise builds
            // a WEST quarry, whose area lies to the east.
            checkBuiltQuarryHasItsDefaultArea(helper, captured, builtRel, Direction.SOUTH, source, "unrotated");
            checkBuiltQuarryHasItsDefaultArea(helper, captured.getRotated(Rotation.CLOCKWISE_90), turnedRel,
                    Direction.WEST, source, "turned");

            assertFrame(helper, source, sourceArea, "Building must leave the source Quarry its own area");
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "frame_init", false),
                    "Building must not edit the stored blueprint (it still holds the area)");

            // A schematic can report success without placing a block. Finishing such a build, with the Quarry that
            // stood in the cell before still standing there, touches that Quarry not at all: one somebody else put
            // there keeps its owner (none here) and lays out no area it was never placed for.
            helper.setBlock(builtRel, quarryState(Direction.SOUTH));
            TileQuarry standing = (TileQuarry) level.getBlockEntity(helper.absolutePos(builtRel));
            helper.assertTrue(standing != null, "The standing Quarry must have its block entity");
            BlueprintOwner.finishBuild(level, helper.absolutePos(builtRel), BUILDER, standing);
            helper.assertFalse(standing.frameBox.isInitialized(),
                    "A build that placed nothing must not lay out an area for the Quarry standing there, got "
                            + standing.frameBox);
            helper.assertTrue(standing.getOwner() == null,
                    "A build that placed nothing must not give the Quarry standing there an owner, got "
                            + standing.getOwner());
        } finally {
            helper.setBlock(sourceRel, Blocks.AIR);
            helper.setBlock(builtRel, Blocks.AIR);
            helper.setBlock(turnedRel, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * A Quarry that a blueprint builds with a volume box behind it takes that box, as one placed there does: its frame
     * stands over the box's footprint on the Quarry's own level and the box is used up. The blueprint holds no area of
     * the original's, so what the built Quarry finds behind it is the only thing it can take. A Quarry that already
     * holds an area is not given another by being told it was built from a blueprint.
     */
    public static void machineSchematicBuildsQuarryOntoAVolumeBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
        BlockPos sourceRel = new BlockPos(1, 2, 1);
        // Facing south, so the cell behind it, (2,2,5), is the box's.
        BlockPos builtRel = new BlockPos(2, 2, 6);
        BlockPos builtAbs = helper.absolutePos(builtRel);
        BlockPos boxMin = helper.absolutePos(new BlockPos(1, 2, 2));
        BlockPos boxMax = helper.absolutePos(new BlockPos(4, 2, 5));
        VolumeBox box = null;
        VolumeBox second = null;
        try {
            helper.setBlock(sourceRel, quarryState(Direction.SOUTH));
            TileQuarry source = (TileQuarry) level.getBlockEntity(helper.absolutePos(sourceRel));
            helper.assertTrue(source != null, "The placed Quarry must have its block entity");
            // Placed before the box exists, so the original's own area is the default one and not the box.
            source.onPlacedBy(null, ItemStack.EMPTY);
            SchematicBlockDefault captured = capture(helper, sourceRel);

            // The kind of box a resize leaves: confirmed, backed by no markers, and takeable by a machine.
            box = VolumeBox.fromMarkers(level, boxMin, boxMax, List.of());
            boxes.volumeBoxes.add(box);
            buildAndFinish(level, captured, builtAbs, "The Quarry");
            TileQuarry built = (TileQuarry) level.getBlockEntity(builtAbs);
            helper.assertTrue(built != null, "The built Quarry must have its block entity");
            // The frame covers the box's footprint on the Quarry's level, and is at least the configured height.
            int top = Math.max(boxMax.getY(), builtAbs.getY() + BCBuildersConfig.quarryFrameMinHeight.get());
            assertFrame(helper, built, new BlockPos[] {
                    new BlockPos(boxMin.getX(), builtAbs.getY(), boxMin.getZ()),
                    new BlockPos(boxMax.getX(), top, boxMax.getZ())},
                    "The built Quarry must take the volume box behind it");
            helper.assertFalse(boxes.volumeBoxes.contains(box),
                    "The volume box the built Quarry took must be used up");
            // Told once more that a blueprint built it, a Quarry that holds an area takes nothing: a second box behind
            // it stays where it is.
            second = VolumeBox.fromMarkers(level, boxMin, boxMax, List.of());
            boxes.volumeBoxes.add(second);
            built.onBuiltFromBlueprint();
            helper.assertTrue(boxes.volumeBoxes.contains(second),
                    "A Quarry that already holds its area must not take the box behind it a second time");
        } finally {
            boolean removed = box != null && boxes.volumeBoxes.remove(box);
            removed |= second != null && boxes.volumeBoxes.remove(second);
            if (removed) {
                boxes.markDirtyAndBroadcast();
            }
            helper.setBlock(sourceRel, Blocks.AIR);
            helper.setBlock(builtRel, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * A Quarry a blueprint builds against a connected marker volume takes the volume, as one placed there does, and
     * taking it breaks the markers: it asks claim mods about them first, and asks as whoever built it. Its owner is
     * set before it lays out its area, so the claim sees the builder (Bob) and never the mod's own profile, which an
     * unowned Quarry asks as. The claim watches the far marker, which the Quarry does not stand against, and allows
     * the break; refusals are {@code MarkerTakingProtectionTester}'s, on the same code path.
     *
     * <p>The schematic is captured from a Quarry that was never placed (no area of its own to take the markers with),
     * and the built Quarry is removed in the tick it was built in, before it ever ticks.
     */
    public static void machineSchematicQuarryTakesMarkersAsItsBuilder(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos marker1 = new BlockPos(1, 2, 1);
        BlockPos marker2 = new BlockPos(4, 2, 1);
        BlockPos marker3 = new BlockPos(1, 2, 4);
        BlockPos boxMax = new BlockPos(4, 2, 4);
        BlockPos sourceRel = new BlockPos(3, 2, 6);
        // Facing west, so its back is marker1.
        BlockPos builtRel = new BlockPos(0, 2, 1);
        arena.guard(() -> {
            arena.place(marker1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker3, BCCoreBlocks.MARKER_VOLUME.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, marker1)
                    && arena.isRegistered(VolumeCache.INSTANCE, marker2)
                    && arena.isRegistered(VolumeCache.INSTANCE, marker3), () -> {
                ServerLevel level = helper.getLevel();
                BlockPos[] markers = {arena.abs(marker1), arena.abs(marker2), arena.abs(marker3)};
                BlockPos builtAbs = arena.abs(builtRel);
                Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
                try {
                    helper.assertFalse(BCCoreConfig.minePlayerProtected.get(),
                            "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
                    helper.assertTrue(level.getBlockEntity(markers[0]) instanceof TileMarkerVolume,
                            "no volume marker tile at " + marker1);
                    TileMarkerVolume volume = (TileMarkerVolume) level.getBlockEntity(markers[0]);
                    volume.onManualConnectionAttempt(null);
                    helper.assertTrue(volume.max().equals(arena.abs(boxMax)),
                            "precondition: the markers did not connect into a box");

                    helper.setBlock(sourceRel, quarryState(Direction.WEST));
                    SchematicBlockDefault captured = capture(helper, sourceRel);
                    helper.setBlock(sourceRel, Blocks.AIR);

                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[1], false)) {
                        buildAndFinish(level, captured, builtAbs, "The Quarry");
                        helper.assertTrue(claim.asks() >= 1,
                                "The built Quarry must ask about the far marker before it breaks it");
                        Player asker = claim.askedBy.get(0);
                        helper.assertTrue(asker != null
                                        && GameProfileUtil.getId(BUILDER).equals(asker.getUUID()),
                                "The built Quarry must ask as its builder " + GameProfileUtil.getName(BUILDER)
                                        + ", so its owner must be set before it lays out its area; asked as "
                                        + (asker == null ? "nobody" : asker.getGameProfile()));
                    }
                    TileQuarry built = (TileQuarry) level.getBlockEntity(builtAbs);
                    helper.assertTrue(built != null, "The built Quarry must have its block entity");
                    GameProfile owner = built.getOwner();
                    helper.assertTrue(owner != null
                                    && GameProfileUtil.getId(owner).equals(GameProfileUtil.getId(BUILDER)),
                            "The built Quarry must belong to its builder, got " + owner);
                    BlockPos min = arena.abs(marker1);
                    BlockPos max = arena.abs(boxMax);
                    assertFrame(helper, built, new BlockPos[] {
                            new BlockPos(min.getX(), builtAbs.getY(), min.getZ()),
                            new BlockPos(max.getX(),
                                    Math.max(max.getY(), builtAbs.getY() + BCBuildersConfig.quarryFrameMinHeight.get()),
                                    max.getZ())},
                            "The built Quarry must take the marker volume behind it");
                    for (BlockPos marker : markers) {
                        helper.assertTrue(level.getBlockState(marker).isAir(),
                                "The built Quarry took the volume but left the marker at " + arena.rel(marker));
                    }
                } finally {
                    // Never leave a Quarry or a dropped marker behind, whatever failed.
                    helper.setBlock(sourceRel, Blocks.AIR);
                    helper.setBlock(builtRel, Blocks.AIR);
                    EntityArenaUtil.discardNewItems(helper, itemsBefore);
                }
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    /**
     * A Filler a blueprint builds against a connected marker volume takes the volume, as one placed there does: the
     * markers go, asked about first as whoever built it (its owner is set before the taking), the box the Filler
     * holds is the volume's, and the Filler has worked out what to build in it (valid: a Filler with a box and no
     * plan for it does nothing). The original is given a pattern for that; a Filler without one has no plan anywhere.
     */
    public static void machineSchematicFillerTakesMarkersAsItsBuilder(GameTestHelper helper) {
        builtMachineTakesMarkerVolume(helper, BCBuildersBlocks.FILLER.get(), "Filler",
                source -> ((TileFiller) source).patternStatement.set(BCBuildersStatements.PATTERN_FILL),
                built -> built instanceof TileFiller filler && filler.isValid() ? filler.box : null);
    }

    /**
     * An Architect Table a blueprint builds against a connected marker volume takes the volume, as one placed there
     * does: the table is valid, its box is the volume's, and the markers went, asked about first as whoever built it.
     */
    public static void machineSchematicArchitectTableTakesMarkersAsItsBuilder(GameTestHelper helper) {
        builtMachineTakesMarkerVolume(helper, BCBuildersBlocks.ARCHITECT.get(), "Architect Table",
                source -> { },
                built -> built instanceof TileArchitectTable table && table.getIsValid() && table.box.isInitialized()
                        ? table.box : null);
    }

    /**
     * The body of the two tests above. {@code prepareSource} sets the machine the schematic is captured from up
     * before the capture. {@code areaOf} answers the area the built machine works on, or null while it
     * has none or cannot work on it (while it is not valid). The schematic is captured from a machine that was never
     * placed, so the blueprint holds no area of the original's, and the built machine is removed in the tick it was
     * built in, before it ever ticks.
     */
    private static void builtMachineTakesMarkerVolume(GameTestHelper helper, Block machine, String name,
            Consumer<BlockEntity> prepareSource, Function<BlockEntity, Box> areaOf) {
        String who = "The built " + name;
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos marker1 = new BlockPos(1, 2, 1);
        BlockPos marker2 = new BlockPos(4, 2, 1);
        BlockPos marker3 = new BlockPos(1, 2, 4);
        BlockPos boxMax = new BlockPos(4, 2, 4);
        BlockPos sourceRel = new BlockPos(3, 2, 6);
        // Facing west, so its back is marker1.
        BlockPos builtRel = new BlockPos(0, 2, 1);
        arena.guard(() -> {
            arena.place(marker1, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker2, BCCoreBlocks.MARKER_VOLUME.get());
            arena.place(marker3, BCCoreBlocks.MARKER_VOLUME.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(VolumeCache.INSTANCE, marker1)
                    && arena.isRegistered(VolumeCache.INSTANCE, marker2)
                    && arena.isRegistered(VolumeCache.INSTANCE, marker3), () -> {
                ServerLevel level = helper.getLevel();
                LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(level);
                BlockPos[] markers = {arena.abs(marker1), arena.abs(marker2), arena.abs(marker3)};
                BlockPos builtAbs = arena.abs(builtRel);
                Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
                try {
                    checkVolumeMarkersConnected(helper, arena, markers, boxMax);

                    helper.setBlock(sourceRel, machineState(machine, Direction.WEST));
                    prepareSource.accept(level.getBlockEntity(arena.abs(sourceRel)));
                    SchematicBlockDefault captured = capture(helper, sourceRel);
                    helper.setBlock(sourceRel, Blocks.AIR);

                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[1], false)) {
                        buildAndFinish(level, captured, builtAbs, "The " + name);
                        checkAskedAsItsBuilder(helper, claim, who);
                    }
                    BlockEntity built = level.getBlockEntity(builtAbs);
                    helper.assertTrue(built instanceof AbstractBCBlockEntity, who + " must have its block entity");
                    checkBelongsToItsBuilder(helper, ((AbstractBCBlockEntity) built).getOwner(), who);
                    Box area = areaOf.apply(built);
                    helper.assertTrue(area != null && area.min().equals(arena.abs(marker1))
                                    && area.max().equals(arena.abs(boxMax)),
                            who + " must take the marker volume behind it and be valid, got "
                                    + (area == null ? "no area it can work on" : area));
                    checkMarkersTaken(helper, arena, markers, who);
                } finally {
                    // Never leave the machine, the box it owns or a dropped marker behind, whatever failed. Removing
                    // the block releases the box the machine owns; the sweep is the backstop.
                    helper.setBlock(sourceRel, Blocks.AIR);
                    helper.setBlock(builtRel, Blocks.AIR);
                    if (boxes.volumeBoxes.removeIf(box -> box.isOwnedBy(builtAbs) || box.box.contains(markers[0]))) {
                        boxes.markDirtyAndBroadcast();
                    }
                    EntityArenaUtil.discardNewItems(helper, itemsBefore);
                }
                arena.finish();
            }, "Volume markers never registered in the cache");
        });
    }

    /**
     * A Builder a blueprint builds against a connected path of path markers takes the path, as one placed there
     * does: the path markers go, asked about first as whoever built it (its owner is set before the taking), and the
     * path the Builder holds is the markers'. The schematic is captured from a Builder that was never placed, and
     * the built Builder is removed in the tick it was built in, before it ever ticks.
     */
    public static void machineSchematicBuilderTakesPathAsItsBuilder(GameTestHelper helper) {
        Session arena = MarkerArenaUtil.open(helper);
        BlockPos path1 = new BlockPos(1, 2, 1);
        BlockPos path2 = new BlockPos(4, 2, 1);
        BlockPos path3 = new BlockPos(4, 2, 4);
        BlockPos sourceRel = new BlockPos(3, 2, 6);
        // Facing west, so its back is path1.
        BlockPos builtRel = new BlockPos(0, 2, 1);
        arena.guard(() -> {
            arena.place(path1, BCCoreBlocks.MARKER_PATH.get());
            arena.place(path2, BCCoreBlocks.MARKER_PATH.get());
            arena.place(path3, BCCoreBlocks.MARKER_PATH.get());
            arena.await(REGISTER_TICKS, () -> arena.isRegistered(PathCache.INSTANCE, path1)
                    && arena.isRegistered(PathCache.INSTANCE, path2)
                    && arena.isRegistered(PathCache.INSTANCE, path3), () -> {
                ServerLevel level = helper.getLevel();
                BlockPos[] markers = {arena.abs(path1), arena.abs(path2), arena.abs(path3)};
                BlockPos builtAbs = arena.abs(builtRel);
                Set<Integer> itemsBefore = EntityArenaUtil.itemIdsInCell(helper);
                try {
                    helper.assertFalse(BCCoreConfig.minePlayerProtected.get(),
                            "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
                    TileMarkerPath path = connectPathMarkers(level, markers);
                    helper.assertTrue(path.getPath().size() == 3,
                            "precondition: the path markers did not connect, got " + path.getPath());

                    helper.setBlock(sourceRel, machineState(BCBuildersBlocks.BUILDER.get(), Direction.WEST));
                    SchematicBlockDefault captured = capture(helper, sourceRel);
                    helper.setBlock(sourceRel, Blocks.AIR);

                    try (ClaimProtectionMod claim = new ClaimProtectionMod(markers[2], false)) {
                        buildAndFinish(level, captured, builtAbs, "The Builder");
                        checkAskedAsItsBuilder(helper, claim, "The built Builder");
                    }
                    TileBuilder built = (TileBuilder) level.getBlockEntity(builtAbs);
                    helper.assertTrue(built != null, "The built Builder must have its block entity");
                    checkBelongsToItsBuilder(helper, built.getOwner(), "The built Builder");
                    helper.assertTrue(built.path != null && built.path.size() == 3
                                    && built.path.containsAll(List.of(markers)),
                            "The built Builder must take the path of the path markers behind it, got " + built.path);
                    checkMarkersTaken(helper, arena, markers, "The built Builder");
                } finally {
                    // Never leave a Builder or a dropped marker behind, whatever failed.
                    helper.setBlock(sourceRel, Blocks.AIR);
                    helper.setBlock(builtRel, Blocks.AIR);
                    EntityArenaUtil.discardNewItems(helper, itemsBefore);
                }
                arena.finish();
            }, "Path markers never registered in the cache");
        });
    }

    /**
     * Builds {@code schematic} at {@code builtAbs} and finishes the build as the Builder does, for {@link #BUILDER}
     * ({@link BlueprintOwner#finishBuild}, given the block entity that stood there before). {@code what} names the
     * built machine in the failure.
     */
    private static void buildAndFinish(ServerLevel level, ISchematicBlock schematic, BlockPos builtAbs, String what) {
        BlockEntity before = level.getBlockEntity(builtAbs);
        if (!schematic.build(level, builtAbs)) {
            throw new IllegalStateException(what + " should build into the empty cell");
        }
        BlueprintOwner.finishBuild(level, builtAbs, BUILDER, before);
    }

    /**
     * Builds {@code schematic} at {@code builtRel}, finishes the build ({@link #buildAndFinish}) and checks the Quarry
     * it places: facing {@code facing}, the default area for its own position and facing as its frame box, the inside
     * of that frame as its mining box (read from what the tile saves, since the box is not public), no task, no drill,
     * and not the original's frame. Removes the Quarry.
     */
    private static void checkBuiltQuarryHasItsDefaultArea(GameTestHelper helper, SchematicBlockDefault schematic,
            BlockPos builtRel, Direction facing, TileQuarry source, String how) {
        ServerLevel level = helper.getLevel();
        BlockPos builtAbs = helper.absolutePos(builtRel);
        buildAndFinish(level, schematic, builtAbs, "The " + how + " Quarry");
        BlockState state = level.getBlockState(builtAbs);
        Direction builtFacing = state.getValue(HorizontalDirectionalBlock.FACING);
        helper.assertTrue(builtFacing == facing, "The " + how + " Quarry must face " + facing + ", got " + builtFacing);
        TileQuarry built = (TileQuarry) level.getBlockEntity(builtAbs);
        helper.assertTrue(built != null, "The " + how + " Quarry must have its block entity");

        BlockPos[] area = defaultQuarryArea(builtAbs, facing);
        assertFrame(helper, built, area, "The " + how + " built Quarry must lay out its default area");
        helper.assertFalse(built.frameBox.min().equals(source.frameBox.min()),
                "The " + how + " built Quarry must not have the original's frame, got " + built.frameBox);

        // The mining box is the frame's inside: one in from its sides, below its top, down to the configured depth
        // (the quarry's own level less that depth, or the bottom of the world).
        CompoundTag saved = built.saveCustomOnly(level.registryAccess());
        int floor = Math.max(level.getMinY(), builtAbs.getY() - BCCoreConfig.miningMaxDepth.get());
        int[] expected = {area[0].getX() + 1, floor, area[0].getZ() + 1,
                area[1].getX() - 1, area[1].getY() - 1, area[1].getZ() - 1};
        String[] keys = {"box_minX", "box_minY", "box_minZ", "box_maxX", "box_maxY", "box_maxZ"};
        helper.assertTrue(NBTUtilBC.getBoolean(saved, "box_init", false),
                "The " + how + " built Quarry must have a mining box, got " + saved);
        for (int i = 0; i < keys.length; i++) {
            int actual = NBTUtilBC.getInt(saved, keys[i], Integer.MIN_VALUE);
            helper.assertTrue(actual == expected[i], "The " + how + " built Quarry's mining box " + keys[i]
                    + " must be " + expected[i] + " (the inside of its frame), got " + actual + " in " + saved);
        }
        helper.assertTrue(built.currentTask == null, "The " + how + " built Quarry must have no task");
        helper.assertTrue(built.drillPos == null, "The " + how + " built Quarry must have no drill");
        helper.setBlock(builtRel, Blocks.AIR);
    }

    /** The frame box of {@code quarry} must be exactly {@code area} ({min, max}). */
    private static void assertFrame(GameTestHelper helper, TileQuarry quarry, BlockPos[] area, String what) {
        helper.assertTrue(quarry.frameBox.isInitialized() && quarry.frameBox.min().equals(area[0])
                        && quarry.frameBox.max().equals(area[1]),
                what + ": expected " + area[0] + " to " + area[1] + ", got " + quarry.frameBox);
    }

    private static BlockState quarryState(Direction facing) {
        return BCBuildersBlocks.QUARRY.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /**
     * The area a Quarry at {@code quarry} facing {@code facing} lays out when nothing is there to take, as {min, max}:
     * 11 blocks wide (5 either side of the Quarry's line), from one block behind it to 11 blocks behind it, standing on
     * its own level. The rule written out here, not asked of the production code. Its height is 4 above the floor,
     * which the configured minimum frame height (4 by default) does not raise; a larger configured minimum would, so
     * the top follows the config.
     */
    private static BlockPos[] defaultQuarryArea(BlockPos quarry, Direction facing) {
        Direction back = facing.getOpposite();
        Direction side = back.getClockWise();
        BlockPos near = quarry.relative(back, 1).relative(side, 5);
        BlockPos far = quarry.relative(back, 11).relative(side, -5);
        int top = quarry.getY() + Math.max(4, BCBuildersConfig.quarryFrameMinHeight.get());
        return new BlockPos[] {
                new BlockPos(Math.min(near.getX(), far.getX()), quarry.getY(), Math.min(near.getZ(), far.getZ())),
                new BlockPos(Math.max(near.getX(), far.getX()), top, Math.max(near.getZ(), far.getZ()))};
    }

    /** Wipes a Builder so removing it spills nothing into the world: the snapshot goes first (a task in flight refunds
     *  its items into the resources), then the contents. */
    private static void clear(ServerLevel level, TileBuilder tile, BlockPos abs) {
        tile.setSnapshot(ItemStack.EMPTY);
        tile.clearContent();
        level.removeBlock(abs, false);
    }

    /** Captures the block in the cell the way the Architect does, through the real factory chain. */
    private static SchematicBlockDefault capture(GameTestHelper helper, BlockPos relPos) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockDefault,
                "A machine should route to SchematicBlockDefault, got " + schematic.getClass().getSimpleName());
        SchematicBlockDefault captured = (SchematicBlockDefault) schematic;
        helper.assertTrue(captured.tileNbt != null, "The scan must keep the machine's tile data");
        return captured;
    }

    /** The preconditions every built-machine test below needs: claims are asked at all, and the three volume
     *  markers, {@code markers[0]} the one behind the machine, are connected into the box reaching
     *  {@code boxMaxRel}. */
    private static void checkVolumeMarkersConnected(GameTestHelper helper, Session arena, BlockPos[] markers,
            BlockPos boxMaxRel) {
        helper.assertFalse(BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
        helper.assertTrue(helper.getLevel().getBlockEntity(markers[0]) instanceof TileMarkerVolume,
                "no volume marker tile at " + arena.rel(markers[0]));
        TileMarkerVolume volume = (TileMarkerVolume) helper.getLevel().getBlockEntity(markers[0]);
        volume.onManualConnectionAttempt(null);
        helper.assertTrue(volume.max().equals(arena.abs(boxMaxRel)),
                "precondition: the markers did not connect into a box");
    }

    /** Connects the three path markers into one path, from {@code markers[0]}, the one behind the machine. */
    private static TileMarkerPath connectPathMarkers(ServerLevel level, BlockPos[] markers) {
        if (!(level.getBlockEntity(markers[0]) instanceof TileMarkerPath path)) {
            throw new IllegalStateException("no path marker tile at " + markers[0]);
        }
        path.getCache().getSubCache(level).tryConnect(markers[0], markers[1]);
        path.getCache().getSubCache(level).tryConnect(markers[1], markers[2]);
        return path;
    }

    /** The claim saw at least one ask, and the first came from {@link #BUILDER}: a built machine asks as its
     *  builder, so its owner must be set before it takes the markers. */
    private static void checkAskedAsItsBuilder(GameTestHelper helper, ClaimProtectionMod claim, String who) {
        helper.assertTrue(claim.asks() >= 1, who + " must ask about the far marker before it breaks it");
        Player asker = claim.askedBy.get(0);
        helper.assertTrue(asker != null && GameProfileUtil.getId(BUILDER).equals(asker.getUUID()),
                who + " must ask as its builder " + GameProfileUtil.getName(BUILDER)
                        + ", so its owner must be set before it takes the markers; asked as "
                        + (asker == null ? "nobody" : asker.getGameProfile()));
    }

    /** The built machine belongs to {@link #BUILDER}, never to whoever made the blueprint it was built from. */
    private static void checkBelongsToItsBuilder(GameTestHelper helper, GameProfile owner, String who) {
        helper.assertTrue(owner != null && GameProfileUtil.getId(owner).equals(GameProfileUtil.getId(BUILDER)),
                who + " must belong to its builder, got " + owner);
    }

    /** Every marker of the connection is gone: the machine took them all. */
    private static void checkMarkersTaken(GameTestHelper helper, Session arena, BlockPos[] markers, String who) {
        for (BlockPos marker : markers) {
            helper.assertTrue(helper.getLevel().getBlockState(marker).isAir(),
                    who + " took the markers but left the marker at " + arena.rel(marker));
        }
    }

    /** The machine's block state turned to face {@code facing}, the state a capture builds back. */
    private static BlockState machineState(Block block, Direction facing) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }
}
