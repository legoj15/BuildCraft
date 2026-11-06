/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.render;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.IItemPluggable;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.RobotStationPluggable;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.silicon.plug.PluggableLens;

import buildcraft.transport.BCTransportItems;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.plug.PluggablePowerAdaptor;

/**
 * Pins the contract that drives the dynamic placement-preview outline (see
 * {@link PipePlacementHighlight}): for every {@link IItemPluggable}, the AABB returned by
 * {@code getPlacementBoundingBox} must match the AABB of the {@link buildcraft.api.transport.pluggable.PipePluggable}
 * that {@code onPlace} would actually produce. If the two ever diverge the preview lies — either
 * shrinking behind the larger placed pluggable (the regression this whole change fixes for facades,
 * power adaptors and lenses) or extending past a smaller one.
 *
 * <p>Only the pluggables whose static box differs from the gate-sized default are pinned here.
 * Gate / pulsar / timer / light-sensor items inherit {@link IItemPluggable}'s default — which
 * matches their concrete pluggables — and a regression there would be obvious in-game.
 */
public class PipePlacementHighlightTester {

    public static void testBlockerPreviewMatchesPlacedBox(GameTestHelper helper) {
        assertMatches(helper, "Blocker", (IItemPluggable) BCTransportItems.PLUG_BLOCKER.get(),
            new ItemStack(BCTransportItems.PLUG_BLOCKER.get()), PluggableBlocker::boundingBoxFor);
    }

    public static void testPowerAdaptorPreviewMatchesPlacedBox(GameTestHelper helper) {
        assertMatches(helper, "Power adaptor",
            (IItemPluggable) BCTransportItems.PLUG_POWER_ADAPTOR.get(),
            new ItemStack(BCTransportItems.PLUG_POWER_ADAPTOR.get()),
            PluggablePowerAdaptor::boundingBoxFor);
    }

    public static void testLensPreviewMatchesPlacedBox(GameTestHelper helper) {
        assertMatches(helper, "Lens", (IItemPluggable) BCSiliconItems.PLUG_LENS.get(),
            new ItemStack(BCSiliconItems.PLUG_LENS.get()), PluggableLens::boundingBoxFor);
    }

    public static void testFacadePreviewMatchesPlacedBox(GameTestHelper helper) {
        ItemPluggableFacade facadeItem = (ItemPluggableFacade) BCSiliconItems.PLUG_FACADE.get();
        ItemStack stoneFacade = facadeItem.createItemStack(FacadeInstance.createSingle(
            new FacadeBlockStateInfo(Blocks.STONE.defaultBlockState(), new ItemStack(Blocks.STONE), ImmutableSet.of()),
            false));
        // #129: a blank (air) facade is refused by onPlace, so the outline must not promise a placement for it.
        helper.assertTrue(facadeItem.canPlace(stoneFacade), "A stone facade must be placeable");
        helper.assertTrue(!facadeItem.canPlace(new ItemStack(facadeItem)),
            "A blank (air) facade must not be placeable, or the outline promises a refused placement");
        assertMatches(helper, "Facade", facadeItem, stoneFacade, PluggableFacade::boundingBoxFor);
    }

    public static void testRobotStationPreviewMatchesPlacedBox(GameTestHelper helper) {
        assertMatches(helper, "Robot station", (IItemPluggable) BCRoboticsItems.ROBOT_STATION.get(),
            new ItemStack(BCRoboticsItems.ROBOT_STATION.get()), RobotStationPluggable::boundingBoxFor);
    }

    private static void assertMatches(GameTestHelper helper, String label, IItemPluggable item,
                                       ItemStack stack,
                                       java.util.function.Function<Direction, AABB> placedBoxFor) {
        for (Direction side : Direction.values()) {
            AABB preview = item.getPlacementBoundingBox(stack, side);
            AABB placed = placedBoxFor.apply(side);
            helper.assertTrue(preview.equals(placed),
                label + " preview AABB must match placed AABB on " + side
                    + " (preview=" + preview + ", placed=" + placed + ")");
        }
        helper.succeed();
    }
}
