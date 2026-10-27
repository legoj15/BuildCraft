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
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.compat.viewer.ProgrammingRecipeCollector;
import buildcraft.robotics.compat.viewer.ProgrammingRecipeEntry;
import buildcraft.robotics.compat.viewer.RoboticsSubtypes;
import buildcraft.silicon.BCSiliconItems;

/** The guide's Programming Table lookup ({@code type="programming"}): blank Redstone Board → programmed board. A
 *  programmed board's page shows its own step; a blank board's page lists programming among its uses as one panel
 *  cycling through every program. Steps come from the same JEI-free collector the JEI category uses, so the guide
 *  and JEI can never disagree about what the table makes. Pin one step with
 *  {@code <recipe id="<board id>" type="programming"/>}, e.g. {@code buildcraftunofficial:boardRobotMiner}. */
public enum GuideProgrammingRecipes implements IStackRecipes {
    INSTANCE;

    @Nullable
    private volatile List<LaserTableStep> steps;

    /** Built on first use and kept: the board registry and table recipes are fixed once the game has loaded. An
     *  empty result is not kept, so a lookup made before the recipes registered retries next time. */
    List<LaserTableStep> steps() {
        List<LaserTableStep> cached = steps;
        if (cached != null) {
            return cached;
        }
        BCRoboticsRecipes.ensureInitialized();
        List<LaserTableStep> built = new ArrayList<>();
        for (ProgrammingRecipeEntry entry : ProgrammingRecipeCollector.collect()) {
            String id = entry.output().getItem() instanceof ItemRedstoneBoard
                    ? RoboticsSubtypes.boardKey(entry.output()) : entry.id();
            built.add(new LaserTableStep(id == null ? entry.id() : id, List.of(entry.inputs()),
                    List.of(entry.output()), entry.microJoules()));
        }
        if (!built.isEmpty()) {
            steps = List.copyOf(built);
        }
        return built;
    }

    private static ItemStack table() {
        return new ItemStack(BCSiliconItems.PROGRAMMING_TABLE.get());
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
