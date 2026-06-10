package buildcraft.lib.test.guide;

import java.util.Collection;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache;
import buildcraft.lib.client.guide.parts.recipe.GuideCraftingFactory;
import buildcraft.lib.client.guide.parts.recipe.GuideCraftingRecipes;
import buildcraft.lib.client.guide.parts.recipe.GuideSmeltingRecipes;

/** Regression coverage for the guide book's recipe lookups running off
 * {@link ClientGuideRecipeCache} — the server-synced store that replaced the
 * integrated-server-only RecipeManager access (which was null on multiplayer clients,
 * silently dropping every crafting/smelting panel; RC4 playtest "Quarry guide book page
 * doesn't list its recipe").
 *
 * <p>A real client never populates the store from a game test (the sync events are
 * client-connection-scoped), so this seeds it directly from the test server's
 * RecipeManager — exactly the holder collection NeoForge's recipe sync delivers — and
 * exercises every lookup shape the guide's markdown tags use: recipes-by-output,
 * usages-by-ingredient, id-substring cycling, smelting by output, the furnace
 * all-smelting special case, and the empty-store multiplayer degradation. */
public class GuideRecipeLookupTester {

    public static void testGuideRecipeLookupFromSyncedCache(GameTestHelper helper) {
        Collection<RecipeHolder<?>> serverRecipes =
            helper.getLevel().getServer().getRecipeManager().getRecipes();
        ItemStack quarryStack = new ItemStack(BCBuildersBlocks.QUARRY.get());

        ClientGuideRecipeCache.setSynced(serverRecipes);
        try {
            // <recipes_usages stack="buildcraftunofficial:quarry"/> — creation panel
            List<GuidePartFactory> quarryRecipes =
                GuideCraftingRecipes.INSTANCE.getRecipes(quarryStack);
            if (quarryRecipes.size() != 1) {
                helper.fail("Expected exactly 1 crafting recipe for the Quarry from the synced"
                    + " cache, got " + quarryRecipes.size());
                return;
            }

            // usages-by-ingredient: the diamond gear page must list the quarry recipe
            List<GuidePartFactory> gearUsages =
                GuideCraftingRecipes.INSTANCE.getUsages(new ItemStack(BCCoreItems.GEAR_DIAMOND.get()));
            boolean quarryListed = gearUsages.stream().anyMatch(
                f -> f instanceof GuideCraftingFactory gcf && gcf.outputMatches(quarryStack));
            if (!quarryListed) {
                helper.fail("Diamond-gear usages (" + gearUsages.size() + " factories) did not"
                    + " include the quarry recipe");
                return;
            }

            // by-id-substring (backs <recipe_cycle match="..."/> and id ordering)
            if (GuideCraftingRecipes.gatherByIdMatch("buildcraftunofficial:quarry").size() != 1) {
                helper.fail("gatherByIdMatch(buildcraftunofficial:quarry) should select exactly"
                    + " the quarry recipe");
                return;
            }
            // plug_gate.md's crafting cycles: 12 gate AND->OR swap recipes fold into one panel
            if (GuideCraftingRecipes.getCyclingFactoryByIdMatch("_to_or") == null) {
                helper.fail("getCyclingFactoryByIdMatch(_to_or) matched no gate swap recipes");
                return;
            }

            // smelting by output (vanilla furnace recipes ride the same synced store)
            if (GuideSmeltingRecipes.INSTANCE.getRecipes(new ItemStack(Items.IRON_INGOT)).isEmpty()) {
                helper.fail("No smelting recipe found for iron ingot in the synced cache");
                return;
            }
            // furnace special case: the furnace page lists ALL smelting recipes
            List<GuidePartFactory> allSmelting =
                GuideSmeltingRecipes.INSTANCE.getUsages(new ItemStack(Blocks.FURNACE));
            if (allSmelting == null || allSmelting.size() < 20) {
                helper.fail("Furnace usages should list all vanilla smelting recipes, got "
                    + (allSmelting == null ? 0 : allSmelting.size()));
                return;
            }
        } finally {
            ClientGuideRecipeCache.clear();
        }

        // Empty store + no integrated server (this IS a dedicated-style test server, so
        // Minecraft.getInstance() is null): the multiplayer no-sync degradation — lookups
        // return empty rather than throwing.
        if (!GuideCraftingRecipes.INSTANCE.getRecipes(quarryStack).isEmpty()) {
            helper.fail("Lookups against an empty recipe store should return no factories");
            return;
        }
        helper.succeed();
    }
}
