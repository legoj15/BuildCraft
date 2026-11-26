/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.transport.EnumWirePart;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.BCSiliconStatements;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateLogic;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.PluggableGate;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.BCTransportStatements;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamondItem;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDirectional;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Anchors the pipe required-item fix ({@link SchematicBlockPipe}). BuildCraft pipes are all one
 * block (the pipe holder) with the type living in block-entity NBT, not the block state. The
 * generic {@link SchematicBlockDefault} resolves a placement's required item via
 * {@code blockState.getBlock().asItem()}, which returns the SAME item for every pipe — so a
 * blueprint of varied pipes used to list a single pipe type in the Builder's resource panel even
 * though each pipe still <em>placed</em> correctly (placement reads the captured BE NBT).
 *
 * <p>This pins, for three pipes spanning all three flow kinds (item / fluid / power), that each
 * captured pipe schematic (a) routes to {@link SchematicBlockPipe} and (b) resolves the correct,
 * <em>distinct</em> pipe item — directly guarding against the "all pipes collapse to one item"
 * regression. The class also covers the captured pipe under rotation: pluggables move to the
 * rotated mounting faces, wires to the rotated corners, a directional or stripes pipe's face, a diamond pipe's filter
 * rows and a gate's statement sides turn with them, with the stored schematic left untouched.
 */
public class SchematicBlockPipeTester {

    /** Place a pipe of the given type, capture it as a schematic, and return its single required item. */
    private static ItemStack requiredItemFor(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        // Initialises the pipe (and its definition) on the BE exactly as a player placement would.
        tile.onPlacedBy(null, new ItemStack(pipeItem));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        SchematicBlockContext context = new SchematicBlockContext(
                level, BlockPos.ZERO, abs, state, state.getBlock());
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(context);
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        List<ItemStack> required = schematic.computeRequiredItems();
        helper.assertTrue(required.size() == 1,
                "A pipe should require exactly one item, got " + required.size() + " for "
                        + BuiltInRegistries.ITEM.getKey(pipeItem));
        return required.get(0);
    }

    /** Each pipe resolves to ITS OWN item, and distinct pipe types stay distinct. */
    public static void pipeSchematicResolvesPerTypeItem(GameTestHelper helper) {
        Item wood = BCTransportItems.PIPE_WOOD_ITEM.get();
        Item goldFluid = BCTransportItems.PIPE_GOLD_FLUID.get();
        Item diamondPower = BCTransportItems.PIPE_DIAMOND_POWER.get();

        ItemStack woodReq = requiredItemFor(helper, new BlockPos(1, 2, 1), wood);
        ItemStack goldReq = requiredItemFor(helper, new BlockPos(2, 2, 1), goldFluid);
        ItemStack diamondReq = requiredItemFor(helper, new BlockPos(3, 2, 1), diamondPower);

        helper.assertTrue(woodReq.is(wood),
                "Wooden item pipe must require the wooden item pipe, got "
                        + BuiltInRegistries.ITEM.getKey(woodReq.getItem()));
        helper.assertTrue(goldReq.is(goldFluid),
                "Gold fluid pipe must require the gold fluid pipe, got "
                        + BuiltInRegistries.ITEM.getKey(goldReq.getItem()));
        helper.assertTrue(diamondReq.is(diamondPower),
                "Diamond power pipe must require the diamond power pipe, got "
                        + BuiltInRegistries.ITEM.getKey(diamondReq.getItem()));

        // The direct regression guard: three different pipe types must not collapse to one item.
        helper.assertFalse(
                woodReq.getItem() == goldReq.getItem()
                        || woodReq.getItem() == diamondReq.getItem()
                        || goldReq.getItem() == diamondReq.getItem(),
                "Distinct pipe types must resolve to distinct required items (the reported 'all pipes are one' bug)");
        helper.succeed();
    }

    /**
     * A pluggable captured on a pipe (here a blocker) must be costed in the required items.
     * {@code build()} restores pluggables from the captured NBT, so the Builder must charge for them
     * rather than placing them for free — the regression this guards.
     */
    public static void pipeSchematicCostsPluggables(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        Item pipeItem = BCTransportItems.PIPE_COBBLE_ITEM.get();
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        // Attach a blocker pluggable (fixed item, holder-free getPickStack) on one face.
        tile.replacePluggable(Direction.UP, new PluggableBlocker(BCTransportPlugs.blocker, tile, Direction.UP));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        List<ItemStack> required = schematic.computeRequiredItems();
        Item blockerItem = BCTransportItems.PLUG_BLOCKER.get();
        boolean hasPipe = required.stream().anyMatch(s -> s.is(pipeItem));
        boolean hasBlocker = required.stream().anyMatch(s -> s.is(blockerItem));
        helper.assertTrue(hasPipe,
                "Required items must include the cobble item pipe, got " + itemNames(required));
        helper.assertTrue(hasBlocker,
                "Required items must include the blocker plug — pluggables must be costed, not placed for free. Got "
                        + itemNames(required));
        helper.succeed();
    }

