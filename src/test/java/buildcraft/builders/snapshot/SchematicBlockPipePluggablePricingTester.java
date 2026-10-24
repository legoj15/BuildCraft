/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.robotics.RobotStationPluggable;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.plug.PluggableLens;
import buildcraft.silicon.plug.PluggableLightSensor;
import buildcraft.silicon.plug.PluggablePulsar;
import buildcraft.silicon.plug.PluggableTimer;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.pipe.PluggableRegistry;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.plug.PluggablePowerAdaptor;

/**
 * A blueprint charges for the pluggables on a pipe by rebuilding each captured one with no pipe holder and asking it for
 * its pick stack, and the build restores the whole captured {@code plugs} tag. A pluggable that cannot be rebuilt that
 * way used to be left uncosted yet still placed, i.e. placed for free. Now it is neither charged nor placed, so two
 * things are pinned here. First, no pluggable BuildCraft itself registers falls into that bucket, because the removal
 * would silently strip a legitimate gate or facade from a player's blueprint. Second, for one that does, the NBT the
 * build places and the price agree.
 */
public class SchematicBlockPipePluggablePricingTester extends VanillaSetupBaseTester {

    private static final Direction FACE = Direction.UP;

    /** A pipe schematic whose captured tile carries {@code plugs}, shaped as a real capture of a cobble item pipe. */
    private static SchematicBlockPipe pipeWith(CompoundTag plugs) {
        return pipeWith(BCTransportPipes.cobbleItem.identifier, plugs);
    }

    /** The same, for the pipe definition {@code pipeDef}. */
    private static SchematicBlockPipe pipeWith(String pipeDef, CompoundTag plugs) {
        SchematicBlockPipe schematic = new SchematicBlockPipe();
        schematic.blockState = BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState();
        schematic.placeBlock = BCTransportBlocks.PIPE_HOLDER.get();
        schematic.tileRotation = Rotation.NONE;
        CompoundTag tile = new CompoundTag();
        CompoundTag pipe = new CompoundTag();
        pipe.putString("def", pipeDef);
        tile.put("pipe", pipe);
        tile.put("plugs", plugs);
        schematic.tileNbt = tile;
        return schematic;
    }

