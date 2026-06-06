/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.lib.gui.GuiBC8;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers data component types as subtype differentiators so JEI can
 * distinguish items that share the same item ID but differ by component
 * (e.g. coloured paintbrushes).
 */
@JeiPlugin
public class BCCoreJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Tell JEI to differentiate paintbrush stacks by their brush_color component.
        // This handles both ingredient list display AND recipe output matching.
        //? if >=1.21.10 {
        registration.registerFromDataComponentTypes(
                BCCoreItems.PAINTBRUSH.get(),
                BCCore.BRUSH_COLOR.get()
        );
        //?} else {
        /*// 1.21.1 JEI has no registerFromDataComponentTypes — hand-write the equivalent
        // interpreter, keying the paintbrush stack on its brush_color (DyeColor) component.
        registration.registerSubtypeInterpreter(
                BCCoreItems.PAINTBRUSH.get(),
                (stack, context) -> {
                    net.minecraft.world.item.DyeColor color = stack.get(BCCore.BRUSH_COLOR.get());
                    return color == null
                            ? mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter.NONE
                            : color.toString();
                }
        );*/
        //?}

        // Differentiate fragile fluid shards by their stored fluid type, but
        // ignore the mB amount in the FLUID_CONTENT component. registerFromDataComponentTypes
        // would key on the whole SimpleFluidContent (including amount), so a 250 mB
        // and 500 mB water shard would hash to different ingredients and the recipe
        // alias (a 500 mB shard, see FluidContainerAliases) would only match
        // exactly-full shards. Keying on the fluid resource location alone keeps
        // alias matching robust regardless of how full the player's shard is.
        registration.registerSubtypeInterpreter(
                BCCoreItems.FRAGILE_FLUID_CONTAINER.get(),
                (stack, context) -> {
                    FluidStack fluid = ItemFragileFluidContainer.getFluid(stack);
                    if (fluid.isEmpty()) {
                        return null;
                    }
                    //? if >=1.21.10 {
                    return BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                    //?} else {
                    /*return BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();*/
                    //?}
                }
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Tell JEI about BuildCraft ledger exclusion areas so the ingredient
        // list is pushed out of the way when ledgers are open.
        registration.addGenericGuiContainerHandler(GuiBC8.class, new BCGuiContainerHandler());
    }
}
