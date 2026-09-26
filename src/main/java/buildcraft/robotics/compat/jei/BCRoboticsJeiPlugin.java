/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.lib.compat.jei.JeiSubtypes;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsRecipes;
import buildcraft.robotics.client.gui.GuiProgrammingTable;
import buildcraft.silicon.BCSiliconItems;

/**
 * JEI integration for robotics: the Programming Table category (every board program, the cost, and the grid cell
 * to click), the table as its catalyst, a click-through on the table GUI's power bar, and subtype keys that tell
 * boards and robots apart by program (robots also by charged vs drained) — without those, JEI merges every board into
 * one entry (and every robot into another) because the program rides in {@code CUSTOM_DATA}. See
 * {@link RoboticsJeiSubtypes}.
 */
@JeiPlugin
public class BCRoboticsJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:robotics_jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        JeiSubtypes.register(registration, BCRoboticsItems.REDSTONE_BOARD.get(), RoboticsJeiSubtypes::boardKey);
        JeiSubtypes.register(registration, BCRoboticsItems.ROBOT.get(), RoboticsJeiSubtypes::robotKey);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ProgrammingTableCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The registry is filled at ServerAboutToStart (integrated server) or LoggingIn (multiplayer client); the
        // relative order of the latter and JEI's own start is not ours to rely on, and the call is once-per-JVM.
        BCRoboticsRecipes.ensureInitialized();
        registration.addRecipes(ProgrammingRecipeJeiTypes.PROGRAMMING, ProgrammingRecipeCollector.collect());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ProgrammingRecipeJeiTypes.PROGRAMMING, BCSiliconItems.PROGRAMMING_TABLE.get());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // The table GUI's laser-energy bar (GuiProgrammingTable.RECT_PROGRESS) opens the category — clear of the
        // option grid (x 43-150), which the GUI hit-tests itself.
        registration.addRecipeClickArea(GuiProgrammingTable.class, 164, 36, 4, 70,
                ProgrammingRecipeJeiTypes.PROGRAMMING);
    }
}
