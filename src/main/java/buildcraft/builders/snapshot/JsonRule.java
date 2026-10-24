/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.lib.misc.NBTUtilBC;

@SuppressWarnings("WeakerAccess")
public class JsonRule {
    public List<JsonSelector> selectors = null;
    public List<RequiredExtractor> requiredExtractors = null;
    public List<BlockPos> requiredBlockOffsets = null;
    public List<String> ignoredProperties = null;
    public List<BlockPos> updateBlockOffsets = null;
    public String placeBlock = null;
    public List<String> canBeReplacedWithBlocks = null;
    public CompoundTag replaceNbt = null;
    public boolean ignore = false; // blacklist for blocks
    public boolean capture = false; // whitelist for entities

    /**
     * Whether this rule charges for an entity itself ({@link RequiredExtractor#pricesEntityItself}): the one thing a
     * {@link #capture} whitelist does not do. An entity is built from its saved data with no {@code item_from_block}
     * fallback, so a rule without such an extractor lets the entity be built for nothing in either contents mode.
     */
    boolean pricesEntityItself() {
        return requiredExtractors != null && requiredExtractors.stream().anyMatch(RequiredExtractor::pricesEntityItself);
    }

    /** The ids this rule's selectors name, for a log line. */
    String describeSelectors() {
        return selectors == null ? "[]" : selectors.stream().map(JsonSelector::base).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * {@code nbt} with the {@link #replaceNbt} of every one of {@code rules} merged over it ({@link NBTUtilBC#merge}:
     * a JSON {@code null} removes the key). Each rule is merged over the data in turn, in the iteration order of
     * {@code rules} ({@link RulesLoader#getRules} returns them in load order), so when two set the same key the later
     * one wins. 1.12.2 instead merged the rules into one tag first, which dropped every rule's removals but the
     * first's. The directives are applied to the data about to be placed, never to a stored blueprint or its preview.
     * Returns {@code nbt} itself when no rule declares one, otherwise a new tag; never mutates {@code nbt}, and the
     * result shares nothing with a rule's own tag.
     */
    static CompoundTag applyReplaceNbt(CompoundTag nbt, Collection<JsonRule> rules) {
        CompoundTag result = nbt;
        for (JsonRule rule : rules) {
            if (rule.replaceNbt != null) {
                result = (CompoundTag) NBTUtilBC.merge(result, rule.replaceNbt);
            }
        }
        return result;
    }
}
