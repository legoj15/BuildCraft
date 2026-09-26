/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.robotics.statements.ActionRobotFilter;
import buildcraft.robotics.statements.ActionRobotFilterTool;
import buildcraft.robotics.statements.ActionStationAcceptFluids;
import buildcraft.robotics.statements.ActionStationAcceptItems;
import buildcraft.robotics.statements.ActionStationForbidRobot;
import buildcraft.robotics.statements.ActionStationProvideFluids;
import buildcraft.robotics.statements.ActionStationProvideItems;
import buildcraft.robotics.statements.StatementParameterRobot;

/** The D1 station-policy overrides on {@link DockingStationPipe} — the one place the gate actions decide what a
 *  robot may do at a pipe station — read against a fixed action list, so the override code itself is what runs
 *  (only {@code getActiveActions}, which needs a live pipe holder, is replaced).
 *
 *  <p>The load-bearing contrast is the GATELESS case: the {@link DockingStation} base class is deliberately
 *  permissive (the contract for non-pipe stations and test doubles), while a pipe station with no gate refuses
 *  every item and fluid exchange — 7.1.x's {@code ActionRobotFilter.canInteractWith*} found no matching action
 *  and said no. A pipe station that "helpfully" fell back to the base defaults would turn every robot station
 *  on every pipe into a free, unfiltered item and fluid port. */
public class DockingStationPipePolicyTest extends VanillaSetupBaseTester {

    // Built per test instance, not in static fields: a stack made at class-load time predates the component
    // binding VanillaSetupBaseTester's @BeforeAll performs (26.x: "Components not bound yet").
    private final ItemStack diamond = new ItemStack(Items.DIAMOND);
    private final ItemStack stone = new ItemStack(Items.STONE);

    private static final IFluidFilter ANY_FLUID = fluid -> true;
    private static final IFluidFilter WATER_ONLY = fluid -> fluid.getFluid() == Fluids.WATER;
    private static final IFluidFilter LAVA_ONLY = fluid -> fluid.getFluid() == Fluids.LAVA;

    private static FluidStack water() {
        return new FluidStack(Fluids.WATER, 1000);
    }

    private static FluidStack lava() {
        return new FluidStack(Fluids.LAVA, 1000);
    }

    // ── the gateless contrast ────────────────────────────────────────────────

    @Test
    public void theBaseStationIsPermissiveWithNoGates() {
        DockingStation base = new RobotRegistryTest.TestDockingStation(BlockPos.ZERO, Direction.UP);

        Assertions.assertTrue(base.canRobotExtractItem(diamond), "base contract: extraction allowed");
        Assertions.assertTrue(base.canRobotAcceptItem(diamond), "base contract: insertion allowed");
        Assertions.assertTrue(base.canRobotExtractFluid(ANY_FLUID), "base contract: fluid extraction allowed");
        Assertions.assertTrue(base.canRobotAcceptFluid(ANY_FLUID), "base contract: fluid insertion allowed");
        Assertions.assertFalse(base.isRobotForbidden(pickerRobot()), "base contract: nobody is forbidden");
        Assertions.assertTrue(base.getRobotItemFilter().matches(stone), "base contract: every item is wanted");
        Assertions.assertTrue(base.getRobotFluidFilter().matches(lava()), "base contract: every fluid is wanted");
    }

    @Test
    public void aGatelessPipeStationRefusesEveryExchange() {
        GatedStation station = new GatedStation();

        Assertions.assertFalse(station.canRobotExtractItem(diamond),
                "no Provide Items action: a robot may not take items from the station's inventory");
        Assertions.assertFalse(station.canRobotAcceptItem(diamond),
                "no Accept Items action: a robot may not unload items into the pipe");
        Assertions.assertFalse(station.canRobotExtractFluid(ANY_FLUID),
                "no Provide Fluids action: a robot may not take fluid");
        Assertions.assertFalse(station.canRobotAcceptFluid(ANY_FLUID),
                "no Accept Fluids action: a robot may not unload fluid");
    }

    @Test
    public void aGatelessPipeStationRestrictsNothingElse() {
        GatedStation station = new GatedStation();

        Assertions.assertFalse(station.isRobotForbidden(pickerRobot()), "no forbid action forbids nobody");
        Assertions.assertTrue(station.getRobotItemFilter().matches(stone),
                "no work or tool filter: the robot works on anything");
        Assertions.assertTrue(station.getRobotFluidFilter().matches(lava()),
                "no work filter: the robot takes any fluid");
        Assertions.assertFalse(station.getRobotFluidFilter().matches(FluidStack.EMPTY),
                "…but 'no fluid' is never a fluid worth fetching");
    }

