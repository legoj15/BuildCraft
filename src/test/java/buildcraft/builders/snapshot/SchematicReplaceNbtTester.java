/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

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
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.tile.TileLaser;
import buildcraft.silicon.tile.TileLaserTableBase;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;

/**
 * A blueprint never rebuilds a machine with the stored energy it was scanned with. The scan keeps the machine's whole
 * saved data, so a quarry, laser or engine would be rebuilt holding the power it had; 1.12.2 stripped it with the rule
 * directive {@code replaceNbt}, which this port parsed and never applied. Every case builds its fixture from the real
 * tile's own save (so a rule naming a key the tile no longer writes fails here) and reads the rebuilt data back through
 * the real tile's own load.
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
                // set, because the load writes every key it does not carry back to its default.
                load(tile, longs("currentTaskId", 0, "task_power", CHARGE));
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

            // Only the energy goes: whatever else the machine was scanned with is rebuilt as it was. In IGNORE mode
            // the inventories go as well, which is not what is under test here; every other key still has to be kept
            // in that mode, so a regression that drops configuration on the IGNORE path is caught.
            for (String key : keysOf(scanned)) {
                if (List.of(machine.energyKeys()).contains(key) || (!includeContents && INVENTORY_KEYS.contains(key))) {
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
