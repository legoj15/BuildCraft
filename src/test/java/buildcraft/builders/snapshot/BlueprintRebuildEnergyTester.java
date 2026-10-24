/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.api.mj.MjBattery;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.tile.TileLaser;

/**
 * The wiring half of "a blueprint never rebuilds a machine with the stored energy it was scanned with": the real
 * Architect scan of a charged machine, then the real {@link SchematicBlockDefault#build} and
 * {@link SchematicBlockDefault#buildWithoutChecks} into a world, then the placed block entity's own battery.
 * {@link SchematicReplaceNbtTester} pins the data the rules produce; this pins that both build paths load that
 * data and not the raw scan (pointing either call site back at a plain copy of {@code tileNbt} re-mints the power).
 * A quarry (a top-level battery key) and a laser (the battery as a nested compound) cover the two layouts.
 */
public class BlueprintRebuildEnergyTester {

    private record Machine(String name, Supplier<Block> block, Function<BlockEntity, MjBattery> battery) {}

    private static final List<Machine> MACHINES = List.of(
        new Machine("quarry", BCBuildersBlocks.QUARRY::get, tile -> ((TileQuarry) tile).getBattery()),
        new Machine("laser", BCSiliconBlocks.LASER::get, tile -> ((TileLaser) tile).getBattery()));

    /** {@link SchematicBlockDefault#build} places the machine with no stored energy, whether the Builder places
     *  container contents or ignores them. */
    public static void testBuildDropsStoredEnergy(GameTestHelper helper) {
        check(helper, false);
    }

    /** {@link SchematicBlockDefault#buildWithoutChecks}, the path the Builder takes when it skips its placement
     *  checks, does the same. */
    public static void testBuildWithoutChecksDropsStoredEnergy(GameTestHelper helper) {
        check(helper, true);
    }

    private static void check(GameTestHelper helper, boolean withoutChecks) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (int row = 0; row < MACHINES.size(); row++) {
                Machine machine = MACHINES.get(row);
                Block block = machine.block().get();

                // z stays inside the test's own 6x7 cell: two rows, with room for two destinations each.
                BlockPos source = helper.absolutePos(new BlockPos(1, 2, 1 + 3 * row));
                level.setBlock(source, block.defaultBlockState(), 3);
                placed.add(source);
                BlockEntity scannedTile = level.getBlockEntity(source);
                require(scannedTile != null, machine.name() + ": the source has no block entity");
                MjBattery sourceBattery = machine.battery().apply(scannedTile);
                sourceBattery.setStored(sourceBattery.getCapacity());
                require(sourceBattery.getStored() > 0, machine.name() + ": the source battery did not charge");

                BlockState state = level.getBlockState(source);
                ISchematicBlock scanned = SchematicBlockManager.getSchematicBlock(
                    new SchematicBlockContext(level, BlockPos.ZERO, source, state, state.getBlock()));
                require(scanned instanceof SchematicBlockDefault,
                    machine.name() + ": expected SchematicBlockDefault; got " + scanned.getClass());
                SchematicBlockDefault schematic = (SchematicBlockDefault) scanned;

                // The scan really captured the energy: loading it into a bare tile gives it back. Without this a
                // scan that stored nothing would make every check below pass on its own.
                require(schematic.tileNbt != null, machine.name() + ": the scan kept no tile data");
                BlockEntity probe = ((EntityBlock) block).newBlockEntity(source, state);
                load(level, probe, schematic.tileNbt.copy());
                require(machine.battery().apply(probe).getStored() > 0,
                    machine.name() + ": the scanned data holds no energy: " + schematic.tileNbt);

                boolean[] contentsModes = withoutChecks ? new boolean[] {true} : new boolean[] {true, false};
                for (int i = 0; i < contentsModes.length; i++) {
                    boolean includeContents = contentsModes[i];
                    String mode = machine.name() + (withoutChecks ? " buildWithoutChecks"
                        : includeContents ? " build (contents placed)" : " build (contents ignored)");
                    BlockPos destination = helper.absolutePos(new BlockPos(3 + 2 * i, 2, 1 + 3 * row));
                    placed.add(destination);

                    boolean built = withoutChecks
                        ? schematic.buildWithoutChecks(level, destination)
                        : schematic.build(level, destination, EnumFluidHandlingMode.NO_REPLACE, includeContents);
                    require(built, mode + ": the schematic did not place");
                    require(level.getBlockState(destination).is(block), mode + ": the wrong block was placed");
                    BlockEntity rebuilt = level.getBlockEntity(destination);
                    require(rebuilt != null, mode + ": the placed block has no block entity");
                    long stored = machine.battery().apply(rebuilt).getStored();
                    require(stored == 0, mode + ": the rebuilt machine holds " + stored + " stored energy");
                }
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            for (BlockPos pos : placed) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /** The load a placed block entity goes through. */
    private static void load(ServerLevel level, BlockEntity tile, CompoundTag tag) {
        //? if >=1.21.10 {
        tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        //?} else {
        /*tile.loadWithComponents(tag, level.registryAccess());*/
        //?}
    }
}
