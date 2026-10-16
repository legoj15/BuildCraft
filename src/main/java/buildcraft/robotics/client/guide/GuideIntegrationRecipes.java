/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.client.guide.parts.recipe.IStackRecipes;

import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.compat.jei.IntegrationRecipeCollector;
import buildcraft.robotics.compat.jei.IntegrationRecipeJei;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconItems;

/** The guide's Integration Table lookup ({@code type="integration"}): blank Robot + programmed board → that robot.
 *  A programmed robot's page (charged or drained) shows its own step; a blank robot's page lists integration among
 *  its uses as one panel cycling through every program, and a programmed board's page lists the one robot it makes.
 *  Steps come from the same JEI-free collector the JEI category uses. Pin one step with
 *  {@code <recipe id="<board id>" type="integration"/>}, e.g. {@code buildcraftunofficial:boardRobotMiner}. */
public enum GuideIntegrationRecipes implements IStackRecipes {
    INSTANCE;

    @Nullable
    private volatile List<LaserTableStep> steps;

    /** Built on first use and kept — see {@link GuideProgrammingRecipes#steps()}. */
    List<LaserTableStep> steps() {
        List<LaserTableStep> cached = steps;
        if (cached != null) {
            return cached;
        }
        BCRoboticsRecipes.ensureInitialized();
        List<LaserTableStep> built = new ArrayList<>();
        for (IntegrationRecipeJei entry : IntegrationRecipeCollector.collect()) {
            List<List<ItemStack>> slots = new ArrayList<>();
            slots.add(List.of(entry.center()));
            for (ItemStack expansion : entry.expansions()) {
                slots.add(List.of(expansion));
            }
            String id = ItemRobot.getBoardId(entry.outputs().get(0));
            built.add(new LaserTableStep(id == null ? entry.id() : id, List.copyOf(slots), entry.outputs(),
                    entry.microJoules()));
        }
        if (!built.isEmpty()) {
            steps = List.copyOf(built);
        }
        return built;
    }

    private static ItemStack table() {
        return new ItemStack(BCSiliconItems.INTEGRATION_TABLE.get());
    }

    @Override
    public List<GuidePartFactory> getUsages(@Nonnull ItemStack stack) {
        return LaserTableLookup.usages(steps(), stack, table());
    }

    @Override
    public List<GuidePartFactory> getRecipes(@Nonnull ItemStack stack) {
        return LaserTableLookup.recipes(steps(), stack, table());
    }

    @Override
    @Nullable
    public GuidePartFactory getFactoryById(String id) {
        return LaserTableLookup.byId(steps(), id, table());
    }
}
