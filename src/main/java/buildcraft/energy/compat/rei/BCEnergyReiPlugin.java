/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.rei;

//? if >=26.3 {
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.rei.ReiDisplay;
//?}
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import buildcraft.api.fuels.IFuel;

import buildcraft.energy.BCEnergyItems;
import buildcraft.energy.BCEnergyRecipes;
import buildcraft.energy.client.gui.ScreenEngineIron;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.compat.viewer.CombustionCoolant;
import buildcraft.energy.compat.viewer.EngineFuelCollector;
import buildcraft.energy.compat.viewer.StirlingFuel;
import buildcraft.lib.compat.rei.ReiScreens;

/**
 * REI integration plugin for BuildCraft Energy, REI's twin of {@code BCEnergyJeiPlugin}: the combustion engine's
 * fuels and coolants and the Stirling engine's solid fuels as categories, the engines as their workstations, and the
 * same click areas on the engine GUIs. The entries come from the shared {@link EngineFuelCollector}.
 *
 * <p>On 26.3 a Stirling fuel's burn time is a datapack value only a server can resolve, so the server sends the list
 * ({@code StirlingFuelSync}) and it can arrive after REI has loaded its plugins. The Stirling category there is a
 * dynamic generator that reads the latest list on every lookup instead of a fixed set of displays (JEI copies its own
 * furnace-fuel category instead, which REI does not have).
 */
@REIPluginClient
public class BCEnergyReiPlugin implements REIClientPlugin {

    private static final CombustionFuelReiCategory COMBUSTION_FUEL = new CombustionFuelReiCategory();
    private static final CombustionCoolantReiCategory COMBUSTION_COOLANT = new CombustionCoolantReiCategory();
    private static final StirlingFuelReiCategory STIRLING_FUEL = new StirlingFuelReiCategory();

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(COMBUSTION_FUEL);
        registry.add(COMBUSTION_COOLANT);
        registry.add(STIRLING_FUEL);
        // U on the combustion engine shows its fuels and coolants; on the Stirling engine, its solid fuels.
        registry.addWorkstations(CombustionFuelReiCategory.ID, EntryStacks.of(BCEnergyItems.ENGINE_IRON.get()));
        registry.addWorkstations(CombustionCoolantReiCategory.ID, EntryStacks.of(BCEnergyItems.ENGINE_IRON.get()));
        registry.addWorkstations(StirlingFuelReiCategory.ID, EntryStacks.of(BCEnergyItems.ENGINE_STONE.get()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // A multiplayer client fills the registries at LoggingIn; REI's reload may come first.
        BCEnergyRecipes.ensureInitialized();
        for (IFuel fuel : EngineFuelCollector.combustionFuels()) {
            registry.add(COMBUSTION_FUEL.display(fuel));
        }
        for (CombustionCoolant coolant : EngineFuelCollector.coolants()) {
            registry.add(COMBUSTION_COOLANT.display(coolant));
        }
        //? if >=26.3 {
        registry.registerDisplayGenerator(StirlingFuelReiCategory.ID, new StirlingFuelGenerator());
        //?} else {
        /*for (StirlingFuel fuel : EngineFuelCollector.stirlingFuels()) {
            registry.add(STIRLING_FUEL.display(fuel));
        }
        *///?}
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        // The Stirling engine's flame indicator.
        ReiScreens.clickArea(registry, ScreenEngineStone.class, 81, 25, 14, 14, StirlingFuelReiCategory.ID);
        // The strip between the combustion engine's fuel and coolant tanks, clear of its gauges.
        ReiScreens.clickArea(registry, ScreenEngineIron.class, 44, 22, 34, 52,
                CombustionFuelReiCategory.ID, CombustionCoolantReiCategory.ID);
    }
    //? if >=26.3 {

    /**
     * The Stirling category's displays, built from the latest server-sent list. The sync replaces that list wholesale,
     * so the displays are rebuilt only when its identity changes, and every lookup hands back the same display objects
     * (REI merges a category's results into a set, so a display reached two ways shows once).
     */
    private static final class StirlingFuelGenerator implements DynamicDisplayGenerator<ReiDisplay<StirlingFuel>> {
        private List<StirlingFuel> builtFrom;
        private List<ReiDisplay<StirlingFuel>> displays = List.of();

        private synchronized List<ReiDisplay<StirlingFuel>> displays() {
            List<StirlingFuel> fuels = EngineFuelCollector.stirlingFuels();
            if (fuels != builtFrom) {
                List<ReiDisplay<StirlingFuel>> built = new ArrayList<>(fuels.size());
                for (StirlingFuel fuel : fuels) {
                    built.add(STIRLING_FUEL.display(fuel));
                }
                displays = List.copyOf(built);
                builtFrom = fuels;
            }
            return displays;
        }

        /** Usages of a fuel item are its own display; of the Stirling engine (the workstation), all of them. */
        @Override
        public Optional<List<ReiDisplay<StirlingFuel>>> getUsageFor(EntryStack<?> entry) {
            if (!(entry.getValue() instanceof ItemStack stack) || stack.isEmpty()) return Optional.empty();
            if (stack.is(BCEnergyItems.ENGINE_STONE.get())) return Optional.of(displays());
            List<ReiDisplay<StirlingFuel>> matching = new ArrayList<>();
            for (ReiDisplay<StirlingFuel> display : displays()) {
                if (stack.is(display.recipe().fuel().getItem())) {
                    matching.add(display);
                }
            }
            return matching.isEmpty() ? Optional.empty() : Optional.of(matching);
        }

        /** Opening the category itself (its tab, or the engine GUI's click area) lists every fuel. */
        @Override
        public Optional<List<ReiDisplay<StirlingFuel>>> generate(ViewSearchBuilder builder) {
            return builder.getCategories().contains(StirlingFuelReiCategory.ID)
                    ? Optional.of(displays())
                    : Optional.empty();
        }
    }
    //?}
}
