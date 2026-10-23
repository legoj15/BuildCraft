/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicEntityContext;
import buildcraft.builders.BuilderRefloodTester;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.RefusalMemory;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.WorkClaimMod;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Claim mods can stop the Builder and the Filler from placing blocks. The Filler places by clicking its item on the
 * cell like a player, so a claim can refuse either the right-click or the placement itself (NeoForge's placement
 * capture); the Builder sets the blueprint's block down directly, so it asks a placement probe first. Either way a
 * refused cell stays empty and is PARKED: not re-asked on every pass while the claim stands, and not dropped for good
 * either, so the build carries on into it once the claim is lifted.
 *
 * <p>The claim is {@link WorkClaimMod}, watching only this test's cells.
 */
public class BuilderClaimTester {

    /** Real ticks to wait, after a claim is lifted, for a parked cell to be retried: its window (200) and some
     *  building. A literal, not computed from the window, so {@code GameTestPollBudgetTester} can read it; the
     *  initializer below fails loudly when the window outgrows it. */
    private static final int RETRY_BUDGET = 400;

    static {
        if (RETRY_BUDGET <= RefusalMemory.BUILDER_PLACE.windowTicks()) {
            throw new IllegalStateException("RETRY_BUDGET (" + RETRY_BUDGET + ") must outlast the refusal window ("
                    + RefusalMemory.BUILDER_PLACE.windowTicks() + "): raise it, and the manifests' max_ticks above it");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /** A Filler stand-in: just what a {@link TemplateBuilder} reads off its tile, unowned (asks as [BuildCraft]).
     *  Excavation is off unless a test turns it on (the real Filler has it on). */
    static final class TestFiller implements ITileForTemplateBuilder {
        final ServerLevel level;
        final BlockPos pos;
        final MjBattery battery = new MjBattery(100_000 * MjAPI.MJ);
        final ItemHandlerSimple inv = new ItemHandlerSimple(1);
        final Template.BuildingInfo info;
        final TemplateBuilder builder = new TemplateBuilder(this);
        boolean excavate;

        TestFiller(ServerLevel level, BlockPos pos, Template.BuildingInfo info) {
            this.level = level;
            this.pos = pos;
            this.info = info;
        }

        @Override
        public Level getWorldBC() {
            return level;
        }

        @Override
        public MjBattery getBattery() {
            return battery;
        }

        @Override
        public BlockPos getBuilderPos() {
            return pos;
        }

        @Override
        public boolean canExcavate() {
            return excavate;
        }

        @Override
        public SnapshotBuilder<?> getBuilder() {
            return builder;
        }

        @Override
        public GameProfile getOwner() {
            return null;
        }

        @Override
        public Template.BuildingInfo getTemplateBuildingInfo() {
            return info;
        }

        @Override
        public IItemTransactor getInvResources() {
            return inv;
        }

        /** Many builder passes inside one game tick, so no refusal window can end while they run. Whether the last
         *  pass found the build finished (the answer the real Filler marks itself finished on). */
        boolean run(int passes) {
            boolean done = false;
            for (int i = 0; i < passes; i++) {
                battery.addPower(battery.getCapacity() - battery.getStored(), false);
                done = builder.tick();
            }
            return done;
        }
    }

    /**
     * Three cells in a row, filled with stone: the first is claimed against right-clicks, the second against
     * placements, the third is free. The free one is filled; the claimed ones stay empty, are asked about once and
     * then parked (with their stone back in the Filler), and are filled once the claim is lifted and their window
     * has ended. Before the fix the Filler posted no right-click ask, so the first cell was filled, and a cell whose
     * placement was refused was asked about again on every pass.
     */
    public static void fillerAsksLikeAPlayerAndParksRefusedCells(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        EntityArenaUtil.forceLoadEntityArena(helper);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos clickClaimed = helper.absolutePos(new BlockPos(1, 2, 2));
        BlockPos placeClaimed = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos free = helper.absolutePos(new BlockPos(3, 2, 2));
        Set<BlockPos> cells = Set.of(clickClaimed, placeClaimed, free);

        Template template = new Template();
        template.size = new BlockPos(3, 1, 1);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet();
        template.data.set(0, 3);
        TestFiller filler = new TestFiller(level, helper.absolutePos(new BlockPos(2, 2, 4)),
                template.new BuildingInfo(clickClaimed, Rotation.NONE));
        filler.inv.setStackInSlot(0, new ItemStack(Items.STONE, 16));
        filler.builder.updateSnapshot();

        WorkClaimMod[] claim = { new WorkClaimMod(cells::contains, clickClaimed::equals, placeClaimed::equals,
                pos -> false) };
        Runnable lift = () -> {
            if (claim[0] != null) {
                claim[0].close();
                claim[0] = null;
            }
        };
        try {
            filler.run(200);
            WorkClaimMod asked = claim[0];
            check(level.getBlockState(free).is(Blocks.STONE), "the free cell is filled");
            check(asked.toolAsks(free) == 1 && asked.placeAsks(free) == 1, "the free cell was clicked and placed "
                    + "once, asked " + asked.toolAsks(free) + "/" + asked.placeAsks(free) + " times");
            check(level.getBlockState(clickClaimed).isAir(), "a cell whose right-click is claimed stays empty");
            check(asked.toolAsks(clickClaimed) == 1, "the claimed click is asked about once and the cell parked, "
                    + "asked " + asked.toolAsks(clickClaimed) + " times");
            check(asked.placeAsks(clickClaimed) == 0, "a refused click places nothing, so it asks no placement");
            check(level.getBlockState(placeClaimed).isAir(), "a cell whose placement is claimed stays empty");
            check(asked.placeAsks(placeClaimed) == 1, "the claimed placement is asked about once and the cell "
                    + "parked, asked " + asked.placeAsks(placeClaimed) + " times");
            check(filler.inv.getStackInSlot(0).getCount() == 15, "only the free cell's stone was used, the refused "
                    + "ones were refunded: " + filler.inv.getStackInSlot(0).getCount() + " left of 16");
            check(filler.builder.leftToPlace == 2, "the parked cells are still owed, not dropped: leftToPlace="
                    + filler.builder.leftToPlace);
        } catch (RuntimeException e) {
            lift.run();
            throw e;
        }
        lift.run();
        EntityArenaUtil.tickUntil(helper, RETRY_BUDGET, () -> {
            filler.run(4);
            return level.getBlockState(clickClaimed).is(Blocks.STONE) && level.getBlockState(placeClaimed)
                    .is(Blocks.STONE);
        }, helper::succeed, "once the claim is lifted, the parked cells are filled", lift);
    }

    /**
     * A Builder building a three-stone column whose first cell is claimed against placements: the other two are
     * built, the claimed one stays empty and is asked about once (the blueprint's block is set down directly, so the
     * ask is the placement probe and no click), and it is built once the claim is lifted. Before the fix nothing was
     * asked and the claimed cell was built.
     */
    public static void blueprintBuilderAsksBeforePlacingAndParksRefusedCells(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        int[] column = { BuilderRefloodTester.STONE, BuilderRefloodTester.STONE, BuilderRefloodTester.STONE };
        BlockPos claimed = helper.absolutePos(BuilderRefloodTester.cell(0));
        BlockPos second = helper.absolutePos(BuilderRefloodTester.cell(1));
        BlockPos third = helper.absolutePos(BuilderRefloodTester.cell(2));
        Set<BlockPos> cells = Set.of(claimed, second, third);
        WorkClaimMod[] claim = { new WorkClaimMod(cells::contains, claimed::equals) };
        Runnable lift = () -> {
            if (claim[0] != null) {
                claim[0].close();
                claim[0] = null;
            }
        };
        TileBuilder tile = BuilderRefloodTester.setUpBuilder(helper, column, new BlockPos[0]);

        int[] heldSince = { -1 };
        int[] tick = { 0 };
        EntityArenaUtil.tickUntil(helper, RETRY_BUDGET + 300, () -> {
            tick[0]++;
            WorkClaimMod asked = claim[0];
            if (asked != null) {
                check(level.getBlockState(claimed).isAir(), "a cell whose placement is claimed must not be built");
                boolean othersBuilt = level.getBlockState(second).is(Blocks.STONE)
                        && level.getBlockState(third).is(Blocks.STONE);
                if (asked.placeAsks(claimed) == 0 || !othersBuilt) {
                    return false;
                }
                check(asked.placeAsks(claimed) == 1, "the claimed cell is asked about once and parked, asked "
                        + asked.placeAsks(claimed) + " times");
                check(asked.toolAsks(claimed) == 0, "a blueprint block is set down, not clicked: no right-click ask");
                check(asked.placeAsks(second) == 1 && asked.placeAsks(third) == 1,
                        "each free cell was asked about once before it was built");
                if (heldSince[0] < 0) {
                    heldSince[0] = tick[0];
                }
                // Held long enough that a builder re-asking every pass would have asked many times over. (The
                // count of cells still owed is read only now: it is taken before a tick's placements run, so right
                // after the free cells went in it still counts them.)
                if (tick[0] - heldSince[0] >= 40) {
                    check(tile.getBuilder().leftToPlace == 1, "the parked cell is still owed, not dropped: "
                            + "leftToPlace=" + tile.getBuilder().leftToPlace);
                    lift.run();
                }
                return false;
            }
            return level.getBlockState(claimed).is(Blocks.STONE);
        }, helper::succeed, "the claimed cell is held, then built once the claim is lifted", lift);
    }

    /**
     * A Builder building stone, a gap and stone, with an armor stand standing in the gap whose cell is claimed
     * against placements: the stone goes in, the stand is asked about once and parked, and the base position stays
     * unfinished while it is owed (so a path-fed Builder does not move on and drop it); once the claim is lifted the
     * stand is spawned and the position finishes. Before the fix a refused entity counted as done, so a Builder on a
     * path moved on to its next position and never came back for it.
     */
    public static void blueprintBuilderParksRefusedEntityAndStaysBusy(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        int[] column = { BuilderRefloodTester.STONE, BuilderRefloodTester.AIR, BuilderRefloodTester.STONE };
        BlockPos first = helper.absolutePos(BuilderRefloodTester.cell(0));
        BlockPos standCell = helper.absolutePos(BuilderRefloodTester.cell(1));
        BlockPos third = helper.absolutePos(BuilderRefloodTester.cell(2));
        // The stand's schematic, taken from one that is never added to the level.
        ArmorStand sample = new ArmorStand(level, standCell.getX() + 0.5, standCell.getY(), standCell.getZ() + 0.5);
        ISchematicEntity standSchematic = SchematicEntityManager.getSchematicEntity(
                new SchematicEntityContext(level, first, sample));
        check(standSchematic != null, "precondition: the armor stand has a schematic");
        Set<BlockPos> cells = Set.of(first, standCell, third);
        WorkClaimMod[] claim = { new WorkClaimMod(cells::contains, standCell::equals) };
        Runnable cleanup = () -> {
            if (claim[0] != null) {
                claim[0].close();
                claim[0] = null;
            }
            for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, EntityArenaUtil.cellBounds(helper))) {
                stand.discard();
            }
        };
        TileBuilder tile = BuilderRefloodTester.setUpBuilder(helper, column, new BlockPos[0],
                List.of(standSchematic));
        tile.setResource(3, new ItemStack(Items.ARMOR_STAND, 1));

        int[] heldSince = { -1 };
        int[] tick = { 0 };
        EntityArenaUtil.tickUntil(helper, RETRY_BUDGET + 300, () -> {
            tick[0]++;
            // Spawning waits on a full battery: keep it topped up, so only the claim can hold the stand back.
            tile.getBattery().addPower(tile.getBattery().getCapacity() - tile.getBattery().getStored(), false);
            boolean standSpawned = !level.getEntitiesOfClass(ArmorStand.class, EntityArenaUtil.cellBounds(helper))
                    .isEmpty();
            WorkClaimMod asked = claim[0];
            if (asked != null) {
                check(!standSpawned, "an entity whose cell is claimed must not be spawned");
                boolean blocksBuilt = level.getBlockState(first).is(Blocks.STONE)
                        && level.getBlockState(third).is(Blocks.STONE);
                if (asked.placeAsks(standCell) == 0 || !blocksBuilt) {
                    return false;
                }
                check(asked.placeAsks(standCell) == 1, "the claimed entity cell is asked about once and parked, "
                        + "asked " + asked.placeAsks(standCell) + " times");
                if (heldSince[0] < 0) {
                    heldSince[0] = tick[0];
                } else {
                    check(!tile.isDone(), "the base position must stay unfinished while the refused entity is owed");
                }
                if (tick[0] - heldSince[0] >= 40) {
                    asked.close();
                    claim[0] = null;
                }
                return false;
            }
            return standSpawned && tile.isDone();
        }, helper::succeed, "the refused stand is held with the build unfinished, then spawned once the claim is "
                + "lifted", cleanup);
    }
}