    /**
     * Pluggables must rotate with the blueprint. A blocker captured on NORTH must move to EAST under
     * a 90° clockwise rotation (otherwise a facade would stay on its captured face after a rotated
     * build and block the wrong pipe connection). getRotated must also leave the original untouched.
     */
    public static void pipeSchematicRotatesPluggableFaces(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        tile.replacePluggable(Direction.NORTH, new PluggableBlocker(BCTransportPlugs.blocker, tile, Direction.NORTH));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        // Sanity: captured on NORTH.
        helper.assertFalse(NBTUtilBC.getCompound(plugsOf(schematic), "north").isEmpty(),
                "Captured pluggable should be on NORTH before rotation");

        // 90° clockwise: NORTH → EAST.
        ISchematicBlock rotated = schematic.getRotated(Rotation.CLOCKWISE_90);
        CompoundTag rotatedPlugs = plugsOf(rotated);
        helper.assertFalse(NBTUtilBC.getCompound(rotatedPlugs, "east").isEmpty(),
                "After CLOCKWISE_90 the pluggable must move to EAST");
        helper.assertTrue(NBTUtilBC.getCompound(rotatedPlugs, "north").isEmpty(),
                "After CLOCKWISE_90 the pluggable must no longer be on NORTH");
        helper.assertTrue(NBTUtilBC.getString(NBTUtilBC.getCompound(rotatedPlugs, "east"), "id", "").contains("blocker"),
                "The rotated EAST entry must still carry the blocker pluggable");

        // getRotated returns a copy — the original schematic's NBT must be untouched.
        helper.assertFalse(NBTUtilBC.getCompound(plugsOf(schematic), "north").isEmpty(),
                "getRotated must not mutate the original schematic (its pluggable should still be on NORTH)");
        helper.succeed();
    }

    /**
     * A pipe's wires sit on the eight corners of the block and are stored by corner in the tile
     * NBT, so a rotated blueprint must move each wire to its rotated corner or the wire run no
     * longer lines up with the rotated pipes. Two colours and two heights, so a swapped colour or
     * a lost y shows. getRotated must also leave the original untouched.
     */
    public static void pipeSchematicRotatesWireCorners(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        tile.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.RED);
        tile.getWireManager().addPart(EnumWirePart.WEST_UP_SOUTH, DyeColor.BLUE);

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        Map<EnumWirePart, DyeColor> captured = wiresOf(schematic);
        helper.assertTrue(captured.equals(wires(EnumWirePart.EAST_DOWN_NORTH, DyeColor.RED,
                EnumWirePart.WEST_UP_SOUTH, DyeColor.BLUE)),
                "Captured wires should be RED on EAST_DOWN_NORTH and BLUE on WEST_UP_SOUTH, got " + captured);

        // Expected corners written out as literals: CLOCKWISE_90 takes EAST to SOUTH and NORTH to EAST,
        // CLOCKWISE_180 takes EAST to WEST and NORTH to SOUTH, COUNTERCLOCKWISE_90 takes EAST to NORTH and NORTH to
        // WEST.
        Map<EnumWirePart, DyeColor> cw90 = wiresOf(schematic.getRotated(Rotation.CLOCKWISE_90));
        helper.assertTrue(cw90.equals(wires(EnumWirePart.EAST_DOWN_SOUTH, DyeColor.RED,
                EnumWirePart.WEST_UP_NORTH, DyeColor.BLUE)),
                "CLOCKWISE_90 must move RED to EAST_DOWN_SOUTH and BLUE to WEST_UP_NORTH, got " + cw90);
        Map<EnumWirePart, DyeColor> cw180 = wiresOf(schematic.getRotated(Rotation.CLOCKWISE_180));
        helper.assertTrue(cw180.equals(wires(EnumWirePart.WEST_DOWN_SOUTH, DyeColor.RED,
                EnumWirePart.EAST_UP_NORTH, DyeColor.BLUE)),
                "CLOCKWISE_180 must move RED to WEST_DOWN_SOUTH and BLUE to EAST_UP_NORTH, got " + cw180);
        Map<EnumWirePart, DyeColor> ccw90 = wiresOf(schematic.getRotated(Rotation.COUNTERCLOCKWISE_90));
        helper.assertTrue(ccw90.equals(wires(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED,
                EnumWirePart.EAST_UP_SOUTH, DyeColor.BLUE)),
                "COUNTERCLOCKWISE_90 must move RED to WEST_DOWN_NORTH and BLUE to EAST_UP_SOUTH, got " + ccw90);
        Map<EnumWirePart, DyeColor> none = wiresOf(schematic.getRotated(Rotation.NONE));
        helper.assertTrue(none.equals(captured),
                "NONE must keep the wires on the corners they were captured on, got " + none);

        // Through the real reader: the rotated schematic built into the arena has its wires on the rotated corners.
        TilePipeHolder built = buildAt(helper, schematic.getRotated(Rotation.CLOCKWISE_90), new BlockPos(3, 2, 1));
        helper.assertTrue(built.getWireManager().getColorOfPart(EnumWirePart.EAST_DOWN_SOUTH) == DyeColor.RED
                        && built.getWireManager().getColorOfPart(EnumWirePart.WEST_UP_NORTH) == DyeColor.BLUE
                        && built.getWireManager().getColorOfPart(EnumWirePart.EAST_DOWN_NORTH) == null
                        && built.getWireManager().getColorOfPart(EnumWirePart.WEST_UP_SOUTH) == null,
                "The pipe built after CLOCKWISE_90 must hold RED on EAST_DOWN_SOUTH and BLUE on WEST_UP_NORTH only");