    // ── items ────────────────────────────────────────────────────────────────

    @Test
    public void provideItemsLetsARobotExtractWhatItsFilterAllows() {
        GatedStation unfiltered = new GatedStation().with(new ActionStationProvideItems());
        Assertions.assertTrue(unfiltered.canRobotExtractItem(stone), "an unfiltered Provide Items offers anything");
        Assertions.assertFalse(unfiltered.canRobotAcceptItem(stone),
                "Provide Items says nothing about unloading — accept stays refused");

        GatedStation diamonds = new GatedStation().with(new ActionStationProvideItems(), stack(diamond));
        Assertions.assertTrue(diamonds.canRobotExtractItem(diamond), "a diamond-filtered provider offers diamonds");
        Assertions.assertFalse(diamonds.canRobotExtractItem(stone), "…and nothing else");
    }

    @Test
    public void twoFilteredProvidersOfferTheUnion() {
        GatedStation station = new GatedStation()
                .with(new ActionStationProvideItems(), stack(diamond))
                .with(new ActionStationProvideItems(), stack(new ItemStack(Items.EMERALD)));

        Assertions.assertTrue(station.canRobotExtractItem(diamond));
        Assertions.assertTrue(station.canRobotExtractItem(new ItemStack(Items.EMERALD)));
        Assertions.assertFalse(station.canRobotExtractItem(stone));
    }

    @Test
    public void acceptItemsLetsARobotUnloadWhatItsFilterAllows() {
        GatedStation station = new GatedStation().with(new ActionStationAcceptItems(), stack(diamond));

        Assertions.assertTrue(station.canRobotAcceptItem(diamond));
        Assertions.assertFalse(station.canRobotAcceptItem(stone), "a diamond-only accept refuses stone");
        Assertions.assertFalse(station.canRobotExtractItem(diamond), "Accept Items does not open extraction");
    }

    @Test
    public void theWorkFilterWinsOverTheToolFilter() {
        GatedStation toolOnly = new GatedStation()
                .with(new ActionRobotFilterTool(), stack(new ItemStack(Items.IRON_AXE)));
        Assertions.assertTrue(toolOnly.getRobotItemFilter().matches(new ItemStack(Items.IRON_AXE)),
                "with no work filter the tool filter restricts the robot");
        Assertions.assertFalse(toolOnly.getRobotItemFilter().matches(diamond));

        GatedStation both = new GatedStation()
                .with(new ActionRobotFilter(), stack(diamond))
                .with(new ActionRobotFilterTool(), stack(new ItemStack(Items.IRON_AXE)));
        Assertions.assertTrue(both.getRobotItemFilter().matches(diamond), "the work filter is the one read");
        Assertions.assertFalse(both.getRobotItemFilter().matches(new ItemStack(Items.IRON_AXE)),
                "…and the tool filter is ignored once a work filter is set");
    }

    @Test
    public void forbidAndForceSelectRobotsByBoard() {
        StatementParameterRobot picker = new StatementParameterRobot(
                ItemRobot.createRobotStack(BoardRobotPickerNBT.INSTANCE.getID(), 0));

        GatedStation forbid = new GatedStation().with(new ActionStationForbidRobot(false), picker);
        Assertions.assertTrue(forbid.isRobotForbidden(pickerRobot()), "Forbid Robot(picker) bars pickers");
        Assertions.assertFalse(forbid.isRobotForbidden(lumberjackRobot()), "…and nobody else");

        GatedStation force = new GatedStation().with(new ActionStationForbidRobot(true), picker);
        Assertions.assertFalse(force.isRobotForbidden(pickerRobot()), "Force Robot(picker) admits pickers");
        Assertions.assertTrue(force.isRobotForbidden(lumberjackRobot()), "…and bars everybody else");
    }

    // ── fluids ───────────────────────────────────────────────────────────────

    @Test
    public void unfilteredFluidActionsOpenTheirOwnDirectionOnly() {
        GatedStation provide = new GatedStation().with(new ActionStationProvideFluids());
        Assertions.assertTrue(provide.canRobotExtractFluid(LAVA_ONLY), "an unfiltered Provide Fluids offers any fluid");
        Assertions.assertFalse(provide.canRobotAcceptFluid(ANY_FLUID), "…and does not open unloading");

        GatedStation accept = new GatedStation().with(new ActionStationAcceptFluids());
        Assertions.assertTrue(accept.canRobotAcceptFluid(LAVA_ONLY), "an unfiltered Accept Fluids takes any fluid");
        Assertions.assertFalse(accept.canRobotExtractFluid(ANY_FLUID), "…and does not open extraction");
    }

