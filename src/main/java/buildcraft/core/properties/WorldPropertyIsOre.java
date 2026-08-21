/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;

/** The "ore" world property: is the block at the position an ore a pickaxe of at most {@code harvestLevel}
 *  can harvest? Ported from 7.1.x {@code WorldPropertyIsOre} (ore-dict {@code ore*} names + the block's
 *  pickaxe tier); the modern equivalent of the ore umbrella is the eight {@code minecraft:*_ores} block
 *  tags, and the pickaxe tier is the smallest pickaxe tier whose item reports
 *  {@code isCorrectToolForDrops} for the state (a state no pickaxe tier can harvest never matches — the
 *  modern equivalent of 7.1.x's "not harvestable with a pickaxe" exclusion). Registered from
 *  {@code BCCore.preInit} under the keys {@code "ore@hardness=0"} .. {@code "ore@hardness=3"} (one
 *  instance per tier, as 7.1.x did); the miner board queries
 *  {@code "ore@hardness=" + min(3, heldPickaxeTier)} through {@link #matches(BlockState)}. */
public class WorldPropertyIsOre implements IWorldProperty {

    /** The pickaxe tier this instance accepts (0 = wooden .. 3 = netherite-capped; 4 is clamped by the
     *  miner board, so four registered instances cover every held tool). */
    private final int harvestLevel;

    // The five vanilla pickaxes in ascending tier. A state's ore tier is the FIRST of these whose item
    // reports isCorrectToolForDrops for it — the probe-block ladder, which 26.x's ToolMaterial no longer
    // exposes as an integer tier.
    private static final Item[] PICKAXE_TIERS = {
        Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE,
        Items.NETHERITE_PICKAXE
    };

    // The eight vanilla ore tags — the modern `ore*` umbrella. Five of them lost their BlockTags constant
    // on the 26.1 line (they exist only as BlockItemTags there), so that line builds the missing keys
    // directly — the ID type is Identifier (the ResourceLocation rename happened earlier, at 1.21.11).
    private static final TagKey<Block> GOLD_ORES = BlockTags.GOLD_ORES;
    private static final TagKey<Block> IRON_ORES = BlockTags.IRON_ORES;
    private static final TagKey<Block> COPPER_ORES = BlockTags.COPPER_ORES;
//? if >=26.1 {
    private static final TagKey<Block> COAL_ORES = BlockTags.create(
            net.minecraft.resources.Identifier.withDefaultNamespace("coal_ores"));
    private static final TagKey<Block> DIAMOND_ORES = BlockTags.create(
            net.minecraft.resources.Identifier.withDefaultNamespace("diamond_ores"));
    private static final TagKey<Block> EMERALD_ORES = BlockTags.create(
            net.minecraft.resources.Identifier.withDefaultNamespace("emerald_ores"));
    private static final TagKey<Block> LAPIS_ORES = BlockTags.create(
            net.minecraft.resources.Identifier.withDefaultNamespace("lapis_ores"));
    private static final TagKey<Block> REDSTONE_ORES = BlockTags.create(
            net.minecraft.resources.Identifier.withDefaultNamespace("redstone_ores"));
//?} else {
    /*private static final TagKey<Block> COAL_ORES = BlockTags.COAL_ORES;
    private static final TagKey<Block> DIAMOND_ORES = BlockTags.DIAMOND_ORES;
    private static final TagKey<Block> EMERALD_ORES = BlockTags.EMERALD_ORES;
    private static final TagKey<Block> LAPIS_ORES = BlockTags.LAPIS_ORES;
    private static final TagKey<Block> REDSTONE_ORES = BlockTags.REDSTONE_ORES;*/
//?}

    private static final List<TagKey<Block>> ORE_TAGS = List.of(
            GOLD_ORES, IRON_ORES, COPPER_ORES, COAL_ORES, DIAMOND_ORES, EMERALD_ORES, LAPIS_ORES, REDSTONE_ORES);

    public WorldPropertyIsOre(int harvestLevel) {
        this.harvestLevel = harvestLevel;
    }

    public int getHarvestLevel() {
        return harvestLevel;
    }

    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: tags + the pickaxe tier ladder both need no level, so {@link #get} delegates
     *  here and the JUnit predicate sweeps can drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        boolean isOre = false;
        for (TagKey<Block> oreTag : ORE_TAGS) {
            if (state.is(oreTag)) {
                isOre = true;
                break;
            }
        }
        if (!isOre) {
            return false;
        }
        for (int tier = 0; tier < PICKAXE_TIERS.length; tier++) {
            if (new ItemStack(PICKAXE_TIERS[tier]).isCorrectToolForDrops(state)) {
                return tier <= harvestLevel;
            }
        }
        // No pickaxe tier can harvest it (a modded unbreakable ore, say) — it never matches.
        return false;
    }

    @Override
    public void clear() {
    }
}
