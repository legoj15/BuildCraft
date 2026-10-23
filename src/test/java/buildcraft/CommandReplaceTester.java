/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * The contract between BuildCraft's spill-on-removal hooks and vanilla's replace commands.
 * <p>
 * Every BuildCraft tile that spills its contents when it is removed by something other than a player's hand
 * (explosion, piston, mod tool, {@code /setblock ... destroy}) must NOT spill them when a command <em>replaces</em>
 * it. Vanilla signals a replace by calling {@code Clearable.tryClear} on the old block entity first (1.21.1:
 * {@code /setblock}, {@code /fill}, {@code /clone}, structure placement) or by placing with the "skip block entity
 * side effects" flag (1.21.10+), so the contents vanish like a vanilla chest's. The 1.21.1 node used to spill
 * every tile that overrode {@code dropContentsOnRemoval}, because BuildCraft's {@code onRemove} catch-all ignored
 * the command; {@code /clone ... move} there also copied the contents to the destination AND spilled them at the
 * source.
 * <p>
 * The rigs are the tile families that spill: standalone fluid tiles, itemManager machines, a machine with
 * loose extra contents (the Builder's snapshot slot and tanks), engines with loose contents (the Stirling's fuel
 * stack, the FE engine's and MJ Dynamo's upgrade slots), the multiblock Heat Exchanger and the pipes' in-transit
 * cargo. {@code /setblock ... destroy} is the control: it must keep spilling.
 */
public class CommandReplaceTester {

    /** Ticks between the removal and the assertions: spilled items are in the level and have barely moved. */
    private static final int GAP_TICKS = 2;
    /** The wait for every rig position to become entity-ticking, so a spilled item is visible to a scan. */
    private static final int POLL_TICKS = 60;
    /** Half the side of the box scanned around a rig. Rigs sit two blocks apart, and an item popped from a rig
     *  stays within about 0.6 blocks of it over {@link #GAP_TICKS}, so a rig's box never sees a neighbour's drops. */
    private static final double RIG_RADIUS = 1.0;

    /** One block under test: what it is called in a failure message, where it is, and which items a removal that
     *  does NOT clear it must spill (the control). */
    private record Rig(String name, BlockPos pos, Item... mustSpill) {}

    private enum Removal {
        /** {@code /setblock x y z minecraft:stone replace}: the case that must not spill. */
        SETBLOCK_REPLACE,
        /** {@code /setblock x y z minecraft:stone destroy}: the loot-and-spill path, which must keep spilling. */
        SETBLOCK_DESTROY,
        /** {@code Clearable.tryClear} then a bare {@code removeBlock}: vanilla's 1.21.1 replace, on every node. */
        CLEAR_THEN_REMOVE
    }

    // ---------- Rig layout ----------
    // Two blocks apart in a 6x7x7 cell, clear of the x=0/x=5 and z=0/z=6 edges so no rig touches a neighbouring
    // arena. The Heat Exchanger is three blocks in a row along x (facing north: east is its START end).

    private static final BlockPos TANK = new BlockPos(1, 2, 1);
    private static final BlockPos FLOOD_GATE = new BlockPos(3, 2, 1);
    private static final BlockPos ENGINE = new BlockPos(1, 2, 3);
    private static final BlockPos DISTILLER = new BlockPos(3, 2, 3);
    private static final BlockPos EXCHANGER_END = new BlockPos(1, 2, 5);
    private static final BlockPos EXCHANGER_MIDDLE = new BlockPos(2, 2, 5);
    private static final BlockPos EXCHANGER_START = new BlockPos(3, 2, 5);
    private static final BlockPos CHUTE = new BlockPos(1, 4, 1);
    private static final BlockPos BUILDER = new BlockPos(3, 4, 1);
    private static final BlockPos PUMP = new BlockPos(1, 4, 3);
    private static final BlockPos ITEM_PIPE = new BlockPos(3, 4, 3);
    private static final BlockPos FLUID_PIPE = new BlockPos(1, 4, 5);
    private static final BlockPos STIRLING = new BlockPos(3, 4, 5);
    // The 2-block grid is full at y=2 and y=4, so the upgrade-slot engines take a third layer; y=3 and y=5 stay
    // empty so a rig's scan box never reaches the layer beside it.
    private static final BlockPos FE_ENGINE = new BlockPos(1, 6, 1);
    private static final BlockPos DYNAMO = new BlockPos(3, 6, 1);

    private static final BlockPos[] ALL_POSITIONS = {
        TANK, FLOOD_GATE, ENGINE, DISTILLER, EXCHANGER_END, EXCHANGER_MIDDLE, EXCHANGER_START,
        CHUTE, BUILDER, PUMP, ITEM_PIPE, FLUID_PIPE, STIRLING, FE_ENGINE, DYNAMO
    };

