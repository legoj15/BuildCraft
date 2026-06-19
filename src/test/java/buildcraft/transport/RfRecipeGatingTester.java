/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Guards the {@code buildcraftunofficial:rf_enabled} recipe condition (see
 * {@link buildcraft.transport.recipe.RfEnabledCondition}) that gates the RF <em>pipe</em> recipes behind
 * the {@code disableRfPipe} config.
 *
 * <p>Asserts two invariants directly:
 * <ul>
 *   <li>each RF <em>pipe</em> recipe — a forward kinesis&rarr;RF craft and a reverse RF&rarr;kinesis
 *       downgrade — is loaded into the datapack recipe set <em>iff</em> RF is enabled
 *       ({@code !disableRfPipe});</li>
 *   <li>the MJ Dynamo and RF Engine — the only MJ&harr;FE conversion bridges — are <em>always</em> loaded
 *       regardless of the toggle, since disabling BC's RF pipes must not sever interop with another mod's
 *       energy system.</li>
 * </ul>
 * Under the default config (RF enabled) this proves the condition registered and kept the pipe recipes;
 * booting with {@code disableRfPipe=true} proves the pipe recipes are dropped while the converters survive.
 * A misregistered/misspelled condition id would fail datapack parsing outright, which this test also catches.
 */
public class RfRecipeGatingTester {

    /** RF pipe recipes gated by the rf_enabled condition: present iff RF is enabled. */
    private static final String[] GATED_PIPE_RECIPE_IDS = {
            "buildcraftunofficial:pipe_wood_rf",            // forward kinesis -> RF pipe craft
            "buildcraftunofficial:pipe_wood_power_from_rf", // reverse RF -> kinesis downgrade
    };

    /** MJ<->FE conversion bridges: must stay craftable whether or not RF pipes are disabled. */
    private static final String[] ALWAYS_PRESENT_RECIPE_IDS = {
            "buildcraftunofficial:engine_rf", // RF (Forge Energy) engine — FE -> MJ
            "buildcraftunofficial:mj_dynamo", // MJ dynamo — MJ -> FE
    };

    private static boolean isLoaded(GameTestHelper helper, String id) {
        // 1.21.1 has no ServerLevel.recipeAccess() and keys recipes by id (not ResourceKey); reach the
        // recipe manager via the server there.
        //? if >=1.21.10 {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
        Optional<RecipeHolder<?>> holder = helper.getLevel().recipeAccess().byKey(key);
        //?} else {
        /*Optional<RecipeHolder<?>> holder = helper.getLevel().getServer().getRecipeManager().byKey(Identifier.parse(id));*/
        //?}
        return holder.isPresent();
    }

    public static void testRfRecipesGatedByConfig(GameTestHelper helper) {
        boolean rfEnabled = !BCTransportConfig.disableRfPipe.get();
        for (String id : GATED_PIPE_RECIPE_IDS) {
            boolean present = isLoaded(helper, id);
            if (present != rfEnabled) {
                helper.fail(rfEnabled
                        ? id + " should be craftable when RF is enabled, but it is missing"
                        : id + " should be removed when disableRfPipe is set, but it is still loaded");
                return;
            }
        }
        for (String id : ALWAYS_PRESENT_RECIPE_IDS) {
            if (!isLoaded(helper, id)) {
                helper.fail(id + " is an MJ<->FE converter and must stay craftable even when disableRfPipe is"
                        + " set, but it was dropped");
                return;
            }
        }
        helper.succeed();
    }
}
