/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.compat.rei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

import dev.architectury.event.CompoundEventResult;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.core.BCCore;

import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.ledger.Ledger_Neptune;

/**
 * REI client-side integration plugin for BuildCraft Core, REI's twin of JEI's {@code BCGuiContainerHandler}: open
 * ledgers are exclusion zones (REI's overlay moves out of their way), and the fluid shown in any
 * {@link GuiElementFluidTank} is the focused stack under the mouse, so REI's recipe and usage keys work on a tank.
 * BuildCraft items tagged {@code c:hidden_from_recipe_viewers} (the facade variants), which JEI leaves out of its
 * ingredient list, are removed from REI's, which does not read that tag.
 */
@REIPluginClient
public class BCCoreReiClientPlugin implements REIClientPlugin {

    @Override
    public void registerEntries(EntryRegistry registry) {
        Predicate<EntryStack<?>> hidden = entry -> entry.getValue() instanceof ItemStack stack
                && stack.is(Tags.Items.HIDDEN_FROM_RECIPE_VIEWERS)
                && BCCore.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
        registry.removeEntryIf(hidden);
    }

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(GuiBC8.class, screen -> {
            Collection<Rectangle> areas = new ArrayList<>();
            for (IGuiElement element : screen.mainGui.shownElements) {
                if (element instanceof Ledger_Neptune ledger) {
                    int x = (int) ledger.getX();
                    int y = (int) ledger.getY();
                    int w = (int) Math.ceil(ledger.getWidth());
                    int h = (int) Math.ceil(ledger.getHeight());
                    if (w > 0 && h > 0) {
                        areas.add(new Rectangle(x, y, w, h));
                    }
                }
            }
            return areas;
        });
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerFocusedStack((screen, mouse) -> {
            if (!(screen instanceof GuiBC8<?> gui)) {
                return CompoundEventResult.pass();
            }
            for (IGuiElement element : gui.mainGui.shownElements) {
                if (!(element instanceof GuiElementFluidTank tankElem)) continue;

                double x = tankElem.getX();
                double y = tankElem.getY();
                if (mouse.x < x || mouse.y < y || mouse.x >= x + tankElem.getWidth()
                        || mouse.y >= y + tankElem.getHeight()) continue;

                BCFluidTank tank = tankElem.getTank();
                if (tank == null || tank.size() == 0) continue;

                FluidStack stack = tank.getFluidStack(0);
                long amount = tank.getAmountMb(0);
                if (stack.isEmpty() || amount <= 0) continue;

                EntryStack<?> entry = EntryStacks.of(stack.getFluid(), amount);
                return CompoundEventResult.interruptTrue(entry);
            }
            return CompoundEventResult.pass();
        });
    }
}
