/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.VanillaSetupBaseTester;

/**
 * What {@link RulesLoader} promises its callers: which entity rules it warns about at load time, and that the cache
 * behind {@link RulesLoader#getRules(BlockState, CompoundTag)} cannot be corrupted by a caller editing the tag it
 * looked up with.
 */
public class RulesLoaderTester extends VanillaSetupBaseTester {

    @Test
    public void captureRulesThatPriceNothingForTheEntityItselfAreTheOnesFlagged() {
        List<JsonRule> rules = RulesLoader.parseRules(new StringReader("""
            [
              { "selectors": ["mymod:bare"], "capture": true },
              { "selectors": ["mymod:contents_only"], "capture": true,
                "requiredExtractors": [{ "type": "items_list", "path": ["Items"] }] },
              { "selectors": ["mymod:priced"], "capture": true,
                "requiredExtractors": [{ "type": "constant", "items": [{ "item": "minecraft:stick" }] }] },
              { "selectors": ["mymod:not_captured"],
                "requiredExtractors": [{ "type": "items_list", "path": ["Items"] }] }
            ]
            """));

        // Only a capture rule is judged (a rule that does not whitelist anything has nothing to refuse), and a
        // constant extractor is what prices the entity itself, whatever contents extractors come with it.
        Assertions.assertEquals(List.of("mymod:bare", "mymod:contents_only"),
            RulesLoader.captureRulesWithoutPrice(rules).stream()
                .flatMap(rule -> rule.selectors.stream())
                .map(JsonSelector::base)
                .toList());
    }

    @Test
    public void aCallerEditingItsLookupTagCannotStrandACacheEntry() {
        // The cache keeps the tag it was asked about, and a tag hashes by content. Keyed by the caller's own tag, an
        // entry would sit under a hash its edited key no longer has, and the same lookup would never hit it again.
        BlockState chest = Blocks.CHEST.defaultBlockState();
        CompoundTag tag = new CompoundTag();
        tag.putString("rulesLoaderTesterProbe", "before");
        Set<JsonRule> first = RulesLoader.getRules(chest, tag);

        tag.putString("rulesLoaderTesterProbe", "after");

        CompoundTag same = new CompoundTag();
        same.putString("rulesLoaderTesterProbe", "before");
        Assertions.assertSame(first, RulesLoader.getRules(chest, same),
            "a lookup with the tag's original content must still hit the entry the first lookup made");
    }
}