        helper.assertTrue(wiresOf(schematic).equals(captured),
                "getRotated must not mutate the original schematic (its wires should still be the captured ones)");
        helper.succeed();
    }

    /**
     * The face a directional pipe points at is stored as a compass name, so a rotated blueprint must turn it with the
     * rest of the pipe: an iron pipe captured pointing NORTH points EAST once built after a 90 degree clockwise turn,
     * and one pointing UP still points UP. A stripes pipe keeps its working face in another key and turns it the same
     * way. The tag-level checks cover the other rotations, and getRotated must leave the original untouched.
     */
    public static void pipeSchematicRotatesPipeDirection(GameTestHelper helper) {
        Item iron = BCTransportItems.PIPE_IRON_ITEM.get();
        BlockPos northPos = new BlockPos(1, 2, 1);
        BlockPos upPos = new BlockPos(1, 2, 3);
        BlockPos stripesPos = new BlockPos(1, 2, 5);
        placePipe(helper, northPos, iron);
        placePipe(helper, upPos, iron);
        TilePipeHolder stripesTile = placePipe(helper, stripesPos, BCTransportItems.PIPE_STRIPES_ITEM.get());
        // The stripes pipe's working face has a public setter, so its captured tag comes from the real writer.
        ((PipeBehaviourStripes) stripesTile.getPipe().getBehaviour()).setDirection(Direction.NORTH);
        SchematicBlockPipe north = capture(helper, northPos);
        SchematicBlockPipe up = capture(helper, upPos);
        SchematicBlockPipe stripes = capture(helper, stripesPos);
        // A pipe with no neighbour points nowhere, and PipeBehaviourDirectional.writeToNbt omits "currentDir" then, so
        // the captured tag is given the key it writes for a pipe that points somewhere (a Direction.name() string).
        behaviourTag(north).put("currentDir", NBTUtilBC.writeEnum(Direction.NORTH));
        behaviourTag(up).put("currentDir", NBTUtilBC.writeEnum(Direction.UP));

        TilePipeHolder builtNorth = buildAt(helper, north.getRotated(Rotation.CLOCKWISE_90), new BlockPos(3, 2, 1));
        helper.assertTrue(currentDirOf(builtNorth) == Direction.EAST,
                "A pipe captured pointing NORTH must point EAST after a CLOCKWISE_90 build, got " + currentDirOf(builtNorth));
        TilePipeHolder builtUp = buildAt(helper, up.getRotated(Rotation.CLOCKWISE_90), new BlockPos(3, 2, 3));
        helper.assertTrue(currentDirOf(builtUp) == Direction.UP,
                "A pipe captured pointing UP must still point UP after a CLOCKWISE_90 build, got " + currentDirOf(builtUp));
        TilePipeHolder builtStripes = buildAt(helper, stripes.getRotated(Rotation.CLOCKWISE_90), new BlockPos(3, 2, 5));
        Direction stripesFace = ((PipeBehaviourStripes) builtStripes.getPipe().getBehaviour()).direction;
        helper.assertTrue(stripesFace == Direction.EAST,
                "A stripes pipe captured working NORTH must work EAST after a CLOCKWISE_90 build, got " + stripesFace);

        helper.assertTrue(directionKey(north.getRotated(Rotation.CLOCKWISE_180), "currentDir") == Direction.SOUTH,
                "CLOCKWISE_180 must turn NORTH to SOUTH");
        helper.assertTrue(directionKey(north.getRotated(Rotation.COUNTERCLOCKWISE_90), "currentDir") == Direction.WEST,
                "COUNTERCLOCKWISE_90 must turn NORTH to WEST");
        helper.assertTrue(directionKey(north.getRotated(Rotation.NONE), "currentDir") == Direction.NORTH,
                "NONE must keep the captured direction");
        helper.assertTrue(directionKey(stripes.getRotated(Rotation.CLOCKWISE_90), "direction") == Direction.EAST,
                "A stripes pipe's working face NORTH must become EAST after a CLOCKWISE_90 rotation");
        helper.assertTrue(directionKey(stripes.getRotated(Rotation.CLOCKWISE_180), "direction") == Direction.SOUTH,
                "A stripes pipe's working face NORTH must become SOUTH after a CLOCKWISE_180 rotation");

        helper.assertTrue(directionKey(north, "currentDir") == Direction.NORTH
                        && directionKey(stripes, "direction") == Direction.NORTH,
                "getRotated must not mutate the original schematic (its directions should still be NORTH)");
        helper.succeed();
    }

    /**
     * A diamond (sorting) pipe keeps nine filter slots per face, the row of face f starting at slot 9 * f.ordinal(),
     * so a rotated blueprint must move each row to the rotated face with its column order kept: a marker in the NORTH
     * row's first slot ends up in the EAST row's first slot after a CLOCKWISE_90 build, the UP row stays, and nothing
     * is left behind in the NORTH row. An emerald pipe's single row of nine filters is not per face and is not moved,
     * even when its tag is forged to the diamond pipe's size.
     */
    public static void pipeSchematicRotatesDiamondFilterRows(GameTestHelper helper) {
        int perSide = PipeBehaviourDiamond.FILTERS_PER_SIDE;
        BlockPos diamondPos = new BlockPos(1, 2, 1);
        BlockPos emeraldPos = new BlockPos(1, 2, 3);
        TilePipeHolder diamond = placePipe(helper, diamondPos, BCTransportItems.PIPE_DIAMOND_ITEM.get());
        PipeBehaviourDiamond diamondBehaviour = (PipeBehaviourDiamond) diamond.getPipe().getBehaviour();
        int northRow = perSide * Direction.NORTH.ordinal();
        int upRow = perSide * Direction.UP.ordinal();
        diamondBehaviour.filters.setStackInSlot(northRow, new ItemStack(Items.IRON_INGOT));
        diamondBehaviour.filters.setStackInSlot(northRow + 4, new ItemStack(Items.COAL));
        diamondBehaviour.filters.setStackInSlot(upRow, new ItemStack(Items.GOLD_INGOT));
        SchematicBlockPipe captured = capture(helper, diamondPos);

        TilePipeHolder built = buildAt(helper, captured.getRotated(Rotation.CLOCKWISE_90), new BlockPos(3, 2, 1));
        ItemHandlerSimple filters = ((PipeBehaviourDiamond) built.getPipe().getBehaviour()).filters;
        assertFilters(helper, filters, "built after CLOCKWISE_90", Map.of(
                perSide * Direction.EAST.ordinal(), Items.IRON_INGOT,
                perSide * Direction.EAST.ordinal() + 4, Items.COAL,
                upRow, Items.GOLD_INGOT));

        assertFilters(helper, filtersOf(captured.getRotated(Rotation.CLOCKWISE_180)), "after CLOCKWISE_180", Map.of(
                perSide * Direction.SOUTH.ordinal(), Items.IRON_INGOT,
                perSide * Direction.SOUTH.ordinal() + 4, Items.COAL,
                upRow, Items.GOLD_INGOT));
        assertFilters(helper, filtersOf(captured.getRotated(Rotation.COUNTERCLOCKWISE_90)), "after COUNTERCLOCKWISE_90", Map.of(
                perSide * Direction.WEST.ordinal(), Items.IRON_INGOT,
                perSide * Direction.WEST.ordinal() + 4, Items.COAL,
                upRow, Items.GOLD_INGOT));
        Map<Integer, Item> asCaptured = Map.of(northRow, Items.IRON_INGOT, northRow + 4, Items.COAL, upRow, Items.GOLD_INGOT);
        assertFilters(helper, filtersOf(captured.getRotated(Rotation.NONE)), "after NONE", asCaptured);
        assertFilters(helper, filtersOf(captured), "of the original after rotating", asCaptured);

        TilePipeHolder emeraldTile = placePipe(helper, emeraldPos, BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get());
        ((PipeBehaviourWoodDiamond) emeraldTile.getPipe().getBehaviour()).filters
                .setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        SchematicBlockPipe emerald = capture(helper, emeraldPos);
        CompoundTag emeraldFilters = NBTUtilBC.getCompound(behaviourTag(emerald), "filters").copy();
        helper.assertFalse(emeraldFilters.isEmpty(), "The emerald pipe must have captured its filters");
        helper.assertTrue(emeraldFilters.equals(NBTUtilBC.getCompound(
                        behaviourTag(emerald.getRotated(Rotation.CLOCKWISE_90)), "filters")),
                "An emerald pipe's filters are not per face and must come out of a rotation as captured");
        // The diamond pipe's captured filter tag (fifty-four slots, a marker in its NORTH row) forged onto the emerald pipe.
        CompoundTag forged = NBTUtilBC.getCompound(behaviourTag(captured), "filters").copy();
        behaviourTag(emerald).put("filters", forged);
        helper.assertTrue(forged.equals(NBTUtilBC.getCompound(
                        behaviourTag(emerald.getRotated(Rotation.CLOCKWISE_90)), "filters")),
                "A diamond-sized filter list forged onto an emerald pipe must still come out as captured");
        helper.succeed();
    }

    /**
     * A gate's statements read a neighbouring face, so the logic tag a rotated blueprint carries must name the rotated
     * face: GateLogic.rotateNbt turns each statement's side, and a statement that carries its direction in its identity
     * (the pipe-direction actions) is swapped for the one of the rotated face. The centre, an unknown statement and a
     * malformed one are left as they are, and the input tag is never touched. The end-to-end case captures a gate
     * mounted on a pipe's NORTH face with a sided trigger and a direction action, and checks the rotated blueprint
     * builds the gate on EAST reading EAST.
     */
    public static void pipeSchematicRotatesGateStatementSides(GameTestHelper helper) {
        String lightKind = BCSiliconStatements.TRIGGER_LIGHT_HIGH.getUniqueTag();
        String northAction = BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.NORTH.ordinal()].getUniqueTag();
        String upAction = BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.UP.ordinal()].getUniqueTag();
        String unknownKind = "buildcraftunofficial:no_such_statement";

        CompoundTag logic = new CompoundTag();
        logic.put("trigger[0]", statementTag(lightKind, Direction.NORTH.get3DDataValue()));
        logic.put("trigger[1]", statementTag(lightKind, 6));
        logic.put("trigger[2]", statementTag(lightKind, Direction.UP.get3DDataValue()));
        logic.put("trigger[3]", statementTag(unknownKind, 6));
        CompoundTag withParameter = statementTag(lightKind, Direction.EAST.get3DDataValue());
        CompoundTag parameter = new CompoundTag();
        parameter.putString("kind", "buildcraft:redstoneGateSideOnly");
        parameter.putBoolean("isOn", true);
        withParameter.put("0", parameter);
        logic.put("trigger[4]", withParameter);
        logic.put("action[0]", statementTag(northAction, 6));
        logic.put("action[1]", statementTag(upAction, 6));
        logic.put("action[2]", statementTag(unknownKind, Direction.NORTH.get3DDataValue()));
        logic.put("action[3]", new CompoundTag());
        logic.putShort("connections", (short) 1);
        CompoundTag before = logic.copy();

        helper.assertTrue(GateLogic.rotateNbt(logic, Rotation.NONE) == logic,
                "NONE must return the tag it was given");
        assertGateStatements(helper, GateLogic.rotateNbt(logic, Rotation.CLOCKWISE_90), "CLOCKWISE_90",
                Direction.EAST, Direction.SOUTH);
        assertGateStatements(helper, GateLogic.rotateNbt(logic, Rotation.CLOCKWISE_180), "CLOCKWISE_180",
                Direction.SOUTH, Direction.WEST);
        assertGateStatements(helper, GateLogic.rotateNbt(logic, Rotation.COUNTERCLOCKWISE_90), "COUNTERCLOCKWISE_90",
                Direction.WEST, Direction.NORTH);
        helper.assertTrue(logic.equals(before), "rotateNbt must not mutate the tag it was given");

        // End to end: a gate on the NORTH face, captured and rotated, builds on the EAST face reading EAST.
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, pipePos, BCTransportItems.PIPE_COBBLE_ITEM.get());
        tile.replacePluggable(Direction.NORTH, new PluggableGate(BCSiliconPlugs.gate, tile, Direction.NORTH,
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER)));
        SchematicBlockPipe captured = capture(helper, pipePos);
        // PluggableGate.writeToNbt stores GateLogic.writeToNbt() under "data" of the entry's "data": plugs.<face>.data.data.
        CompoundTag capturedLogic = gateLogicOf(captured, "north");
        helper.assertFalse(capturedLogic.isEmpty(), "The captured gate must carry its logic tag");
        capturedLogic.put("trigger[0]", statementTag(lightKind, Direction.NORTH.get3DDataValue()));
        capturedLogic.put("action[0]", statementTag(northAction, 6));

        SchematicBlockPipe rotated = (SchematicBlockPipe) captured.getRotated(Rotation.CLOCKWISE_90);
        helper.assertTrue(NBTUtilBC.getCompound(plugsOf(rotated), "north").isEmpty()
                        && !NBTUtilBC.getCompound(plugsOf(rotated), "east").isEmpty(),
                "The gate must move from NORTH to EAST");
        CompoundTag rotatedLogic = gateLogicOf(rotated, "east");
        helper.assertTrue(sideOf(rotatedLogic, "trigger[0]") == Direction.EAST.get3DDataValue(),
                "The gate's trigger side must follow its blueprint to EAST, got " + sideOf(rotatedLogic, "trigger[0]"));
        helper.assertTrue(kindOf(rotatedLogic, "action[0]").equals(
                        BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()].getUniqueTag()),
                "The gate's direction action must follow its blueprint to EAST, got " + kindOf(rotatedLogic, "action[0]"));
        helper.assertTrue(sideOf(gateLogicOf(captured, "north"), "trigger[0]") == Direction.NORTH.get3DDataValue(),
                "getRotated must not mutate the original schematic (its trigger should still read NORTH)");

        TilePipeHolder built = buildAt(helper, rotated, new BlockPos(3, 2, 1));
        helper.assertTrue(built.getPluggable(Direction.EAST) instanceof PluggableGate,
                "The rotated gate must be built on the EAST face");
        PluggableGate gate = (PluggableGate) built.getPluggable(Direction.EAST);
        helper.assertTrue(gate.logic.statements[0].trigger.get() != null
                        && gate.logic.statements[0].trigger.get().sourcePart == EnumPipePart.EAST,
                "The built gate's trigger must read the EAST face");
        helper.assertTrue(gate.logic.statements[0].action.get() != null
                        && gate.logic.statements[0].action.get().delegate
                                == BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()],
                "The built gate's action must be the EAST pipe-direction action");
        helper.succeed();
    }

    /** One statement as {@code TriggerType}/{@code ActionType} save it: the {@code s} compound of kind and side. */
    private static CompoundTag statementTag(String kind, int side) {
        CompoundTag identity = new CompoundTag();
        identity.putString("kind", kind);
        identity.putByte("side", (byte) side);
        CompoundTag statement = new CompoundTag();
        statement.put("s", identity);
        return statement;
    }

    private static int sideOf(CompoundTag logic, String key) {
        return NBTUtilBC.getByte(NBTUtilBC.getCompound(NBTUtilBC.getCompound(logic, key), "s"), "side", (byte) -1);
    }

    private static String kindOf(CompoundTag logic, String key) {
        return NBTUtilBC.getString(NBTUtilBC.getCompound(NBTUtilBC.getCompound(logic, key), "s"), "kind", "");
    }

    /**
     * Checks the logic tag of {@link #pipeSchematicRotatesGateStatementSides} after a rotation: the sided statements
     * turn (NORTH to {@code northBecomes}, EAST to {@code eastBecomes}), the centre and UP stay, the NORTH
     * pipe-direction action becomes the one of {@code northBecomes} and the UP one stays, an unknown statement keeps
     * its kind, a parameter that carries no direction is not rewritten, and what is not a statement is left alone.
     */
    private static void assertGateStatements(GameTestHelper helper, CompoundTag rotated, String label,
                                             Direction northBecomes, Direction eastBecomes) {
        helper.assertTrue(sideOf(rotated, "trigger[0]") == northBecomes.get3DDataValue(),
                label + ": a trigger on NORTH must read " + northBecomes + ", got side " + sideOf(rotated, "trigger[0]"));
        helper.assertTrue(sideOf(rotated, "trigger[1]") == 6,
                label + ": a centre trigger must stay on the centre, got side " + sideOf(rotated, "trigger[1]"));
        helper.assertTrue(sideOf(rotated, "trigger[2]") == Direction.UP.get3DDataValue(),
                label + ": a trigger on UP must stay on UP, got side " + sideOf(rotated, "trigger[2]"));
        helper.assertTrue(sideOf(rotated, "trigger[4]") == eastBecomes.get3DDataValue(),
                label + ": a trigger on EAST must read " + eastBecomes + ", got side " + sideOf(rotated, "trigger[4]"));
        helper.assertTrue(kindOf(rotated, "trigger[0]").equals(BCSiliconStatements.TRIGGER_LIGHT_HIGH.getUniqueTag()),
                label + ": a light trigger keeps its kind, got " + kindOf(rotated, "trigger[0]"));
        helper.assertTrue(NBTUtilBC.getCompound(rotated, "trigger[4]").equals(statementTagWithParameter(eastBecomes)),
                label + ": a parameter that carries no direction must be left as it was");
        helper.assertTrue(kindOf(rotated, "action[0]").equals(
                        BCTransportStatements.ACTION_PIPE_DIRECTION[northBecomes.ordinal()].getUniqueTag()),
                label + ": the NORTH pipe-direction action must become the " + northBecomes + " one, got "
                        + kindOf(rotated, "action[0]"));
        helper.assertTrue(kindOf(rotated, "action[1]").equals(
                        BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.UP.ordinal()].getUniqueTag()),
                label + ": the UP pipe-direction action must stay, got " + kindOf(rotated, "action[1]"));
        helper.assertTrue(NBTUtilBC.getCompound(rotated, "trigger[3]")
                        .equals(statementTag("buildcraftunofficial:no_such_statement", 6)),
                label + ": an unknown statement on the centre must be left as it was");
        helper.assertTrue(kindOf(rotated, "action[2]").equals("buildcraftunofficial:no_such_statement")
                        && sideOf(rotated, "action[2]") == northBecomes.get3DDataValue(),
                label + ": an unknown statement keeps its kind and its side still turns, got side " + sideOf(rotated, "action[2]"));
        helper.assertTrue(NBTUtilBC.getCompound(rotated, "action[3]").isEmpty(),
                label + ": a statement tag without an identity must be left as it was");
        helper.assertTrue(NBTUtilBC.getShort(rotated, "connections", (short) 0) == 1,
                label + ": the rest of the logic tag must be left as it was");
    }

    /** The {@code trigger[4]} of {@link #pipeSchematicRotatesGateStatementSides} once it reads {@code side}. */
    private static CompoundTag statementTagWithParameter(Direction side) {
        CompoundTag statement = statementTag(BCSiliconStatements.TRIGGER_LIGHT_HIGH.getUniqueTag(), side.get3DDataValue());
        CompoundTag parameter = new CompoundTag();
        parameter.putString("kind", "buildcraft:redstoneGateSideOnly");
        parameter.putBoolean("isOn", true);
        statement.put("0", parameter);
        return statement;
    }

    /** The gate logic tag of a captured pipe schematic's gate on {@code face} (the live tag: edits reach the schematic). */
    private static CompoundTag gateLogicOf(ISchematicBlock schematic, String face) {
        return NBTUtilBC.getCompound(NBTUtilBC.getCompound(NBTUtilBC.getCompound(plugsOf(schematic), face), "data"), "data");
    }

    /** Places a pipe of the given type in the cell and returns its holder, set up as a player's placement would. */
    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** Captures the pipe in the cell the way the Architect does, through the real factory chain. */
    private static SchematicBlockPipe capture(GameTestHelper helper, BlockPos relPos) {
        ISchematicBlock schematic = scan(helper.getLevel(), helper.absolutePos(relPos));
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());
        return (SchematicBlockPipe) schematic;
    }

    /** Builds the schematic into the empty cell as the Builder does and returns the holder that was placed. */
    private static TilePipeHolder buildAt(GameTestHelper helper, ISchematicBlock schematic, BlockPos relPos) {
        helper.assertTrue(schematic.build(helper.getLevel(), helper.absolutePos(relPos)),
                "The pipe schematic should build into the empty cell " + relPos);
        //? if >=1.21.10 {
        TilePipeHolder built = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder built = helper.getBlockEntity(relPos);*/
        //?}
        helper.assertTrue(built.getPipe() != null, "The built pipe holder must hold a pipe");
        return built;
    }

    /** The behaviour compound of a captured pipe schematic ({@code pipe.beh}): the live tag, so edits reach the schematic. */
    private static CompoundTag behaviourTag(ISchematicBlock schematic) {
        return NBTUtilBC.getCompound(NBTUtilBC.getCompound(schematic.getTileNbtForRender(), "pipe"), "beh");
    }

    /** The direction a schematic's behaviour tag stores under {@code key}, or null when it stores none. */
    private static Direction directionKey(ISchematicBlock schematic, String key) {
        return NBTUtilBC.readEnum(behaviourTag(schematic).get(key), Direction.class);
    }

    private static Direction currentDirOf(TilePipeHolder tile) {
        return ((PipeBehaviourDirectional) tile.getPipe().getBehaviour()).getCurrentDir();
    }

    /** The filter rows a schematic's diamond pipe holds, read the way a placed pipe reads them. */
    private static ItemHandlerSimple filtersOf(ISchematicBlock schematic) {
        return new PipeBehaviourDiamondItem(null, behaviourTag(schematic)).filters;
    }

    /** Every filter slot is empty except the slots of {@code expected}, which hold that item. */
    private static void assertFilters(GameTestHelper helper, ItemHandlerSimple filters, String label,
                                      Map<Integer, Item> expected) {
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            ItemStack stack = filters.getStackInSlot(slot);
            Item item = expected.get(slot);
            if (item == null) {
                helper.assertTrue(stack.isEmpty(),
                        "Filter slot " + slot + " " + label + " must be empty, got "
                                + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            } else {
                helper.assertTrue(stack.is(item),
                        "Filter slot " + slot + " " + label + " must hold " + BuiltInRegistries.ITEM.getKey(item)
                                + ", got " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
    }

    /**
     * A pluggable the blueprint cannot price is left off the pipe the Builder places, not placed for free. A
     * captured pipe with a blocker on UP is given, in its stored NBT, a gate that cannot be rebuilt on NORTH and one
     * from an add-on that is gone on SOUTH; built into an empty cell it must come out as a working pipe with the
     * blocker and nothing else. Before the fix the unrebuildable gate made the whole pipe fail to load, which wiped the
     * pipe and the blocker with it.
     */
    public static void pipeSchematicLeavesUnpriceablePluggablesOff(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder source = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder source = helper.getBlockEntity(relPos);*/
        //?}
        source.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        source.replacePluggable(Direction.UP, new PluggableBlocker(BCTransportPlugs.blocker, source, Direction.UP));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock captured = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(captured instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + captured.getClass().getSimpleName());
        SchematicBlockPipe schematic = (SchematicBlockPipe) captured;
        CompoundTag plugs = NBTUtilBC.getCompound(schematic.tileNbt, "plugs").copy();
        plugs.put(Direction.NORTH.getName(), unrebuildableGateEntry());
        plugs.put(Direction.SOUTH.getName(), unknownPluggableEntry());
        schematic.tileNbt.put("plugs", plugs);

        List<ItemStack> required = schematic.computeRequiredItems();
        helper.assertTrue(required.size() == 2 && required.get(0).is(BCTransportItems.PIPE_COBBLE_ITEM.get())
                        && required.get(1).is(BCTransportItems.PLUG_BLOCKER.get()),
                "Only the pipe and the blocker are charged for, got " + itemNames(required));

        helper.setBlock(relPos, Blocks.AIR);
        helper.assertTrue(schematic.build(level, abs), "The pipe schematic should build into the emptied cell");
        //? if >=1.21.10 {
        TilePipeHolder built = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder built = helper.getBlockEntity(relPos);*/
        //?}
        helper.assertTrue(built.getPipe() != null,
                "The pipe must survive an unpriceable pluggable (it used to be wiped with it)");
        helper.assertTrue(built.getPluggable(Direction.UP) instanceof PluggableBlocker,
                "The priced blocker on UP must still be placed");
        helper.assertTrue(built.getPluggable(Direction.NORTH) == null,
                "The unrebuildable gate on NORTH must not be placed");
        helper.assertTrue(built.getPluggable(Direction.SOUTH) == null,
                "The unknown pluggable on SOUTH must not be placed");
        // The blueprint itself still carries them: only the placed copy is trimmed.
        helper.assertFalse(NBTUtilBC.getCompound(plugsOf(schematic), "north").isEmpty(),
                "Building must not edit the stored schematic");
        helper.succeed();
    }

    /**
     * A waterlogged block is scanned as itself, through the real factory chain: the fluid schematic (highest priority)
     * once took every state with a fluid, so a wet slab was charged 1000 mB of water and no item, and a wet pipe lost
     * its pipe definition and pluggables. A water source is still the fluid schematic.
     */
    public static void wetBlocksScanAsThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipePos = new BlockPos(1, 2, 1);
        BlockPos slabPos = new BlockPos(3, 2, 1);
        BlockPos waterPos = new BlockPos(1, 2, 4);
        helper.setBlock(pipePos, BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipePos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipePos);*/
        //?}
        Item pipeItem = BCTransportItems.PIPE_COBBLE_ITEM.get();
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        tile.replacePluggable(Direction.UP, new PluggableBlocker(BCTransportPlugs.blocker, tile, Direction.UP));
        helper.setBlock(slabPos, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        helper.setBlock(waterPos, Blocks.WATER);

        ISchematicBlock pipe = scan(level, helper.absolutePos(pipePos));
        helper.assertTrue(pipe instanceof SchematicBlockPipe,
                "A wet pipe must still scan as the pipe schematic, got " + pipe.getClass().getSimpleName());
        List<ItemStack> pipeRequired = pipe.computeRequiredItems();
        helper.assertTrue(pipeRequired.stream().anyMatch(s -> s.is(pipeItem))
                        && pipeRequired.stream().anyMatch(s -> s.is(BCTransportItems.PLUG_BLOCKER.get())),
                "A wet pipe must be charged for its pipe and pluggable, got " + itemNames(pipeRequired));
        helper.assertTrue(pipe.computeRequiredFluids().isEmpty(), "A wet pipe must not be charged water");
        helper.assertTrue(pipe.getBlockStateForRender().getValue(BlockStateProperties.WATERLOGGED),
                "A wet pipe must stay waterlogged");

        ISchematicBlock slab = scan(level, helper.absolutePos(slabPos));
        helper.assertTrue(slab instanceof SchematicBlockDefault,
                "A wet slab must scan as the default schematic, got " + slab.getClass().getSimpleName());
        List<ItemStack> slabRequired = slab.computeRequiredItems();
        helper.assertTrue(slabRequired.size() == 1 && slabRequired.get(0).is(Items.OAK_SLAB),
                "A wet slab must be charged for the slab, got " + itemNames(slabRequired));
        helper.assertTrue(slab.computeRequiredFluids().isEmpty(), "A wet slab must not be charged water");

        ISchematicBlock water = scan(level, helper.absolutePos(waterPos));
        helper.assertTrue(water instanceof SchematicBlockFluid,
                "A water source must still scan as the fluid schematic, got " + water.getClass().getSimpleName());
        helper.assertTrue(water.computeRequiredFluids().size() == 1, "A water source is charged its water");
        helper.succeed();
    }

    private static ISchematicBlock scan(ServerLevel level, BlockPos abs) {
        BlockState state = level.getBlockState(abs);
        return SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
    }

    private static CompoundTag plugsOf(ISchematicBlock schematic) {
        return NBTUtilBC.getCompound(schematic.getTileNbtForRender(), "plugs");
    }

    /** The wires a captured pipe schematic carries, by corner ({@code wires.parts} of the tile NBT). */
    private static Map<EnumWirePart, DyeColor> wiresOf(ISchematicBlock schematic) {
        int[] parts = NBTUtilBC.getIntArray(
                NBTUtilBC.getCompound(schematic.getTileNbtForRender(), "wires"), "parts", new int[0]);
        Map<EnumWirePart, DyeColor> wires = new EnumMap<>(EnumWirePart.class);
        // The same flat (part ordinal, colour id) pairs WireManager writes; a pair naming no part is skipped.
        for (int i = 0; i + 1 < parts.length; i += 2) {
            if (parts[i] >= 0 && parts[i] < EnumWirePart.VALUES.length) {
                wires.put(EnumWirePart.VALUES[parts[i]], DyeColor.byId(parts[i + 1]));
            }
        }
        return wires;
    }

    /** The expected wire map of the two-wire pipe this tester builds. */
    private static Map<EnumWirePart, DyeColor> wires(EnumWirePart partA, DyeColor colourA,
                                                     EnumWirePart partB, DyeColor colourB) {
        Map<EnumWirePart, DyeColor> wires = new EnumMap<>(EnumWirePart.class);
        wires.put(partA, colourA);
        wires.put(partB, colourB);
        return wires;
    }

    private static String itemNames(List<ItemStack> stacks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(BuiltInRegistries.ITEM.getKey(stacks.get(i).getItem()));
        }
        return sb.append("]").toString();
    }

    /**
     * A gate entry whose pluggable cannot be rebuilt (shared with the unit-level
     * SchematicBlockPipePluggablePricingTester; it lives here because the Fabric node compiles the game tests without
     * the JUnit files). A clay brick gate holds one slot and a quartz modifier halves the slots: zero slots, so constructing it fails (its connection array would be -1 long). Not something a player
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
}