    // ---------- Tests ----------

    /** The bug: a replace-mode {@code /setblock} spilled every tile's contents on 1.21.1. */
    public static void testSetblockReplaceSpillsNothing(GameTestHelper helper) {
        runRigs(helper, Removal.SETBLOCK_REPLACE);
    }

    /** The control: destroy mode is a deliberate "break it" and keeps spilling on every node. */
    public static void testSetblockDestroyStillSpills(GameTestHelper helper) {
        runRigs(helper, Removal.SETBLOCK_DESTROY);
    }

    /** The mechanism on every node, including the ones whose commands never call it: after the vanilla
     *  {@code Clearable.tryClear} a plain removal has nothing left to spill. */
    public static void testClearableWipesWhatRemovalSpills(GameTestHelper helper) {
        runRigs(helper, Removal.CLEAR_THEN_REMOVE);
    }

    /** {@code /clone ... move} of a full tank: the water arrives at the destination once, and the source's copy
     *  neither spills nor survives. On 1.21.1 it used to arrive AND spill. */
    public static void testCloneMoveNeitherSpillsNorDuplicates(GameTestHelper helper) {
        BlockPos source = TANK;
        BlockPos destination = new BlockPos(3, 2, 1);
        forceLoadAround(helper, source, destination);
        EntityArenaUtil.tickUntilThen(helper, POLL_TICKS,
                () -> EntityArenaUtil.isEntityTicking(helper, source)
                        && EntityArenaUtil.isEntityTicking(helper, destination),
                () -> {
                    clearDrops(helper, source);
                    clearDrops(helper, destination);
                    helper.setBlock(source, BCFactoryBlocks.TANK.get());
                    tile(helper, source, TileTank.class).tank.setFluidStack(0, new FluidStack(Fluids.WATER, 1000));
                    BlockPos a = helper.absolutePos(source);
                    BlockPos b = helper.absolutePos(destination);
                    command(helper, "clone " + a.getX() + " " + a.getY() + " " + a.getZ() + " "
                            + a.getX() + " " + a.getY() + " " + a.getZ() + " "
                            + b.getX() + " " + b.getY() + " " + b.getZ() + " replace move");
                },
                GAP_TICKS,
                () -> {
                    if (!helper.getBlockState(source).isAir()) {
                        throw new IllegalStateException("clone move should have left air at the source, found "
                                + helper.getBlockState(source));
                    }
                    BlockEntity moved = helper.getLevel().getBlockEntity(helper.absolutePos(destination));
                    if (!(moved instanceof TileTank tank)) {
                        throw new IllegalStateException("clone move should have placed a tank at the destination, found "
                                + helper.getBlockState(destination));
                    }
                    if (tank.tank.getAmountMb(0) != 1000) {
                        throw new IllegalStateException("the cloned tank should hold the 1000 mB it was moved with, held "
                                + tank.tank.getAmountMb(0));
                    }
                    List<ItemEntity> spilled = new ArrayList<>(EntityArenaUtil.droppedItems(helper, source, RIG_RADIUS));
                    spilled.addAll(EntityArenaUtil.droppedItems(helper, destination, RIG_RADIUS));
                    if (!spilled.isEmpty()) {
                        throw new IllegalStateException("clone move must not spill the tank's water, but dropped "
                                + describe(spilled));
                    }
                    helper.succeed();
                },
                "the clone rig never became entity-ticking",
                () -> {
                    clearDrops(helper, source);
                    clearDrops(helper, destination);
                });
    }

    // ---------- Driver ----------

    private static void runRigs(GameTestHelper helper, Removal removal) {
        List<Rig> rigs = new ArrayList<>();
        forceLoadAround(helper, ALL_POSITIONS);
        EntityArenaUtil.tickUntilThen(helper, POLL_TICKS,
                () -> {
                    for (BlockPos pos : ALL_POSITIONS) {
                        if (!EntityArenaUtil.isEntityTicking(helper, pos)) {
                            return false;
                        }
                    }
                    return true;
                },
                () -> {
                    for (BlockPos pos : ALL_POSITIONS) {
                        clearDrops(helper, pos);
                    }
                    rigs.addAll(buildRigs(helper));
                    for (Rig rig : rigs) {
                        remove(helper, rig, removal);
                    }
                },
                GAP_TICKS,
                () -> {
                    List<String> failures = new ArrayList<>();
                    for (Rig rig : rigs) {
                        List<ItemEntity> dropped = EntityArenaUtil.droppedItems(helper, rig.pos(), RIG_RADIUS);
                        if (removal == Removal.SETBLOCK_DESTROY) {
                            for (Item expected : rig.mustSpill()) {
                                if (dropped.stream().noneMatch(e -> e.getItem().is(expected))) {
                                    failures.add(rig.name() + " should have spilled " + expected
                                            + " but dropped " + describe(dropped));
                                }
                            }
                        } else {
                            if (!dropped.isEmpty()) {
                                failures.add(rig.name() + " spilled " + describe(dropped));
                            }
                            if (removal == Removal.SETBLOCK_REPLACE && !helper.getBlockState(rig.pos()).is(Blocks.STONE)) {
                                failures.add(rig.name() + " was not replaced by the command, found "
                                        + helper.getBlockState(rig.pos()));
                            }
                        }
                    }
                    if (!failures.isEmpty()) {
                        throw new IllegalStateException(removal + ": " + String.join("; ", failures));
                    }
                    helper.succeed();
                },
                "a rig position never became entity-ticking",
                () -> {
                    for (BlockPos pos : ALL_POSITIONS) {
                        clearDrops(helper, pos);
                    }
                });
    }