    @Test
    public void aBucketParameterFiltersAFluidActionByTheFluidItHolds() {
        assertBucketReadsAsWater();
        GatedStation station = new GatedStation()
                .with(new ActionStationProvideFluids(), stack(new ItemStack(Items.WATER_BUCKET)))
                .with(new ActionStationAcceptFluids(), stack(new ItemStack(Items.LAVA_BUCKET)));

        Assertions.assertTrue(station.canRobotExtractFluid(WATER_ONLY), "a water-bucket provider offers water");
        Assertions.assertFalse(station.canRobotExtractFluid(LAVA_ONLY), "…and not lava");
        Assertions.assertTrue(station.canRobotAcceptFluid(LAVA_ONLY), "a lava-bucket acceptor takes lava");
        Assertions.assertFalse(station.canRobotAcceptFluid(WATER_ONLY), "…and not water");
    }

    @Test
    public void aFluidActionFilteredOnlyByNonFluidItemsMatchesNothing() {
        GatedStation station = new GatedStation().with(new ActionStationProvideFluids(), stack(stone));

        Assertions.assertFalse(station.canRobotExtractFluid(ANY_FLUID),
                "a filter is SET (stone) but holds no fluid, so nothing can match it — not a pass-through");
    }

    @Test
    public void theWorkFilterReadsBucketsAsTheirFluid() {
        assertBucketReadsAsWater();
        GatedStation station = new GatedStation().with(new ActionRobotFilter(), stack(new ItemStack(Items.WATER_BUCKET)));

        IFluidFilter filter = station.getRobotFluidFilter();
        Assertions.assertTrue(filter.matches(water()), "a water-bucket work filter wants water");
        Assertions.assertFalse(filter.matches(lava()), "…not lava");
        Assertions.assertFalse(filter.matches(FluidStack.EMPTY), "…and never 'no fluid'");
    }

    @Test
    public void aWorkFilterOfOnlyNonFluidItemsWantsNoFluidAtAll() {
        GatedStation station = new GatedStation().with(new ActionRobotFilter(), stack(stone));

        IFluidFilter filter = station.getRobotFluidFilter();
        Assertions.assertFalse(filter.matches(water()),
                "7.1.x's array fluid filter over fluidless stacks matches nothing — a fluid carrier set up this "
                        + "way never loads, rather than silently loading everything");
        Assertions.assertFalse(filter.matches(lava()));
    }

    @Test
    public void theWorkFilterIsTheUnionOfItsBuckets() {
        assertBucketReadsAsWater();
        GatedStation station = new GatedStation().with(new ActionRobotFilter(),
                stack(new ItemStack(Items.WATER_BUCKET)), stack(new ItemStack(Items.LAVA_BUCKET)), stack(stone));

        IFluidFilter filter = station.getRobotFluidFilter();
        Assertions.assertTrue(filter.matches(water()));
        Assertions.assertTrue(filter.matches(lava()), "a stone beside two buckets does not narrow the fluid set");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** Liveness precondition: the fluid assertions above are only meaningful if a bucket's fluid is readable in
     *  this environment (item fluid capabilities bind during mod loading). */
    private static void assertBucketReadsAsWater() {
        Assertions.assertEquals(Fluids.WATER,
                buildcraft.lib.misc.FluidUtilBC.getFluidContained(new ItemStack(Items.WATER_BUCKET)).getFluid(),
                "precondition: a water bucket must read as holding water");
    }

    private static StatementParameterItemStack stack(ItemStack stack) {
        return new StatementParameterItemStack(stack);
    }

    private static MockRobotAccess pickerRobot() {
        MockRobotAccess robot = new MockRobotAccess();
        robot.setBoard(BoardRobotPickerNBT.INSTANCE.create(robot));
        return robot;
    }

    private static MockRobotAccess lumberjackRobot() {
        MockRobotAccess robot = new MockRobotAccess();
        robot.setBoard(BoardRobotLumberjackNBT.INSTANCE.create(robot));
        return robot;
    }

    /** The real {@link DockingStationPipe} policy code over a fixed gate-action list — the only override is the
     *  action source, which in production walks the host pipe's {@code PluggableGate}s. */
    private static final class GatedStation extends DockingStationPipe {
        private final List<StatementSlot> slots = new ArrayList<>();

        GatedStation with(IStatement action, IStatementParameter... params) {
            StatementSlot slot = new StatementSlot();
            slot.statement = action;
            slot.parameters = params;
            slots.add(slot);
            return this;
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return slots;
        }
    }
}
