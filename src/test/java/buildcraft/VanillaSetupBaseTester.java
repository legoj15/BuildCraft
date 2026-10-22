/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;

import net.minecraft.core.HolderLookup;
//? if >=26.1 {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
import net.minecraft.data.registries.VanillaRegistries;

/**
 * Base class for unit tests that construct {@link net.minecraft.world.item.ItemStack}s.
 *
 * <p>The FML-JUnit environment ({@code neoForge { unitTest }} in build.gradle.kts) boots the loader,
 * bootstraps the registries and loads the mod — which is everything an ItemStack needs on the
 * {@code <26.1} nodes, where an item's default components are built in the {@code Item} constructor.
 * At 26.1 Mojang moved default components off the item onto {@code Holder.Reference}, bound in a
 * separate step that normally runs only during server resource load or client registry sync — neither
 * of which happens in a unit-test JVM, so {@code new ItemStack(...)} dies with "Components not bound
 * yet". This class closes that gap: extend it and the components are bound once per JVM, the same way
 * vanilla's own {@code RegistryComponentsReport} datagen binds them outside a server.
 *
 * <p>History: an earlier incarnation of this file called {@code Bootstrap.bootStrap()} for the
 * pre-FML-JUnit {@code test} task, where that call could never work (no loader, {@code FeatureFlags}
 * un-class-loadable) — it rotted silently with nothing extending it. {@link FmlJunitEnvironmentTest}
 * now pins the environment loudly, including this class's binding.
 */
public class VanillaSetupBaseTester {

    private static final AtomicBoolean COMPONENTS_BOUND = new AtomicBoolean();

    @BeforeAll
    public static void bindDefaultItemComponents() {
        //? if >=26.1 {
        if (COMPONENTS_BOUND.compareAndSet(false, true)) {
            // NeoForge's CommonHooks.validateComponent (an IDE-only dev aid, gated on this flag)
            // rejects the anonymous empty-named HolderSets the datagen lookup returns for tag-valued
            // components (e.g. PROVIDES_BANNER_PATTERNS) — classes without equals/hashCode. In a real
            // server bind the loaded registries return proper Named sets, and in production the check
            // is skipped entirely; skipping it here reproduces the production path, nothing more.
            boolean wasIde = net.minecraft.SharedConstants.IS_RUNNING_IN_IDE;
            net.minecraft.SharedConstants.IS_RUNNING_IN_IDE = false;
            try {
                // vanillaLookup() composes the frozen built-in registries with the code-built vanilla
                // dynamic ones (damage types, jukebox songs, ...) — the exact provider vanilla's
                // RegistryComponentsReport hands to the same build() call in datagen.
                BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                        .build(vanillaLookup())
                        .forEach(pending -> pending.apply());
            } finally {
                net.minecraft.SharedConstants.IS_RUNNING_IN_IDE = wasIde;
            }
        }
        //?}
    }

    /**
     * The vanilla datagen registry lookup: the frozen built-in registries plus the code-built vanilla dynamic
     * ones. Available on every node — creative-tab {@code ItemDisplayParameters} need one too, with or without
     * the 26.1 component binding. 26.3 split {@code VanillaRegistries.createLookup()} into a world layer
     * ({@code createWorldLookup()}, what vanilla's own datagen hands the component report) and a reloadable layer
     * (loot, recipes, advancements) stacked on top of it; the world layer is the equivalent of the old single one.
     */
    public static HolderLookup.Provider vanillaLookup() {
        //? if >=26.3 {
        return VanillaRegistries.createWorldLookup();
        //?} else {
        /*return VanillaRegistries.createLookup();
        *///?}
    }
}