    private static void remove(GameTestHelper helper, Rig rig, Removal removal) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rig.pos());
        switch (removal) {
            case SETBLOCK_REPLACE -> command(helper, "setblock " + abs.getX() + " " + abs.getY() + " " + abs.getZ()
                    + " minecraft:stone replace");
            case SETBLOCK_DESTROY -> command(helper, "setblock " + abs.getX() + " " + abs.getY() + " " + abs.getZ()
                    + " minecraft:stone destroy");
            case CLEAR_THEN_REMOVE -> {
                // Vanilla's Clearable.tryClear, which 1.21.10 removed: the instanceof is all it ever did.
                if (level.getBlockEntity(abs) instanceof Clearable clearable) {
                    clearable.clearContent();
                }
                level.removeBlock(abs, false);
            }
        }
    }

    /** Runs a vanilla command as the server in the test's level, so the test goes through the node's own
     *  {@code SetBlockCommand} / {@code CloneCommands} rather than imitating them. */
    private static void command(GameTestHelper helper, String command) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    // ---------- Rig construction ----------

    private static List<Rig> buildRigs(GameTestHelper helper) {
        List<Rig> rigs = new ArrayList<>();
        Item shard = BCCoreItems.FRAGILE_FLUID_CONTAINER.get();
        FluidStack water = new FluidStack(Fluids.WATER, 1000);

        helper.setBlock(TANK, BCFactoryBlocks.TANK.get());
        tile(helper, TANK, TileTank.class).tank.setFluidStack(0, water);
        rigs.add(new Rig("tank", TANK, shard));

        helper.setBlock(FLOOD_GATE, BCFactoryBlocks.FLOOD_GATE.get());
        tile(helper, FLOOD_GATE, TileFloodGate.class).getTank().setFluidStack(0, water);
        rigs.add(new Rig("flood gate", FLOOD_GATE, shard));

        helper.setBlock(ENGINE, BCEnergyBlocks.ENGINE_IRON.get());
        TileEngineIron_BC8 engine = tile(helper, ENGINE, TileEngineIron_BC8.class);
        engine.tankFuel.setFluidStack(0, water);
        engine.tankCoolant.setFluidStack(0, water);
        rigs.add(new Rig("combustion engine", ENGINE, shard));

        helper.setBlock(DISTILLER, BCFactoryBlocks.DISTILLER.get());
        TileDistiller_BC8 distiller = tile(helper, DISTILLER, TileDistiller_BC8.class);
        distiller.getTankIn().setFluidStack(0, water);
        distiller.containerSlots.setStackInSlot(0, new ItemStack(Items.BUCKET));
        rigs.add(new Rig("distiller", DISTILLER, shard));

        // Three heat exchangers in a row form one multiblock once the middle one has looked around; the
        // START end (east, facing north) owns the section tanks and the bucket slots that spill.
        helper.setBlock(EXCHANGER_END, BCFactoryBlocks.HEAT_EXCHANGE.get());
        helper.setBlock(EXCHANGER_MIDDLE, BCFactoryBlocks.HEAT_EXCHANGE.get());
        helper.setBlock(EXCHANGER_START, BCFactoryBlocks.HEAT_EXCHANGE.get());
        TileHeatExchange middle = tile(helper, EXCHANGER_MIDDLE, TileHeatExchange.class);
        middle.markCheckNeighbours();
        middle.serverTick();
        TileHeatExchange start = tile(helper, EXCHANGER_START, TileHeatExchange.class);
        if (start.getSection() == null) {
            throw new IllegalStateException("the heat exchanger rig did not form a multiblock");
        }
        start.getSection().tankInput.setFluidStack(0, water);
        start.containerSlots.setStackInSlot(0, new ItemStack(Items.BUCKET));
        rigs.add(new Rig("heat exchanger start", EXCHANGER_START, shard));

        helper.setBlock(CHUTE, BCFactoryBlocks.CHUTE.get());
        TileChute chute = tile(helper, CHUTE, TileChute.class);
        chute.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        rigs.add(new Rig("chute", CHUTE, Items.DIAMOND));

        helper.setBlock(BUILDER, BCBuildersBlocks.BUILDER.get());
        TileBuilder builder = tile(helper, BUILDER, TileBuilder.class);
        builder.invResources.setStackInSlot(0, new ItemStack(Items.EMERALD, 3));
        builder.getTank(0).setFluidStack(0, water);
        builder.setSnapshot(new ItemStack(Items.NETHER_STAR));
        rigs.add(new Rig("builder", BUILDER, Items.EMERALD, shard, Items.NETHER_STAR));

        helper.setBlock(PUMP, BCFactoryBlocks.PUMP.get());
        tile(helper, PUMP, TilePump.class).getTank().setFluidStack(0, water);
        rigs.add(new Rig("pump", PUMP, shard));

        helper.setBlock(ITEM_PIPE, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder itemPipe = tile(helper, ITEM_PIPE, TilePipeHolder.class);
        itemPipe.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        ((PipeFlowItems) itemPipe.getPipe().getFlow())
                .insertItemsForce(new ItemStack(Items.GOLD_INGOT, 4), Direction.NORTH, null, 0.04);
        rigs.add(new Rig("item pipe", ITEM_PIPE, Items.GOLD_INGOT));

        helper.setBlock(FLUID_PIPE, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder fluidPipe = tile(helper, FLUID_PIPE, TilePipeHolder.class);
        fluidPipe.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_FLUID.get()));
        int inserted = ((PipeFlowFluids) fluidPipe.getPipe().getFlow()).insertFluidsForce(water, null, false);
        if (inserted <= 0) {
            throw new IllegalStateException("the fluid pipe rig accepted no water");
        }
        rigs.add(new Rig("fluid pipe", FLUID_PIPE, shard));

        helper.setBlock(STIRLING, BCEnergyBlocks.ENGINE_STONE.get());
        tile(helper, STIRLING, TileEngineStone_BC8.class).setFuelStack(new ItemStack(Items.COAL, 5));
        rigs.add(new Rig("stirling engine", STIRLING, Items.COAL));

        // The FE engine and the MJ Dynamo keep gear upgrades in a raw ItemHandlerSimple, outside any itemManager.
        helper.setBlock(FE_ENGINE, BCEnergyBlocks.ENGINE_FE.get());
        TileEngineFE feEngine = tile(helper, FE_ENGINE, TileEngineFE.class);
        feEngine.upgrades.setStackInSlot(0, new ItemStack(BCCoreItems.GEAR_IRON.get()));
        feEngine.upgrades.setStackInSlot(2, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
        rigs.add(new Rig("FE engine", FE_ENGINE, BCCoreItems.GEAR_IRON.get(), BCCoreItems.GEAR_GOLD.get()));

        helper.setBlock(DYNAMO, BCEnergyBlocks.DYNAMO_MJ.get());
        tile(helper, DYNAMO, TileDynamoMJ.class).upgrades.setStackInSlot(1, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
        rigs.add(new Rig("MJ dynamo", DYNAMO, BCCoreItems.GEAR_GOLD.get()));

        return rigs;
    }

    // ---------- Helpers ----------

    private static <T extends BlockEntity> T tile(GameTestHelper helper, BlockPos rel, Class<T> type) {
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        if (!type.isInstance(be)) {
            throw new IllegalStateException("expected a " + type.getSimpleName() + " at " + rel + ", found " + be);
        }
        return type.cast(be);
    }

    /** Force-loads the chunk neighbourhood of every rig position. The cell crosses a chunk border in about 57% of
     *  runs, and a chunk only becomes entity-ticking once its own 5x5 neighbourhood is loaded, so the arena
     *  origin's neighbourhood alone leaves the far side of the cell waiting forever. */
    private static void forceLoadAround(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            EntityArenaUtil.forceLoadEntityArena(helper, pos);
        }
    }

    /** Discards the loose items around a rig: leftovers of a previous test in this cell, and our own spills. */
    private static void clearDrops(GameTestHelper helper, BlockPos rel) {
        for (ItemEntity item : EntityArenaUtil.droppedItems(helper, rel, RIG_RADIUS + 1.0)) {
            item.discard();
        }
    }

    private static String describe(List<ItemEntity> items) {
        if (items.isEmpty()) {
            return "nothing";
        }
        StringBuilder sb = new StringBuilder();
        for (ItemEntity item : items) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item.getItem());
        }
        return sb.toString();
    }
}
