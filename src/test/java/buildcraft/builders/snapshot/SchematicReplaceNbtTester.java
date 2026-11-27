/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.factory.tile.TileMiner;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.tile.AbstractBCBlockEntity;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.tile.TileLaser;
import buildcraft.silicon.tile.TileLaserTableBase;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * A blueprint never rebuilds a machine with the stored energy it was scanned with. The scan keeps the machine's whole
 * saved data, so a quarry, laser or engine would be rebuilt holding the power it had; 1.12.2 stripped it with the rule
 * directive {@code replaceNbt}, which this port parsed and never applied. Every case builds its fixture from the real
 * tile's own save (so a rule naming a key the tile no longer writes fails here) and reads the rebuilt data back through
 * the real tile's own load. A machine that works an area it saves as absolute positions is rebuilt without that area
 * the same way ({@link #aRebuiltMachineHoldsNoWorkingArea}).
 */
public class SchematicReplaceNbtTester extends VanillaSetupBaseTester {

    private static final long CHARGE = 7 * MjAPI.MJ;

    /** The top-level save keys the item extractors of these machines' rules strip in IGNORE mode: {@code items}
     *  (the inventory lists), {@code invSnapshot} (the builder's snapshot slot), {@code containerSlots} (the
     *  distiller's container slots) and {@code fuel} (the stone engine's). Nothing else may change in that mode. */
    private static final Set<String> INVENTORY_KEYS = Set.of("items", "invSnapshot", "containerSlots", "fuel");

    /** One block entity that keeps energy in its saved data: how to charge it, how much it then holds, and the
     *  top-level save keys that hold that energy. */
    private record Machine(String name, Supplier<? extends Block> block, Consumer<BlockEntity> charge,
                           ToLongFunction<BlockEntity> stored, String... energyKeys) {}

    private static void fill(MjBattery battery) {
        battery.setStored(battery.getCapacity());
    }

    private static CompoundTag longs(Object... keysAndValues) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            tag.putLong((String) keysAndValues[i], ((Number) keysAndValues[i + 1]).longValue());
        }
        return tag;
    }

    private static final List<Machine> MACHINES = List.of(
        new Machine("quarry", BCBuildersBlocks.QUARRY::get,
            tile -> {
                // A task mid-way has already been paid for: its power is energy too. Loaded before the battery is
                // set, because the load writes every key it does not carry back to its default. The quarry stands
                // against its frame (the tile is at the origin, the frame starts one block east of it, the mining box
                // is the frame's inside): a quarry that does not drops its box on load, and its task with it.
                load(tile, longs("currentTaskId", 0, "task_power", CHARGE,
                    "frame_init", 1, "frame_minX", 1, "frame_minY", 0, "frame_minZ", -2,
                    "frame_maxX", 5, "frame_maxY", 4, "frame_maxZ", 2,
                    "box_init", 1, "box_minX", 2, "box_minY", -60, "box_minZ", -1,
                    "box_maxX", 4, "box_maxY", 3, "box_maxZ", 1));
                fill(((TileQuarry) tile).getBattery());
            },
            tile -> ((TileQuarry) tile).getBattery().getStored()
                + (((TileQuarry) tile).currentTask == null ? 0 : ((TileQuarry) tile).currentTask.power),
            "battery_mj", "task_power"),
        new Machine("filler", BCBuildersBlocks.FILLER::get,
            tile -> fill(((TileFiller) tile).getBattery()),
            tile -> ((TileFiller) tile).getBattery().getStored(),
            "battery_mj"),
        new Machine("builder", BCBuildersBlocks.BUILDER::get,
            tile -> fill(((TileBuilder) tile).getBattery()),
            tile -> ((TileBuilder) tile).getBattery().getStored(),
            "battery_mj"),
        new Machine("chute", BCFactoryBlocks.CHUTE::get,
            tile -> fill(((TileChute) tile).getBattery()),
            tile -> ((TileChute) tile).getBattery().getStored(),
            "mjStored"),
        new Machine("distiller", BCFactoryBlocks.DISTILLER::get,
            tile -> fill(((TileDistiller_BC8) tile).getBattery()),
            tile -> ((TileDistiller_BC8) tile).getBattery().getStored(),
            "mjStored"),
        new Machine("pump", BCFactoryBlocks.PUMP::get,
            tile -> fill(((TileMiner) tile).getBattery()),
            tile -> ((TileMiner) tile).getBattery().getStored(),
            "mjStored"),
        new Machine("mining_well", BCFactoryBlocks.MINING_WELL::get,
            tile -> fill(((TileMiner) tile).getBattery()),
            tile -> ((TileMiner) tile).getBattery().getStored(),
            "mjStored"),
        new Machine("laser", BCSiliconBlocks.LASER::get,
            tile -> fill(((TileLaser) tile).getBattery()),
            tile -> ((TileLaser) tile).getBattery().getStored(),
            "battery"),
        new Machine("mj_dynamo", BCEnergyBlocks.DYNAMO_MJ::get,
            tile -> {
                load(tile, longs("power", CHARGE));
                ((TileDynamoMJ) tile).setCurrentFe(TileDynamoMJ.MAX_FE);
                fill(((TileDynamoMJ) tile).getMjBattery());
            },
            tile -> ((TileDynamoMJ) tile).getPower() + ((TileDynamoMJ) tile).getCurrentFe()
                + ((TileDynamoMJ) tile).getMjBattery().getStored(),
            "power", "currentFe", "mjStored"),
        new Machine("engine_stone", BCEnergyBlocks.ENGINE_STONE::get,
            tile -> load(tile, longs("power", CHARGE)),
            tile -> ((TileEngineBase_BC8) tile).getPower(),
            "power"),
        new Machine("engine_iron", BCEnergyBlocks.ENGINE_IRON::get,
            tile -> load(tile, longs("power", CHARGE)),
            tile -> ((TileEngineBase_BC8) tile).getPower(),
            "power"),
        new Machine("engine_rf", BCEnergyBlocks.ENGINE_FE::get,
            tile -> {
                load(tile, longs("power", CHARGE));
                ((TileEngineFE) tile).setCurrentFe(TileEngineFE.MAX_FE);
            },
            tile -> ((TileEngineFE) tile).getPower() + ((TileEngineFE) tile).getCurrentFe(),
            "power", "currentFe"),
        new Machine("autoworkbench_item", BCFactoryBlocks.AUTOWORKBENCH_ITEM::get,
            tile -> fill(((TileAutoWorkbenchItems) tile).getBattery()),
            tile -> ((TileAutoWorkbenchItems) tile).getBattery().getStored(),
            "mjStored"),
        new Machine("assembly_table", BCSiliconBlocks.ASSEMBLY_TABLE::get,
            tile -> ((TileLaserTableBase) tile).power = CHARGE,
            tile -> ((TileLaserTableBase) tile).power,
            "power"),
        new Machine("advanced_crafting_table", BCSiliconBlocks.ADVANCED_CRAFTING_TABLE::get,
            tile -> ((TileLaserTableBase) tile).power = CHARGE,
            tile -> ((TileLaserTableBase) tile).power,
            "power"),
        new Machine("integration_table", BCSiliconBlocks.INTEGRATION_TABLE::get,
            tile -> ((TileLaserTableBase) tile).power = CHARGE,
            tile -> ((TileLaserTableBase) tile).power,
            "power"),
        new Machine("programming_table", BCSiliconBlocks.PROGRAMMING_TABLE::get,
            tile -> ((TileLaserTableBase) tile).power = CHARGE,
            tile -> ((TileLaserTableBase) tile).power,
            "power")
    );

    private static BlockEntity newTile(Block block) {
        BlockState state = block.defaultBlockState();
        BlockEntity tile = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, state);
        Assertions.assertNotNull(tile, "no block entity for " + block);
        return tile;
    }

    private static CompoundTag save(BlockEntity tile) {
        return tile.saveWithoutMetadata(vanillaLookup());
    }

    /** The load a placed block entity goes through. */
    private static void load(BlockEntity tile, CompoundTag tag) {
        //? if >=1.21.10 {
        tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, vanillaLookup(), tag));
        //?} else {
        /*tile.loadWithComponents(tag, vanillaLookup());*/
        //?}
    }

    private static SchematicBlockDefault schematic(Block block, CompoundTag tileNbt) {
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.blockState = block.defaultBlockState();
        schematic.placeBlock = block;
        schematic.tileRotation = Rotation.NONE;
        schematic.tileNbt = tileNbt;
        schematic.canBeReplacedWithBlocks.add(block);
        return schematic;
    }

    @TestFactory
    public Stream<DynamicTest> aRebuiltMachineHoldsNoStoredEnergy() {
        return MACHINES.stream().map(machine -> DynamicTest.dynamicTest(machine.name(), () -> check(machine)));
    }

    private static void check(Machine machine) {
        Block block = machine.block().get();

        BlockEntity charged = newTile(block);
        machine.charge().accept(charged);
        CompoundTag scanned = save(charged);
        CompoundTag idle = save(newTile(block));

        // The fixture is real: the scanned data does hold energy, under the keys the machine's row names, and
        // loading it back gives that energy back. Without this the checks below could pass on an empty fixture.
        BlockEntity scannedAgain = newTile(block);
        load(scannedAgain, scanned.copy());
        Assertions.assertTrue(machine.stored().applyAsLong(scannedAgain) > 0,
            machine.name() + ": the scanned machine must hold energy once loaded: " + scanned);
        for (String key : machine.energyKeys()) {
            Assertions.assertTrue(scanned.contains(key) && !scanned.get(key).equals(idle.get(key)),
                machine.name() + ": the tile no longer saves its energy under \"" + key + "\": " + scanned);
        }

        CompoundTag scannedBefore = scanned.copy();
        SchematicBlockDefault schematic = schematic(block, scanned);
        for (boolean includeContents : new boolean[] {true, false}) {
            String mode = machine.name() + (includeContents ? " (contents placed)" : " (contents ignored)");
            CompoundTag placed = schematic.tileNbtToPlace(includeContents);

            BlockEntity rebuilt = newTile(block);
            load(rebuilt, placed.copy());
            Assertions.assertEquals(0L, machine.stored().applyAsLong(rebuilt),
                mode + ": the rebuilt machine must start with no stored energy: " + placed);

            // The energy goes, and nothing else but a working area: whatever else the machine was scanned with is
            // rebuilt as it was. In IGNORE mode
            // the inventories go as well, which is not what is under test here; every other key still has to be kept
            // in that mode, so a regression that drops configuration on the IGNORE path is caught. A machine that
            // works an area drops that too ({@link #aRebuiltMachineHoldsNoWorkingArea} pins it), and is not
            // configuration either.
            List<String> areaKeys = AREAS.stream().filter(area -> area.name().equals(machine.name()))
                .flatMap(area -> area.removed().stream()).toList();
            for (String key : keysOf(scanned)) {
                if (List.of(machine.energyKeys()).contains(key) || areaKeys.contains(key)
                    || (!includeContents && INVENTORY_KEYS.contains(key))) {
                    continue;
                }
                Assertions.assertEquals(scanned.get(key), placed.get(key), mode + ": \"" + key + "\" must be kept");
            }
            for (String key : keysOf(placed)) {
                Assertions.assertTrue(scanned.contains(key), mode + ": \"" + key + "\" appeared from nowhere");
            }
        }
        Assertions.assertEquals(scannedBefore, schematic.tileNbt, "building must not touch the schematic's own data");
    }

    private static List<String> keysOf(CompoundTag tag) {
        //? if >=1.21.10 {
        return new ArrayList<>(tag.keySet());
        //?} else {
        /*return new ArrayList<>(tag.getAllKeys());*/
        //?}
    }

    @Test
    public void aWorkbenchScannedBeforeTheBatteryMigrationDoesNotRebuildItsCharge() {
        // TileAutoWorkbenchBase still reads the legacy "powerStored" when "mjStored" is absent, so a blueprint
        // scanned back then would hand the charge back through that key.
        Block block = BCFactoryBlocks.AUTOWORKBENCH_ITEM.get();
        CompoundTag legacy = longs("powerStored", CHARGE);
        BlockEntity scanned = newTile(block);
        load(scanned, legacy.copy());
        org.junit.jupiter.api.Assumptions.assumeTrue(
            ((TileAutoWorkbenchItems) scanned).getBattery().getStored() > 0, "the legacy key is no longer read");

        CompoundTag placed = schematic(block, legacy).tileNbtToPlace(true);
        BlockEntity rebuilt = newTile(block);
        load(rebuilt, placed.copy());
        Assertions.assertEquals(0L, ((TileAutoWorkbenchItems) rebuilt).getBattery().getStored(), placed.toString());
    }

    /** The MJ in a stripes pipe behaviour's battery, as its own save lays it out. */
    private static long stripesCharge(CompoundTag behaviourSave) {
        return NBTUtilBC.getLong(NBTUtilBC.getCompound(behaviourSave, "battery"), "stored", 0L);
    }

    @Test
    public void aRebuiltStripesPipeKeepsItsKindAndDirectionButNotItsCharge() {
        // A stripes pipe keeps the MJ it was fed in its behaviour's own battery, inside the pipe holder's save. The
        // holder cannot be saved without a world (its item flow writes the game time), so the fixture is the real
        // behaviour's own save under the "pipe"/"beh" layout Pipe.writeToNbt gives it.
        CompoundTag behaviour = new PipeBehaviourStripes(null,
            compound("battery", longs("stored", CHARGE), "direction", NBTUtilBC.writeEnum(Direction.EAST)))
            .writeToNbt();
        Assertions.assertEquals(CHARGE, stripesCharge(behaviour), "the fixture holds the energy: " + behaviour);
        CompoundTag pipe = compound(
            "def", StringTag.valueOf(BCTransportPipes.stripesItem.identifier), "beh", behaviour, "flow", new CompoundTag());
        Block holder = BCTransportBlocks.PIPE_HOLDER.get();

        CompoundTag placed = schematic(holder, compound("pipe", pipe)).tileNbtToPlace(true);

        CompoundTag placedPipe = NBTUtilBC.getCompound(placed, "pipe");
        CompoundTag rebuiltBehaviour = new PipeBehaviourStripes(null, NBTUtilBC.getCompound(placedPipe, "beh"))
            .writeToNbt();
        Assertions.assertEquals(0L, stripesCharge(rebuiltBehaviour), placed.toString());
        Assertions.assertEquals(behaviour.get("direction"), rebuiltBehaviour.get("direction"),
            "the pipe keeps its direction");
        Assertions.assertEquals(pipe.get("def"), placedPipe.get("def"));
        Assertions.assertEquals(pipe.get("flow"), placedPipe.get("flow"));

        // The rule is aimed at every pipe holder: a pipe with no battery gets nothing added to it, and a holder
        // with no pipe at all gets no pipe.
        CompoundTag stonePipe = compound("pipe", compound(
            "def", StringTag.valueOf(BCTransportPipes.stoneItem.identifier), "beh", new CompoundTag()));
        Assertions.assertEquals(stonePipe, schematic(holder, stonePipe).tileNbtToPlace(true));
        Assertions.assertEquals(new CompoundTag(), schematic(holder, new CompoundTag()).tileNbtToPlace(true));
    }

    @Test
    public void aSchematicWithoutTileDataPlacesNone() {
        Assertions.assertNull(schematic(BCBuildersBlocks.QUARRY.get(), null).tileNbtToPlace(true));
    }

    // --- A rebuilt machine holds no working area ---

    /**
     * One machine that works an area it saves as absolute world positions. {@code given} is written by hand with the
     * key names the tile's own {@code writeData} uses (the fixture is checked against that writer below) and gives
     * the machine an area through its own reader. {@code removed} are the keys a rebuilt copy must not carry,
     * {@code notLoaded} keys that may stay in the tag but that the tile's reader no longer reads once {@code removed}
     * are gone (a Builder's numbered path positions follow {@code path_count}), and {@code kept} keys of the same
     * machine that are not its area and must come through untouched.
     */
    private record Area(String name, Supplier<? extends Block> block, CompoundTag given, List<String> removed,
                        List<String> notLoaded, List<String> kept) {}

    private static final String OWNER_ID = "00000000-0000-0000-0000-000000000001";

    private static final List<String> BOX_KEYS = List.of("box_initialized", "box_minX", "box_minY", "box_minZ",
        "box_maxX", "box_maxY", "box_maxZ");

    private static CompoundTag tag(Consumer<CompoundTag> fill) {
        CompoundTag tag = new CompoundTag();
        fill.accept(tag);
        return tag;
    }

    /** The box keys {@code TileFiller}, {@code TileArchitectTable} and {@code TileBuilder} write for a box that is set. */
    private static void putBox(CompoundTag tag) {
        tag.putBoolean("box_initialized", true);
        tag.putInt("box_minX", 100);
        tag.putInt("box_minY", 64);
        tag.putInt("box_minZ", 200);
        tag.putInt("box_maxX", 104);
        tag.putInt("box_maxY", 66);
        tag.putInt("box_maxZ", 204);
    }

    /** {@code OwnerData.writeOwner}'s keys, which every machine but the laser saves. */
    private static void putOwner(CompoundTag tag) {
        tag.putString("ownerUUID", OWNER_ID);
        tag.putString("ownerName", "Steve");
    }

    private static List<String> concat(List<String> first, String... more) {
        List<String> all = new ArrayList<>(first);
        all.addAll(List.of(more));
        return all;
    }

    private static final List<Area> AREAS = List.of(
        // TileFiller.writeData: the box, the flag that says it came from a volume box behind the Filler (false for a
        // Filler placed against one; a copy that kept it would adopt whatever box lies behind it) and the flag that
        // says it finished filling that box (a copy that kept it would stay parked once it had a box of its own), then
        // the pattern, the invert flag and the owner.
        new Area("filler", BCBuildersBlocks.FILLER::get,
            tag(tag -> {
                putBox(tag);
                tag.putBoolean("markerBox", false);
                tag.putBoolean("finished", true);
                tag.putBoolean("inverted", true);
                putOwner(tag);
            }),
            concat(BOX_KEYS, "markerBox", "finished"), List.of(),
            List.of("inverted", "patternStatement")),
        // TileArchitectTable.writeData: the box, the scan flags that belong to it, then the table's own settings.
        new Area("architect", BCBuildersBlocks.ARCHITECT::get,
            tag(tag -> {
                putBox(tag);
                tag.putBoolean("isValid", true);
                tag.putBoolean("scanning", true);
                tag.putBoolean("scanRefused", true);
                tag.putBoolean("markerBox", true);
                tag.putString("name", "tower");
                putOwner(tag);
            }),
            concat(BOX_KEYS, "isValid", "scanning", "scanRefused"), List.of(),
            List.of("name", "markerBox", "snapshotType")),
        // TileBuilder.writeData: the box, the path of base positions (a count, then three ints per position) and the
        // index into it, beside the turn and settings that are the Builder's own.
        new Area("builder", BCBuildersBlocks.BUILDER::get,
            tag(tag -> {
                putBox(tag);
                tag.putInt("path_count", 2);
                tag.putInt("path_0_x", 100);
                tag.putInt("path_0_y", 64);
                tag.putInt("path_0_z", 200);
                tag.putInt("path_1_x", 100);
                tag.putInt("path_1_y", 64);
                tag.putInt("path_1_z", 210);
                tag.putInt("currentBasePosIndex", 1);
                tag.putInt("rotation", Rotation.CLOCKWISE_90.ordinal());
                tag.putBoolean("canExcavate", false);
                putOwner(tag);
            }),
            concat(BOX_KEYS, "path_count", "currentBasePosIndex"),
            List.of("path_0_x", "path_0_y", "path_0_z", "path_1_x", "path_1_y", "path_1_z"),
            List.of("rotation", "canExcavate")),
        // TileQuarry.writeData: the frame, the mining box inside it, the task under way and the drill's position. The
        // quarry checks its boxes on load, but only that the block beside it lies on the frame's edge: a copy built
        // anywhere along that edge would pass and work the original's area, so the rule has to take them out. The
        // tile is at the origin and the frame starts one block east of it, so this fixture loads as a valid quarry.
        new Area("quarry", BCBuildersBlocks.QUARRY::get,
            tag(tag -> {
                tag.putBoolean("frame_init", true);
                tag.putInt("frame_minX", 1);
                tag.putInt("frame_minY", 0);
                tag.putInt("frame_minZ", -2);
                tag.putInt("frame_maxX", 5);
                tag.putInt("frame_maxY", 4);
                tag.putInt("frame_maxZ", 2);
                tag.putBoolean("box_init", true);
                tag.putInt("box_minX", 2);
                tag.putInt("box_minY", -60);
                tag.putInt("box_minZ", -1);
                tag.putInt("box_maxX", 4);
                tag.putInt("box_maxY", 3);
                tag.putInt("box_maxZ", 1);
                tag.putByte("currentTaskId", (byte) 0);
                tag.putInt("task_breakX", 2);
                tag.putInt("task_breakY", 1);
                tag.putInt("task_breakZ", 1);
                tag.putBoolean("hasDrill", true);
                tag.putDouble("drillX", 3.5);
                tag.putDouble("drillY", 2.0);
                tag.putDouble("drillZ", 0.5);
                putOwner(tag);
            }),
            List.of("frame_init", "frame_minX", "frame_minY", "frame_minZ", "frame_maxX", "frame_maxY", "frame_maxZ",
                "box_init", "box_minX", "box_minY", "box_minZ", "box_maxX", "box_maxY", "box_maxZ",
                "currentTaskId", "task_breakX", "task_breakY", "task_breakZ", "hasDrill", "drillX", "drillY", "drillZ"),
            List.of(), List.of()),
        // TileMiner.writeData: the position being mined, the tube length that goes with it and the progress on it.
        new Area("mining_well", BCFactoryBlocks.MINING_WELL::get,
            tag(tag -> {
                tag.putBoolean("hasCurrentPos", true);
                tag.putInt("currentPosX", 100);
                tag.putInt("currentPosY", 20);
                tag.putInt("currentPosZ", 200);
                tag.putInt("wantedLength", 12);
                tag.putInt("progress", 5);
                putOwner(tag);
            }),
            List.of("hasCurrentPos", "currentPosX", "currentPosY", "currentPosZ", "wantedLength", "progress"),
            List.of(), List.of()),
        // TileLaser.writeData: where the beam ends (laser_*, as doubles) and the cell it targets.
        new Area("laser", BCSiliconBlocks.LASER::get,
            tag(tag -> {
                tag.putDouble("laser_x", 100.5);
                tag.putDouble("laser_y", 64.5);
                tag.putDouble("laser_z", 200.5);
                tag.putInt("target_x", 100);
                tag.putInt("target_y", 64);
                tag.putInt("target_z", 200);
                tag.putBoolean("has_target", true);
            }),
            List.of("laser_x", "laser_y", "laser_z", "target_x", "target_y", "target_z", "has_target"),
            List.of(), List.of("avg_power")));

    @TestFactory
    public Stream<DynamicTest> aRebuiltMachineHoldsNoWorkingArea() {
        return AREAS.stream().map(area -> DynamicTest.dynamicTest(area.name(), () -> checkNoWorkingArea(area)));
    }

    private static void checkNoWorkingArea(Area area) throws Exception {
        Block block = area.block().get();
        CompoundTag fresh = save(newTile(block));

        // The fixture is real: the machine loads the area and saves it back under these keys, with a value a machine
        // nobody gave an area does not have. Without this the checks below could pass on keys the tile never writes.
        BlockEntity working = newTile(block);
        load(working, area.given().copy());
        CompoundTag scanned = save(working);
        for (String key : area.removed()) {
            Assertions.assertTrue(scanned.contains(key) && !scanned.get(key).equals(fresh.get(key)),
                area.name() + ": the tile no longer saves its working area under \"" + key + "\": " + scanned);
        }
        for (String key : area.kept()) {
            Assertions.assertTrue(scanned.contains(key),
                area.name() + ": the tile no longer saves \"" + key + "\": " + scanned);
        }
        // The owner is no area, but it is the one other thing every one of these fixtures names: a machine that saves
        // it (all but the laser) must have it in the scan, and a copy must not carry it (the same rule
        // aRebuiltMachineIsNotOwnedByTheBlueprintsOwner checks for the machines of the energy list).
        boolean owned = area.given().contains("ownerUUID");
        for (String key : OWNER_KEYS) {
            Assertions.assertEquals(owned, scanned.contains(key),
                area.name() + ": the scan must hold the owner under \"" + key + "\" exactly when the tile saves one: "
                    + scanned);
        }

        CompoundTag scannedBefore = scanned.copy();
        SchematicBlockDefault schematic = schematic(block, scanned);
        for (boolean includeContents : new boolean[] {true, false}) {
            String mode = area.name() + (includeContents ? " (contents placed)" : " (contents ignored)");
            CompoundTag placed = schematic.tileNbtToPlace(includeContents);

            for (String key : area.removed()) {
                Assertions.assertFalse(placed.contains(key), mode + ": \"" + key + "\" must not be built: " + placed);
            }
            for (String key : OWNER_KEYS) {
                Assertions.assertFalse(placed.contains(key),
                    mode + ": the blueprint's owner \"" + key + "\" must not be built: " + placed);
            }
            for (String key : area.kept()) {
                Assertions.assertEquals(scannedBefore.get(key), placed.get(key), mode + ": \"" + key + "\" must be kept");
            }

            // The placed tile starts as one nobody gave an area, and keeps the rest of what it was scanned with.
            BlockEntity rebuilt = newTile(block);
            load(rebuilt, placed.copy());
            CompoundTag reloaded = save(rebuilt);
            for (String key : concat(area.removed(), area.notLoaded().toArray(String[]::new))) {
                Assertions.assertEquals(fresh.get(key), reloaded.get(key),
                    mode + ": \"" + key + "\" must read back as it does on a machine with no area: " + reloaded);
            }
            for (String key : area.kept()) {
                Assertions.assertEquals(scannedBefore.get(key), reloaded.get(key),
                    mode + ": \"" + key + "\" must survive the load");
            }
            for (String key : OWNER_KEYS) {
                Assertions.assertFalse(reloaded.contains(key),
                    mode + ": a rebuilt machine must have no owner of its own to save (\"" + key + "\"): " + reloaded);
            }
        }
        Assertions.assertEquals(scannedBefore, schematic.tileNbt, "building must not touch the schematic's own data");

        // A blueprint saved before the keys were dropped holds them in its stored tag, and the stored form of this one
        // is exactly that: reading it back and building it must give what building the capture gives.
        // Reading it back runs the capture's own sanitizing (a Filler's or Builder's queued tasks go), so the scan is
        // sanitized the same way before the two are compared.
        CompoundTag expected = schematic(block,
            SchematicBlockDefault.sanitizeTileNbt(scannedBefore.copy(), block.defaultBlockState())).tileNbtToPlace(true);
        SchematicBlockDefault stored = new SchematicBlockDefault();
        stored.deserializeNbt(schematic.serializeNbt());
        for (String key : area.removed()) {
            Assertions.assertTrue(stored.tileNbt.contains(key),
                area.name() + ": the stored blueprint should still hold \"" + key + "\": " + stored.tileNbt);
        }
        Assertions.assertEquals(expected, stored.tileNbtToPlace(true),
            area.name() + ": a stored blueprint must build without the working area too");
        for (String key : OWNER_KEYS) {
            Assertions.assertEquals(owned, stored.tileNbt.contains(key),
                area.name() + ": the stored blueprint still holds the owner it was scanned with (\"" + key + "\"): "
                    + stored.tileNbt);
        }
    }

    // --- A rebuilt block is not owned by the blueprint's owner ---

    private static final List<String> OWNER_KEYS = List.of("ownerUUID", "ownerName");

    private static final UUID BLUEPRINT_OWNER = UUID.fromString(OWNER_ID);

    /**
     * Every machine and pipe a blueprint rebuilds belongs to whoever builds it (the Builder stamps its own owner on
     * it, or none), so what the blueprint holds of the owner it was scanned from never reaches the placed tag: a
     * machine loading it would otherwise act (dig, place, ask claim mods) as someone who never built it. Each machine
     * of the energy list is given an owner, scanned, and built in both container modes: the built tag has neither
     * owner key, is the very tag the same scan without its owner builds (what else the rules take out goes either
     * way), and loads into a machine with no owner; the stored schematic is not touched. A tile that saves no owner
     * has none to strip.
     */
    @Test
    public void aRebuiltMachineIsNotOwnedByTheBlueprintsOwner() {
        List<String> carriers = new ArrayList<>();
        for (Machine machine : MACHINES) {
            Block block = machine.block().get();
            BlockEntity tile = newTile(block);
            if (tile instanceof AbstractBCBlockEntity owned) {
                owned.setOwner(new GameProfile(BLUEPRINT_OWNER, "Steve"));
            }
            CompoundTag scanned = save(tile);
            boolean carries = scanned.contains("ownerUUID");
            if (carries) {
                carriers.add(machine.name());
            }
            CompoundTag scannedBefore = scanned.copy();
            SchematicBlockDefault schematic = schematic(block, scanned);
            for (boolean includeContents : new boolean[] {true, false}) {
                String mode = machine.name() + (includeContents ? " (contents placed)" : " (contents ignored)");
                CompoundTag placed = schematic.tileNbtToPlace(includeContents);
                for (String key : OWNER_KEYS) {
                    Assertions.assertFalse(placed.contains(key),
                        mode + ": the blueprint's owner \"" + key + "\" must not be built: " + placed);
                }
                // The owner is the only difference it makes: a scan of the same machine with no owner builds the
                // same tag (what else the rules take out, the energy and the area, goes either way).
                CompoundTag ownerless = scannedBefore.copy();
                for (String key : OWNER_KEYS) {
                    ownerless.remove(key);
                }
                Assertions.assertEquals(schematic(block, ownerless).tileNbtToPlace(includeContents), placed,
                    mode + ": building the scan must give what building it without its owner gives");
                BlockEntity rebuilt = newTile(block);
                load(rebuilt, placed.copy());
                if (rebuilt instanceof AbstractBCBlockEntity ownable) {
                    Assertions.assertNull(ownable.getOwner(), mode + ": the rebuilt machine must have no owner yet");
                }
            }
            Assertions.assertEquals(scannedBefore, schematic.tileNbt, machine.name()
                + ": building must not touch the schematic's own data");
        }
        // Liveness: a list whose tiles saved no owner at all would pass without testing anything.
        // The machines known to save their owner must be among them: one that stopped saving it would skip the
        // checks above without a word, and would forget its owner on every reload.
        Assertions.assertTrue(carriers.containsAll(
            List.of("quarry", "filler", "builder", "chute", "distiller", "mining_well", "engine_stone")),
            "these machines must save an owner once given one, the ones that did: " + carriers);
    }

    /** A pipe holder saves its owner under the same two keys, beside the pipe, and a built pipe holds none. */
    @Test
    public void aRebuiltPipeIsNotOwnedByTheBlueprintsOwner() {
        Block holder = BCTransportBlocks.PIPE_HOLDER.get();
        CompoundTag stonePipe = compound("pipe", compound(
            "def", StringTag.valueOf(BCTransportPipes.stoneItem.identifier), "beh", new CompoundTag()));
        CompoundTag scanned = stonePipe.copy();
        putOwner(scanned);

        // The fixture is real: a pipe holder reads that owner back from these keys.
        BlockEntity probe = newTile(holder);
        load(probe, tag(SchematicReplaceNbtTester::putOwner));
        Assertions.assertNotNull(((TilePipeHolder) probe).getOwner(),
            "a pipe holder must read its owner from ownerUUID/ownerName");

        for (boolean includeContents : new boolean[] {true, false}) {
            Assertions.assertEquals(stonePipe, schematic(holder, scanned).tileNbtToPlace(includeContents),
                "a rebuilt pipe keeps everything but the owner");
        }
        Assertions.assertTrue(scanned.contains("ownerUUID"), "building must not touch the schematic's own data");
    }

    /** The Filler's retired save shape held the owner in an {@code owner} child, which it still reads as a fallback. */
    @Test
    public void aRebuiltFillerLeavesTheRetiredOwnerChildBehind() {
        Block filler = BCBuildersBlocks.FILLER.get();
        CompoundTag legacy = compound("owner", compound(
            "uuid", StringTag.valueOf(OWNER_ID), "name", StringTag.valueOf("Steve")));

        BlockEntity probe = newTile(filler);
        load(probe, legacy.copy());
        Assertions.assertNotNull(((TileFiller) probe).getOwner(), "the Filler must still read the retired owner child");

        CompoundTag placed = schematic(filler, legacy).tileNbtToPlace(true);
        Assertions.assertFalse(placed.contains("owner"), "the retired owner child must not be built: " + placed);
        BlockEntity rebuilt = newTile(filler);
        load(rebuilt, placed.copy());
        Assertions.assertNull(((TileFiller) rebuilt).getOwner(), "the rebuilt Filler must have no owner");
    }

    /**
     * Only this mod's block entities are touched: another mod's tile may use these key names for something of its
     * own, and its data is not ours to edit. A chest stands for it, with the keys on it.
     */
    @Test
    public void aBlockOfAnotherModKeepsKeysThatLookLikeAnOwner() {
        CompoundTag tag = tag(SchematicReplaceNbtTester::putOwner);
        tag.put("owner", compound("uuid", StringTag.valueOf(OWNER_ID), "name", StringTag.valueOf("Steve")));
        CompoundTag placed = schematic(Blocks.CHEST, tag).tileNbtToPlace(true);
        for (String key : List.of("ownerUUID", "ownerName", "owner")) {
            Assertions.assertEquals(tag.get(key), placed.get(key), "\"" + key + "\" is not ours to remove: " + placed);
        }
    }

    // --- NBTUtilBC.merge: 1.12.2's replaceNbt semantics ---

    private static CompoundTag compound(Object... keysAndValues) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            tag.put((String) keysAndValues[i], (Tag) keysAndValues[i + 1]);
        }
        return tag;
    }

    private static LongTag num(long value) {
        return LongTag.valueOf(value);
    }

    @Test
    public void aNullInTheSourceRemovesTheKeyAndTheRestMergesRecursively() {
        CompoundTag destination = compound(
            "kept", num(1),
            "gone", num(2),
            "nested", compound("stored", num(5), "capacity", num(9)),
            "replaced", StringTag.valueOf("old"));
        CompoundTag source = compound(
            "gone", NBTUtilBC.NBT_NULL,
            "nested", compound("stored", NBTUtilBC.NBT_NULL),
            "replaced", compound("now", num(3)),
            "added", num(4));

        Tag merged = NBTUtilBC.merge(destination, source);

        Assertions.assertEquals(
            compound(
                "kept", num(1),
                "nested", compound("capacity", num(9)),
                "replaced", compound("now", num(3)),
                "added", num(4)),
            merged);
    }

    @Test
    public void aNullForAKeyThatIsNotThereLeaksNothingIntoTheResult() {
        CompoundTag destination = compound("kept", num(1));
        CompoundTag source = compound(
            "absent", NBTUtilBC.NBT_NULL,
            "fresh", compound("inner", num(2), "dropped", NBTUtilBC.NBT_NULL),
            "phantom", compound("deeper", compound("battery", NBTUtilBC.NBT_NULL)),
            "explicit", new CompoundTag());

        // "phantom" holds nothing but removals for a key the destination lacks: it adds no empty shell, where an
        // empty compound written on purpose ("explicit") is kept.
        Assertions.assertEquals(
            compound("kept", num(1), "fresh", compound("inner", num(2)), "explicit", new CompoundTag()),
            NBTUtilBC.merge(destination, source));
    }

    @Test
    public void aScalarOrListInTheSourceReplacesTheDestinationsWholesale() {
        ListTag oldList = new ListTag();
        oldList.add(num(1));
        oldList.add(num(2));
        ListTag newList = new ListTag();
        newList.add(num(3));
        CompoundTag destination = compound("list", oldList, "value", compound("a", num(1)));
        CompoundTag source = compound("list", newList, "value", num(7));

        Assertions.assertEquals(compound("list", newList, "value", num(7)), NBTUtilBC.merge(destination, source));
    }

    @Test
    public void mergingNeverMutatesOrSharesWithEitherSide() {
        ListTag sourceList = new ListTag();
        sourceList.add(num(1));
        CompoundTag destination = compound("nested", compound("a", num(1)));
        CompoundTag source = compound("nested", compound("b", num(2)), "list", sourceList);
        CompoundTag destinationBefore = destination.copy();
        CompoundTag sourceBefore = source.copy();

        CompoundTag merged = (CompoundTag) NBTUtilBC.merge(destination, source);
        ((ListTag) merged.get("list")).add(num(99));
        ((CompoundTag) merged.get("nested")).putLong("c", 3);

        Assertions.assertEquals(destinationBefore, destination);
        Assertions.assertEquals(sourceBefore, source);
    }

    @Test
    public void everyRuleOfTheMatchSetContributesItsReplaceNbt() {
        // The union of the matching rules' replaceNbt, as 1.12.2 reduced them; a rule without one adds nothing.
        JsonRule first = new JsonRule();
        first.replaceNbt = compound("a", NBTUtilBC.NBT_NULL);
        JsonRule second = new JsonRule();
        second.replaceNbt = compound("b", compound("c", NBTUtilBC.NBT_NULL));
        JsonRule none = new JsonRule();
        CompoundTag nbt = compound("a", num(1), "b", compound("c", num(2), "d", num(3)), "e", num(4));
        CompoundTag before = nbt.copy();

        CompoundTag applied = JsonRule.applyReplaceNbt(nbt, List.of(first, second, none));

        Assertions.assertEquals(compound("b", compound("d", num(3)), "e", num(4)), applied);
        Assertions.assertEquals(before, nbt, "the tag handed in is never mutated");
        Assertions.assertSame(nbt, JsonRule.applyReplaceNbt(nbt, List.of(none)), "no replaceNbt, nothing to copy");
    }
}
