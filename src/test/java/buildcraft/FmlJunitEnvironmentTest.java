/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.fml.ModList;

/**
 * Pins the unit-test environment itself: the {@code test} task must boot a real FML loader (moddev's
 * {@code neoForge { unitTest }} wiring in build.gradle.kts) before the JUnit engine runs.
 *
 * <p>Without that wiring the failure mode is <b>silent</b>, which is why this file exists. A bare-JVM
 * {@code test} task cannot class-load {@code Entity} (NeoForge's {@code AttachmentHolder} static
 * initialiser asks {@code FMLEnvironment.isProduction()} and throws "There is no current FML Loader")
 * and cannot construct an {@code ItemStack} (registries never bootstrap) — but a suite whose tests were
 * all written to avoid those classes stays green and never notices. That is exactly how the old
 * Bootstrap-calling {@code VanillaSetupBaseTester} rotted for months with nothing extending it: the
 * environment broke and no test was in a position to say so. These assertions are in that position.
 *
 * <p>If this file fails wholesale on one node while the rest of the suite passes, suspect the unitTest
 * wiring for that node (build.gradle.kts is shared, but each node resolves its own NeoForge/FML), not
 * the test bodies.
 */
public class FmlJunitEnvironmentTest extends VanillaSetupBaseTester {

    @Test
    public void entityClassLoadsUnderTheFmlLoader() throws Exception {
        // Class.forName initialises the class, which is the whole point: Entity extends NeoForge's
        // AttachmentHolder, whose <clinit> throws without a live FML loader. Reflection rather than a
        // plain Entity.class reference so the assertion message survives even if loading dies.
        Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");

        Class<?> robotBase = Class.forName("buildcraft.api.robots.EntityRobotBase");
        Assertions.assertTrue(entity.isAssignableFrom(robotBase),
                "EntityRobotBase must be an Entity — and both must be class-loadable in unit tests; the "
                        + "robotics Ph3 suite had to contort around exactly this before the FML-JUnit "
                        + "environment landed");
    }

    @Test
    public void itemStacksCanBeConstructed() {
        // An ItemStack cannot exist registry-dead: its constructor resolves the item's holder — and on
        // the 26.x nodes it also reads the holder's default components, which only exist because this
        // class extends VanillaSetupBaseTester. This assertion pins the FULL chain a future
        // ItemStack-touching unit test relies on, on every node.
        ItemStack stack = new ItemStack(Items.APPLE);
        Assertions.assertFalse(stack.isEmpty(), "a freshly built stack of a real item is not empty");
        Assertions.assertEquals(1, stack.getCount(), "and it holds the default single item");
    }

    @Test
    public void buildcraftItselfIsLoadedAsAMod() {
        Assertions.assertTrue(ModList.get().isLoaded("buildcraftunofficial"),
                "the unitTest DSL declares buildcraftunofficial as the tested mod — if FML boots but the "
                        + "mod is absent, testedMod/loadedMods wiring regressed and any test touching BC "
                        + "registries or config will misbehave");
    }
}
