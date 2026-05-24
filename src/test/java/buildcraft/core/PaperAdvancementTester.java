/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core;

import java.util.List;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Pins the JSON contract for the four-criterion {@code buildcraftunofficial:paper}
 * advancement. Per {@code CLAUDE.md}'s "Player-state testing limitation",
 * {@code makeMockPlayer} returns a plain {@code Player}, not a {@code ServerPlayer},
 * so the actual award calls inside {@link buildcraft.lib.misc.AdvancementUtil} short-
 * circuit — this test instead pins the wiring around the award: the JSON keys match
 * the {@link PaperAdvancement} constants used at the four grant sites, and the
 * {@code requirements} array is shaped so all four criteria are required (one
 * single-element group per criterion) rather than any-one (a single group with all
 * four).
 */
public class PaperAdvancementTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testPaperAdvancementContract(GameTestHelper helper) {
        // (a) Advancement is loaded.
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(PaperAdvancement.ID);
        assertTrue(holder != null, "buildcraftunofficial:paper advancement is not loaded");

        // (b) All four criteria are present with the exact names the grant sites use.
        var criteria = holder.value().criteria();
        assertTrue(criteria.containsKey(PaperAdvancement.WRITE_TO_LIST),
                "paper.json missing 'write_to_list' criterion — grant in ContainerList.ListPhantomSlot.set would no-op");
        assertTrue(criteria.containsKey(PaperAdvancement.WRITE_TO_BLUEPRINT),
                "paper.json missing 'write_to_blueprint' criterion — Architect blueprint grant would no-op");
        assertTrue(criteria.containsKey(PaperAdvancement.WRITE_TO_TEMPLATE),
                "paper.json missing 'write_to_template' criterion — Architect template grant would no-op");
        assertTrue(criteria.containsKey(PaperAdvancement.CAPTURE_WITH_SCHEMATIC),
                "paper.json missing 'capture_with_schematic' criterion — ItemSchematicSingle capture grant would no-op");
        assertTrue(criteria.size() == 4,
                "paper.json must declare exactly 4 criteria, found " + criteria.size());

        // (c) requirements shape — four single-element groups (AND between groups,
        //     OR within a group), not one four-element group. A mis-shaped requirements
        //     array would silently turn the advancement into "earn any one" instead of
        //     "earn all four", and the x/4 progress UI would render but complete on first.
        AdvancementRequirements requirements = holder.value().requirements();
        List<List<String>> groups = requirements.requirements();
        assertTrue(groups.size() == 4,
                "paper.json requirements must have 4 groups (one per criterion); found " + groups.size());
        for (List<String> group : groups) {
            assertTrue(group.size() == 1,
                    "each requirements group must hold exactly one criterion (AND semantics) — "
                            + "found a group of size " + group.size());
        }

        // (d) ID constant matches the file path it's loaded from. Cheap, but if a future
        //     rename of the JSON loses the constant in sync this is the first test to fail.
        assertTrue(PaperAdvancement.ID.equals(Identifier.parse("buildcraftunofficial:paper")),
                "PaperAdvancement.ID drift — must remain buildcraftunofficial:paper");

        helper.succeed();
    }
}
