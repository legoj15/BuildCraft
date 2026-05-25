/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import buildcraft.lib.compat.jei.BCGhostIngredientHandler;
import buildcraft.lib.compat.jei.BlueprintTransferHandler;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.item.ItemPluggableLens;

/**
 * JEI integration plugin for BuildCraft Silicon.
 * Registers the Advanced Crafting Table as a crafting station with recipe
 * transfer support and a clickable progress bar, and surfaces every Assembly
 * Table recipe via a custom category — facades collapse into a single cycling
 * entry so JEI's index doesn't explode with thousands of variants.
 */
@JeiPlugin
public class BCSiliconJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Without subtype interpreters JEI collapses every variant of these items into a single
        // entry, so pressing R/U on any lens/filter/gate/facade in-world shows recipes for ALL of
        // them at once (see the runtime warning "N duplicate items were found in 'BuildCraft
        // Pluggables/Facades' creative tab's: displayItems"). Each interpreter returns the
        // smallest key that uniquely identifies a *visual* variant — two stacks with the same
        // key are merged in JEI, two with different keys get their own entry and their own
        // recipe lookup.

        // Lens / Filter: colour × mode (lens or filter). "clear" stands in for the no-colour case.
        registration.registerSubtypeInterpreter(
                BCSiliconItems.PLUG_LENS.get(),
                (stack, context) -> {
                    DyeColor colour = ItemPluggableLens.getColour(stack);
                    boolean isFilter = ItemPluggableLens.isFilter(stack);
                    return (colour == null ? "clear" : colour.getName()) + ":" + isFilter;
                }
        );

        // Gate: material + logic + modifier as already encoded by GateVariant.getVariantName()
        // (the same key the item model dispatch uses, so two gates with the same key render
        // identically and should share a JEI entry).
        registration.registerSubtypeInterpreter(
                BCSiliconItems.PLUG_GATE.get(),
                (stack, context) -> ItemPluggableGate.getVariant(stack).getVariantName()
        );

        // Facade: the entire "facade" NBT compound — encodes the wrapped block state(s), phased
        // colour mappings, and the hollow flag. CompoundTag has structural equals/hashCode in
        // 26.1, so two facades wrapping the same state(s) collapse to one JEI entry while two
        // wrapping different states get separate entries (and therefore separate recipe lookups).
        // For the bulk Basic-facade case there's a single state, so the compound is small and
        // comparisons are cheap; the Phased case keys on the multi-state list and isHollow bit.
        registration.registerSubtypeInterpreter(
                BCSiliconItems.PLUG_FACADE.get(),
                (stack, context) -> NBTUtilBC.getItemData(stack).getCompoundOrEmpty("facade")
        );
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AssemblyTableCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AssemblyRecipeJeiTypes.ASSEMBLY, AssemblyRecipeCollector.collect());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // Register the phantom-slot blueprint transfer handler for the Advanced Crafting Table.
        // Clicking JEI's "+" button will set the 3x3 blueprint grid via handlePlacement().
        registration.addRecipeTransferHandler(
                new BlueprintTransferHandler<>(
                        ContainerAdvancedCraftingTable.class,
                        BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get()
                ),
                RecipeTypes.CRAFTING
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Clicking the progress arrow between the blueprint grid and the output slot
        // opens JEI's crafting recipe view. The arrow is at approximately (93, 32) size 23x16.
        registration.addRecipeClickArea(
                GuiAdvancedCraftingTable.class,
                93, 32, 23, 16,
                RecipeTypes.CRAFTING
        );
        // Allow dragging ingredients from JEI onto phantom blueprint slots
        registration.addGhostIngredientHandler(GuiAdvancedCraftingTable.class, new BCGhostIngredientHandler<>());
        
        // Let JEI know about GuiGate's winglettes
        registration.addGuiContainerHandler(buildcraft.silicon.gui.GuiGate.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<buildcraft.silicon.gui.GuiGate>() {
            @Override
            public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(buildcraft.silicon.gui.GuiGate containerScreen) {
                java.util.List<net.minecraft.client.renderer.Rect2i> extraAreas = new java.util.ArrayList<>();
                for (buildcraft.lib.gui.IGuiElement element : containerScreen.mainGui.shownElements) {
                    if (element instanceof buildcraft.lib.gui.statement.GuiElementStatementSource) {
                        extraAreas.add(new net.minecraft.client.renderer.Rect2i(
                                (int) element.getX(), (int) element.getY(),
                                (int) element.getWidth(), (int) element.getHeight()
                        ));
                    }
                }
                return extraAreas;
            }
        });
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Register the Advanced Crafting Table block as a crafting catalyst.
        // Pressing "U" (uses) on it in JEI shows all crafting recipes.
        registration.addCraftingStation(RecipeTypes.CRAFTING, BCSiliconItems.ADVANCED_CRAFTING_TABLE.get());
        // Assembly Table: catalyst for the assembly category.
        registration.addCraftingStation(AssemblyRecipeJeiTypes.ASSEMBLY, BCSiliconItems.ASSEMBLY_TABLE.get());
    }
}