    /** The {@code plugs} entry the pipe holder writes for {@code plug}: its definition id and its own saved data. */
    private static CompoundTag entryOf(PipePluggable plug) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", plug.definition.identifier.toString());
        entry.put("data", plug.writeToNbt());
        return entry;
    }

    private static CompoundTag plugsOf(String face, CompoundTag entry) {
        CompoundTag plugs = new CompoundTag();
        plugs.put(face, entry);
        return plugs;
    }

    /** The faces that carry a pluggable entry in a tile's {@code plugs} tag. */
    private static List<String> facesIn(CompoundTag tileNbt) {
        CompoundTag plugs = NBTUtilBC.getCompound(tileNbt, "plugs");
        List<String> faces = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if (!NBTUtilBC.getCompound(plugs, face.getName()).isEmpty()) {
                faces.add(face.getName());
            }
        }
        return faces;
    }

    /** What the schematic asks for beyond the pipe itself. */
    private static List<ItemStack> pluggableItems(SchematicBlockPipe schematic) {
        List<ItemStack> required = schematic.computeRequiredItems();
        Assertions.assertFalse(required.isEmpty(), "the pipe itself is always priced");
        return required.subList(1, required.size());
    }

    private static List<PipePluggable> representatives(PluggableDefinition def) {
        List<PipePluggable> out = new ArrayList<>();
        switch (def.identifier.toString()) {
            case "buildcraftunofficial:blocker" -> out.add(new PluggableBlocker(def, null, FACE));
            case "buildcraftunofficial:power_adaptor" -> out.add(new PluggablePowerAdaptor(def, null, FACE));
            case "buildcraftunofficial:pulsar" -> out.add(new PluggablePulsar(def, null, FACE));
            case "buildcraftunofficial:light_sensor" -> out.add(new PluggableLightSensor(def, null, FACE));
            case "buildcraftunofficial:timer" -> out.add(new PluggableTimer(def, null, FACE));
            case "buildcraftunofficial:robot_station" -> out.add(new RobotStationPluggable(def, null, FACE));
            case "buildcraftunofficial:gate" -> {
                // Every gate the assembly table can make: a modifier only exists on a material that can take one.
                for (EnumGateLogic logic : EnumGateLogic.VALUES) {
                    for (EnumGateMaterial material : EnumGateMaterial.VALUES) {
                        for (EnumGateModifier modifier : EnumGateModifier.VALUES) {
                            if (material.canBeModified || modifier == EnumGateModifier.NO_MODIFIER) {
                                out.add(new PluggableGate(def, null, FACE, new GateVariant(logic, material, modifier)));
                            }
                        }
                    }
                }
            }
            case "buildcraftunofficial:lens" -> {
                out.add(new PluggableLens(def, null, FACE, null, false));
                out.add(new PluggableLens(def, null, FACE, null, true));
                for (DyeColor colour : DyeColor.values()) {
                    out.add(new PluggableLens(def, null, FACE, colour, false));
                    out.add(new PluggableLens(def, null, FACE, colour, true));
                }
            }
            case "buildcraftunofficial:facade" -> {
                FacadeStateManager.ensureInitialized();
                FacadeBlockStateInfo stone = new FacadeBlockStateInfo(
                    Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE), ImmutableSet.of());
                FacadeBlockStateInfo glass = new FacadeBlockStateInfo(
                    Blocks.GLASS.defaultBlockState(), new ItemStack(Items.GLASS), ImmutableSet.of());
                out.add(new PluggableFacade(def, null, FACE, FacadeInstance.createSingle(stone, false)));
                out.add(new PluggableFacade(def, null, FACE, FacadeInstance.createSingle(stone, true)));
                out.add(new PluggableFacade(def, null, FACE, new FacadeInstance(new FacadePhasedState[] {
                    new FacadePhasedState(stone, null), new FacadePhasedState(glass, DyeColor.RED)}, false)));
            }
            default -> Assertions.fail("No representative instance for the registered pluggable " + def.identifier
                + ": add one here, so that its blueprint pricing is checked (a pluggable that cannot be priced "
                + "holder-free is removed from the blueprint it is built from).");
        }
        return out;
    }

    @Test
    public void everyInTreePluggablePricesHolderFree() {
        List<String> checked = new ArrayList<>();
        for (PluggableDefinition def : PluggableRegistry.INSTANCE.getDefinitions()) {
            List<PipePluggable> plugs = representatives(def);
            Assertions.assertFalse(plugs.isEmpty(), "no instance for " + def.identifier);
            for (PipePluggable plug : plugs) {
                ItemStack expected = plug.getPickStack();
                Assertions.assertFalse(expected.isEmpty(), def.identifier + " has no pick stack of its own");

                SchematicBlockPipe schematic = pipeWith(plugsOf(FACE.getName(), entryOf(plug)));
                List<ItemStack> priced = pluggableItems(schematic);
                Assertions.assertEquals(1, priced.size(),
                    def.identifier + " must price to exactly its own item when rebuilt holder-free, got " + priced);
                Assertions.assertTrue(ItemStack.isSameItemSameComponents(expected, priced.get(0)),
                    def.identifier + " priced to " + priced.get(0) + " instead of " + expected);

                // The price and the NBT the build places agree: a priceable pluggable is placed exactly as captured.
                Assertions.assertEquals(schematic.tileNbt,
                    SchematicBlockPipe.withoutUnpriceablePluggables(schematic.tileNbt),
                    def.identifier + " is priced, so the NBT the build places must be what was captured");
            }
            checked.add(def.identifier.toString());
        }
        // Not vacuous: one pluggable from each module that registers them, so a registry that silently came up empty
        // (or a module whose registration never ran in this environment) cannot pass for "all of them price".
        for (String id : List.of("blocker", "facade", "gate", "robot_station")) {
            Assertions.assertTrue(checked.contains("buildcraftunofficial:" + id), id + " is not registered: " + checked);
        }
    }

    /** One pipe carrying a priced blocker, a priced gate, a gate that cannot be rebuilt, and an unknown pluggable. */
    private static SchematicBlockPipe pipeWithUnpriceablePluggables() {
        PluggableDefinition gateDef = PluggableRegistry.INSTANCE.getDefinition(
            Identifier.parse("buildcraftunofficial:gate"));
        PluggableDefinition blockerDef = PluggableRegistry.INSTANCE.getDefinition(
            Identifier.parse("buildcraftunofficial:blocker"));
        Assertions.assertNotNull(gateDef);
        Assertions.assertNotNull(blockerDef);

        CompoundTag plugs = new CompoundTag();
        plugs.put(Direction.UP.getName(), entryOf(new PluggableBlocker(blockerDef, null, Direction.UP)));
        plugs.put(Direction.EAST.getName(), entryOf(new PluggableGate(gateDef, null, Direction.EAST,
            new GateVariant(EnumGateLogic.OR, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND))));

        plugs.put(Direction.NORTH.getName(), unrebuildableGateEntry());
        plugs.put(Direction.SOUTH.getName(), unknownPluggableEntry());
        return pipeWith(plugs);
    }

    /**
     * A gate entry whose pluggable cannot be rebuilt. A clay brick gate holds one slot and a quartz modifier halves
     * the slots: zero slots, so constructing it fails (its connection array would be -1 long). Not something a player
     * can make; something a hand-made or damaged blueprint can carry. Reading it into a live pipe holder fails the
     * same way.
     */
    static CompoundTag unrebuildableGateEntry() {
        CompoundTag variant = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK,
            EnumGateModifier.NO_MODIFIER).writeToNBT();
        variant.putByte("modifier", (byte) EnumGateModifier.QUARTZ.ordinal());
        CompoundTag logic = new CompoundTag();
        logic.put("variant", variant);
        CompoundTag data = new CompoundTag();
        data.put("data", logic);
        CompoundTag entry = new CompoundTag();
        entry.putString("id", "buildcraftunofficial:gate");
        entry.put("data", data);
        return entry;
    }

    /** An entry naming a pluggable no loaded mod registers (an add-on that was removed since the capture). */
    static CompoundTag unknownPluggableEntry() {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", "buildcraftunofficial:no_such_pluggable");
        entry.put("data", new CompoundTag());
        return entry;
    }

    @Test
    public void aPluggableThatCannotBePricedIsNeitherChargedNorPlaced() {
        SchematicBlockPipe schematic = pipeWithUnpriceablePluggables();
        CompoundTag before = schematic.tileNbt.copy();

        List<ItemStack> priced = pluggableItems(schematic);
        Assertions.assertEquals(2, priced.size(), "the blocker and the gold gate only: " + priced);

        CompoundTag placedTile = SchematicBlockPipe.withoutUnpriceablePluggables(schematic.tileNbt);
        CompoundTag placed = NBTUtilBC.getCompound(placedTile, "plugs");
        List<String> placedFaces = facesIn(placedTile);
        Assertions.assertEquals(List.of("east", "up"), placedFaces.stream().sorted().toList(),
            "exactly the faces that were priced are placed; the unrebuildable gate on north and the unknown "
                + "pluggable on south are left off");
        Assertions.assertEquals(priced.size(), placedFaces.size(), "everything placed was charged for");
        Assertions.assertEquals("buildcraftunofficial:blocker",
            NBTUtilBC.getString(NBTUtilBC.getCompound(placed, "up"), "id", ""));
        Assertions.assertEquals("buildcraftunofficial:gate",
            NBTUtilBC.getString(NBTUtilBC.getCompound(placed, "east"), "id", ""));

        // Only the placed copy loses them: the blueprint itself keeps what it was captured with, so a pluggable
        // from an add-on that is installed again later is still in the file.
        Assertions.assertEquals(before, schematic.tileNbt, "the stored blueprint data must not be edited");
    }

    @Test
    public void aPipeWhoseEveryPluggableIsUnpriceableIsPlacedWithNone() {
        SchematicBlockPipe schematic = pipeWith(plugsOf("down", unknownPluggableEntry()));
        CompoundTag placed = SchematicBlockPipe.withoutUnpriceablePluggables(schematic.tileNbt);
        Assertions.assertEquals(List.of(), facesIn(placed), "nothing priced, so no face keeps a pluggable");
        Assertions.assertEquals(schematic.tileNbt.get("pipe"), placed.get("pipe"), "the pipe itself is untouched");
    }

    @Test
    public void aPipeWhoseDefinitionIsMissingStillChargesForItsPluggables() {
        // The holder is placed (priced as the generic holder item) and its plugs load regardless of the pipe, so they
        // are charged for like any other.
        PluggableDefinition blockerDef = PluggableRegistry.INSTANCE.getDefinition(
            Identifier.parse("buildcraftunofficial:blocker"));
        SchematicBlockPipe schematic = pipeWith("buildcraftunofficial:removed_addon_pipe",
            plugsOf("up", entryOf(new PluggableBlocker(blockerDef, null, Direction.UP))));

        List<ItemStack> required = schematic.computeRequiredItems();
        Assertions.assertTrue(required.stream().anyMatch(stack -> stack.is(BCTransportItems.PLUG_BLOCKER.get())),
            "the blocker is placed with the holder, so it is charged for: " + required);
    }
}
