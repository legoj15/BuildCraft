/* Copyright (c) 2026 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.lib.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import buildcraft.lib.registry.LegacyAliases.Mapping;

/**
 * Verifies every backwards-compat alias registered by {@link LegacyAliases} resolves to a live
 * registry entry. Aliases are checked through {@code Registry.getValue}, which is exactly the path
 * a loading save takes — {@code getValue} applies {@code resolve()} — so this pins the shipped
 * behaviour without needing an actual old world. Catches typo'd targets, wrong namespaces, and
 * dangling aliases left behind by a future rename.
 */
public class LegacyAliasTester {

    // An ID nothing registers, used to read each registry's "missing" sentinel. ITEM/BLOCK/FLUID are
    // DefaultedRegistry (missing → AIR/EMPTY, not null), so we compare against this rather than null.
    private static final Identifier ABSENT = Identifier.parse("buildcraftunofficial:__absent_sentinel__");

    public static void testAliasesResolve(GameTestHelper helper) {
        if (LegacyAliases.MAPPINGS.isEmpty()) {
            throw new IllegalStateException("LegacyAliases.MAPPINGS is empty — LegacyAliases.init() did not run");
        }

        StringBuilder errors = new StringBuilder();
        for (Mapping m : LegacyAliases.MAPPINGS) {
            Registry<?> registry = m.registry();
            //? if >=1.21.10 {
            Object missing = registry.getValue(ABSENT);
            Object target = registry.getValue(m.to());
            Object resolved = registry.getValue(m.from());
            //?} else {
            /*Object missing = registry.get(ABSENT);
            Object target = registry.get(m.to());
            Object resolved = registry.get(m.from());*/
            //?}

            if (target == missing) {
                errors.append("\n  dangling target: ").append(m.from()).append(" -> ").append(m.to());
            } else if (resolved != target) {
                errors.append("\n  did not resolve: ").append(m.from()).append(" -> ").append(m.to())
                        .append(" (got ").append(resolved).append(')');
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("LegacyAliases has " + errors.length()
                    + " broken alias(es):" + errors);
        }

        // Representative spot-checks across generations and migration kinds.
        assertItemAlias("buildcrafttransport:pipe_wood_item", "buildcraftunofficial:pipe_wood_item");          // namespace flip
        assertItemAlias("buildcraftcore:wrench", "buildcraftunofficial:wrench");                               // namespace flip
        assertItemAlias("buildcraftsilicon:redstone_red_chipset", "buildcraftunofficial:chipset_redstone");    // gen-2 rename
        assertItemAlias("buildcraftunofficial:redstone_red_chipset", "buildcraftunofficial:chipset_redstone"); // gen-3 rename
        assertItemAlias("buildcraftsilicon:redstone_chipset", "buildcraftunofficial:chipset_redstone");        // 1.12.2 metadata collapse
        assertItemAlias("buildcrafttransport:wire", "buildcraftunofficial:wire_white");                        // metadata collapse
        assertItemAlias("buildcraftcore:fragile_fluid_shard", "buildcraftunofficial:fragile_fluid_container"); // 1.12.2 rename
        assertItemAlias("buildcraftfactory:gel", "buildcraftunofficial:gelled_water");                         // 1.12.2 rename

        helper.succeed();
    }

    private static void assertItemAlias(String from, String to) {
        //? if >=1.21.10 {
        Item target = BuiltInRegistries.ITEM.getValue(Identifier.parse(to));
        Item resolved = BuiltInRegistries.ITEM.getValue(Identifier.parse(from));
        Item air = BuiltInRegistries.ITEM.getValue(ABSENT);
        //?} else {
        /*Item target = BuiltInRegistries.ITEM.get(Identifier.parse(to));
        Item resolved = BuiltInRegistries.ITEM.get(Identifier.parse(from));
        Item air = BuiltInRegistries.ITEM.get(ABSENT);*/
        //?}
        if (target == air) {
            throw new IllegalStateException("Spot-check target is not registered: " + to);
        }
        if (resolved != target) {
            throw new IllegalStateException("Spot-check failed: " + from + " did not resolve to " + to);
        }
    }
}
