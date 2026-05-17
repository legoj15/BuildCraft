package buildcraft.energy;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Game tests for the {@code fine_riches} and {@code sticky_dipping} advancements.
 *
 * <h3>What's covered here and why</h3>
 * <ul>
 *   <li><b>Advancement JSON parses and loads under the expected ID</b> — catches
 *       JSON syntax mistakes, wrong predicate-wrapping shapes (the
 *       {@code minecraft:location} criterion has a notoriously fiddly
 *       {@code player → location_check → predicate} structure), and ID
 *       drift between code constants and the JSON file path.</li>
 *   <li><b>{@code #buildcraftunofficial:oil_fluids} block tag completeness</b> —
 *       exactly the 12 oil source blocks the {@code sticky_dipping} predicate
 *       relies on. Catches typos in the tag JSON and silent regressions when
 *       new oil variants are added in Java without updating the tag.</li>
 *   <li><b>Tag exclusivity</b> — explicit negative assertions that
 *       {@code oil_residue}, every {@code fuel_*}, and the vanilla
 *       water/lava blocks are NOT in the tag (per design spec).</li>
 *   <li><b>Predicate negative case for fine_riches</b> — in the (flat or
 *       void-biome) game-test world, {@code wouldGenerateOilForOriginChunk}
 *       must return {@code false} so the advancement can't false-fire.</li>
 * </ul>
 *
 * <h3>What is NOT covered and why</h3>
 * Directly observing advancement award to a player is out of scope:
 * {@link GameTestHelper#makeMockPlayer} returns an anonymous {@code Player}
 * (NOT a {@code ServerPlayer}), and
 * {@link buildcraft.lib.misc.AdvancementUtil#unlockAdvancement(net.minecraft.world.entity.player.Player,
 * Identifier)} short-circuits on a non-ServerPlayer. End-to-end advancement
 * firing must be verified manually in a live client (load a world, walk to a
 * desert, expect the toast; walk into oil, expect the toast). The covered
 * tests check every link in the chain except the final player-tracker write.
 */
public class AdvancementTester {

    private static final Identifier FINE_RICHES_ID = Identifier.parse("buildcraftunofficial:fine_riches");
    private static final Identifier STICKY_DIPPING_ID = Identifier.parse("buildcraftunofficial:sticky_dipping");
    private static final Identifier PRECISION_CRAFTING_ID = Identifier.parse("buildcraftunofficial:precision_crafting");
    private static final TagKey<Block> OIL_FLUIDS_TAG = TagKey.create(
            Registries.BLOCK, Identifier.parse("buildcraftunofficial:oil_fluids"));

    // ─── Advancement JSON loaded under expected ID ───────────────────────

    public static void testFineRichesAdvancementLoaded(GameTestHelper helper) {
        assertAdvancementLoaded(helper, FINE_RICHES_ID);
        helper.succeed();
    }

    public static void testStickyDippingAdvancementLoaded(GameTestHelper helper) {
        AdvancementHolder holder = assertAdvancementLoaded(helper, STICKY_DIPPING_ID);
        // For sticky_dipping specifically, also verify the criterion isn't `minecraft:impossible`
        // — that would mean we accidentally reverted to the broken stub state.
        boolean hasNonImpossibleCriterion = holder.value().criteria().values().stream()
                .anyMatch(c -> !isImpossibleTrigger(c));
        if (!hasNonImpossibleCriterion) {
            throw new AssertionError("sticky_dipping has only impossible-trigger criteria; "
                    + "expected at least one real trigger (likely minecraft:location). "
                    + "This suggests the trigger fix was reverted.");
        }
        helper.succeed();
    }

    public static void testPrecisionCraftingAdvancementLoaded(GameTestHelper helper) {
        AdvancementHolder holder = assertAdvancementLoaded(helper, PRECISION_CRAFTING_ID);
        // Precision crafting is code-driven (TileAssemblyTable.serverTick calls
        // AdvancementUtil.unlockAdvancement which writes to the default "code_trigger"
        // criterion name). The JSON must therefore keep a minecraft:impossible-shaped
        // criterion named code_trigger; if the JSON criterion type or name changes without
        // updating the handler, the trigger silently stops working.
        boolean hasImpossibleTrigger = holder.value().criteria().values().stream()
                .anyMatch(AdvancementTester::isImpossibleTrigger);
        if (!hasImpossibleTrigger) {
            throw new AssertionError("precision_crafting no longer has a minecraft:impossible criterion. "
                    + "TileAssemblyTable awards this via AdvancementUtil.unlockAdvancement, which writes "
                    + "to the default 'code_trigger' criterion. Either restore the JSON criterion or "
                    + "update the handler call to match the new criterion name/type.");
        }
        boolean hasCodeTriggerCriterion = holder.value().criteria().containsKey("code_trigger");
        if (!hasCodeTriggerCriterion) {
            throw new AssertionError("precision_crafting's impossible criterion is not named 'code_trigger' "
                    + "(found: " + holder.value().criteria().keySet() + "). "
                    + "AdvancementUtil.unlockAdvancement's default criterionName is 'code_trigger', so "
                    + "the handler call will silently fail to grant the advancement.");
        }
        helper.succeed();
    }

    /** Resolves the trigger's registry ID and compares to {@code minecraft:impossible}.
     * Going through the registry rather than class-name string-matching means renames
     * of the {@code ImpossibleTrigger} class don't silently make the check pass. */
    private static boolean isImpossibleTrigger(net.minecraft.advancements.Criterion<?> c) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.TRIGGER_TYPES.getKey(c.trigger());
        return id != null && id.equals(Identifier.withDefaultNamespace("impossible"));
    }

    // ─── Block tag content ───────────────────────────────────────────────

    public static void testOilFluidsTagContainsAllOilSourceBlocks(GameTestHelper helper) {
        // Compute the expected set from the live fluid registry — base name must be
        // "oil", "oil_heavy", "oil_dense", or "oil_distilled" (3 heat tiers each → 12).
        // Driving this off BCEnergyFluids.ALL means the test stays correct if heat tier
        // numbering changes but flags any future addition of a new oil base variant
        // (e.g. "oil_sour") that should be in the tag.
        Set<String> oilBaseNames = Set.of("oil", "oil_heavy", "oil_dense", "oil_distilled");
        Set<Identifier> expected = BCEnergyFluids.ALL.stream()
                .filter(e -> oilBaseNames.contains(e.baseName()))
                .map(e -> BuiltInRegistries.BLOCK.getKey(e.block().get()))
                .collect(Collectors.toSet());

        if (expected.size() != 12) {
            throw new AssertionError("Expected exactly 12 oil source blocks (4 base × 3 heat), "
                    + "found " + expected.size() + ": " + expected
                    + ". BCEnergyFluids.ALL may have changed shape — update the tag or the test accordingly.");
        }

        Set<Identifier> actual = getTagContents(helper, OIL_FLUIDS_TAG);

        Set<Identifier> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        Set<Identifier> extra = new HashSet<>(actual);
        extra.removeAll(expected);

        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new AssertionError("oil_fluids tag mismatch."
                    + (missing.isEmpty() ? "" : "\n  Missing: " + missing)
                    + (extra.isEmpty() ? "" : "\n  Unexpected extras: " + extra));
        }
        helper.succeed();
    }

    public static void testOilFluidsTagExcludesNonOilFluids(GameTestHelper helper) {
        // oil_residue: refinery byproduct, not "oil" in the player sense
        assertNotInTag(helper, getFirstHeatBlockByBaseName("oil_residue"),
                "oil_residue (refinery byproduct, not an oil deposit)");

        // Every fuel_* variant (all heats): refinery outputs, not raw oils
        for (String fuelBase : new String[] {
                "fuel_dense", "fuel_mixed_heavy", "fuel_light", "fuel_mixed_light", "fuel_gaseous"
        }) {
            for (int heat = 0; heat < 3; heat++) {
                assertNotInTag(helper, getBlockByBaseNameAndHeat(fuelBase, heat),
                        fuelBase + " heat " + heat + " (refinery output)");
            }
        }

        // Vanilla fluids: explicit defence against someone overbroadening the tag
        assertNotInTag(helper, Blocks.WATER, "minecraft:water");
        assertNotInTag(helper, Blocks.LAVA, "minecraft:lava");

        helper.succeed();
    }

    // ─── fine_riches predicate sanity ────────────────────────────────────

    public static void testWouldGenerateOilReturnsFalseInTestEnvironment(GameTestHelper helper) {
        // Game tests run in either a flat or void-biome arena; either way
        // wouldGenerateOilForOriginChunkInOilBiome (the predicate the handler
        // calls) should refuse to award the advancement — flat-world short-
        // circuits in canGenerateOilIn, void biome fails the oil-design biome
        // tier check. Sample several chunks around the test arena to catch any
        // seed-dependent positive rolls that would slip through if a future
        // change weakened the gates.
        int chunkX = helper.absolutePos(new net.minecraft.core.BlockPos(0, 64, 0)).getX() >> 4;
        int chunkZ = helper.absolutePos(new net.minecraft.core.BlockPos(0, 64, 0)).getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (buildcraft.energy.generation.OilGenerator
                        .wouldGenerateOilForOriginChunkInOilBiome(helper.getLevel(), chunkX + dx, chunkZ + dz)) {
                    throw new AssertionError("wouldGenerateOilForOriginChunkInOilBiome returned true for chunk ("
                            + (chunkX + dx) + ", " + (chunkZ + dz) + ") in the test arena. "
                            + "The level-veto / biome-tier / per-chunk-roll gates may be "
                            + "broken — fine_riches could false-fire in worlds where no oil exists.");
                }
            }
        }
        helper.succeed();
    }

    /**
     * Independently verify the biome-tier gate that prevents fine_riches from
     * false-firing in chunks where the per-chunk RNG rolled oil but the biome
     * isn't one designed for oil. Two distinct false-positive classes get
     * caught here:
     * <ul>
     *   <li>Non-oil biomes (plains/forest/taiga/etc.) — the original bug
     *       observed in a forest spawn at render distance 32. getStructures
     *       rolls LARGE/MEDIUM at base 1.0× in any biome, so without a tier
     *       gate the advancement fires anywhere.</li>
     *   <li>Light-tier biomes (shallow ocean variants) — observed when a
     *       player got the advancement crossing a 2.5-chunk patch of
     *       cold_ocean nestled inside taiga. MEDIUM rolls in shallow water
     *       produce a 4-block oil pool at y=62 (inside the water column) plus
     *       a fully-buried sphere+spout — visually undetectable. The advancement
     *       fired but the player saw no oil. Solution: light tier is no longer
     *       an oil-design biome for the advancement's purposes (it still rolls
     *       oil in worldgen, just doesn't grant fine_riches).</li>
     * </ul>
     * Tests fixed biome IDs rather than going through the chunk path, since the
     * game-test arena biome is fixed and can't exercise the per-chunk roll
     * differentiation directly. Configurable lists are checked against their
     * defaults, so a default-loadout regression in BCEnergyConfig also trips.
     */
    public static void testFineRichesBiomeTierGate(GameTestHelper helper) {
        // Positive cases — defaults from BCEnergyConfig.richSurfaceDepositBiomes.
        // If any of these stop counting as oil biomes, the advancement becomes
        // unreachable in those biomes — catches "I removed minecraft:desert
        // from the rich list" regressions.
        for (String richBiome : new String[] {
                "minecraft:desert", "minecraft:badlands", "minecraft:deep_ocean"
        }) {
            if (!buildcraft.energy.generation.OilGenerator
                    .isOilDesignBiome(Identifier.parse(richBiome))) {
                throw new AssertionError(richBiome + " must be recognised as an oil-design biome "
                        + "(default rich-tier member). fine_riches will never fire there if this fails.");
            }
        }

        // Negative cases — bug class 1: vanilla non-oil biomes a real player spawns in.
        for (String nonOilBiome : new String[] {
                "minecraft:plains", "minecraft:forest", "minecraft:taiga",
                "minecraft:snowy_taiga", "minecraft:snowy_plains"
        }) {
            if (buildcraft.energy.generation.OilGenerator
                    .isOilDesignBiome(Identifier.parse(nonOilBiome))) {
                throw new AssertionError(nonOilBiome + " was reported as an oil-design biome. "
                        + "fine_riches will false-fire there given enough chunks loaded — "
                        + "this is the original biome-tier-gate bug. Check whether something "
                        + "was added to richSurfaceDepositBiomes / forceExcessiveOilBiomes "
                        + "by mistake.");
            }
        }

        // Negative cases — bug class 2: light-tier shallow oceans deliberately
        // excluded because MEDIUM rolls there are invisible to the player.
        // If these come back true, light tier got re-added to isOilDesignBiome
        // and the small-shallow-ocean false-fire is back.
        for (String lightTierBiome : new String[] {
                "minecraft:ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
                "minecraft:cold_ocean", "minecraft:frozen_ocean"
        }) {
            if (buildcraft.energy.generation.OilGenerator
                    .isOilDesignBiome(Identifier.parse(lightTierBiome))) {
                throw new AssertionError(lightTierBiome + " was reported as an oil-design biome. "
                        + "Light-tier biomes are deliberately excluded — MEDIUM rolls there are "
                        + "invisible (surface tendril sits inside the water column, spout doesn't "
                        + "reach the seafloor). If isOilDesignBiome now also queries "
                        + "surfaceDepositBiomes, the small-shallow-ocean false-fire is back.");
            }
        }
        helper.succeed();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private static AdvancementHolder assertAdvancementLoaded(GameTestHelper helper, Identifier id) {
        MinecraftServer server = helper.getLevel().getServer();
        if (server == null) {
            throw new IllegalStateException("Test helper level has no server — can't look up advancements.");
        }
        ServerAdvancementManager mgr = server.getAdvancements();
        AdvancementHolder holder = mgr.get(id);
        if (holder == null) {
            throw new AssertionError("Advancement " + id + " did not load. "
                    + "Either the JSON is missing, the path is wrong, or the codec rejected the JSON shape. "
                    + "Check the server log for an `Couldn't load advancement` line.");
        }
        return holder;
    }

    private static Set<Identifier> getTagContents(GameTestHelper helper, TagKey<Block> tagKey) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK)
                .get(tagKey)
                .orElseThrow(() -> new AssertionError(
                        "Tag " + tagKey.location() + " not loaded — JSON missing or path wrong."))
                .stream()
                .map(Holder::unwrapKey)
                .map(opt -> opt.orElseThrow().identifier())
                .collect(Collectors.toSet());
    }

    private static void assertNotInTag(GameTestHelper helper, Block block, String description) {
        if (block.builtInRegistryHolder().is(OIL_FLUIDS_TAG)) {
            throw new AssertionError(description + " (id="
                    + BuiltInRegistries.BLOCK.getKey(block)
                    + ") is in oil_fluids tag, but should not be.");
        }
    }

    private static Block getFirstHeatBlockByBaseName(String baseName) {
        return getBlockByBaseNameAndHeat(baseName, 0);
    }

    private static Block getBlockByBaseNameAndHeat(String baseName, int heat) {
        return BCEnergyFluids.ALL.stream()
                .filter(e -> e.baseName().equals(baseName) && e.heat() == heat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not find fluid entry for baseName=" + baseName + " heat=" + heat))
                .block().get();
    }
}
