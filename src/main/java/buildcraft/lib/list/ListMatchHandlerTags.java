/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.lists.ListMatchHandler;

/** Replaces the 1.12.2 OreDictionary handler. Splits each item tag's path on {@code /} into
 * a (type, material) pair so a tag like {@code c:ingots/iron} contributes type=ingots and
 * material=iron. Single-segment tags like {@code minecraft:planks} contribute type only. */
public class ListMatchHandlerTags extends ListMatchHandler {

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (type != Type.TYPE && type != Type.MATERIAL) return false;
        // Any tag is a valid source for both modes — single-segment tags contribute the same
        // value to both type and material (vanilla single-segment tags like minecraft:planks
        // can't distinguish between "type of plank" and "material wood", so we treat them as
        // matching either; structured tags like c:ingots/iron still split as expected).
        return tagsOf(stack).findAny().isPresent();
    }

    @Override
    public boolean matches(Type type, @Nonnull ItemStack source, @Nonnull ItemStack target, boolean precise) {
        if (source.isEmpty() || target.isEmpty()) return false;
        if (type != Type.TYPE && type != Type.MATERIAL) return false;
        Set<String> sourceParts = collectParts(source, type);
        if (sourceParts.isEmpty()) return false;
        Set<String> targetParts = collectParts(target, type);
        return !Collections.disjoint(sourceParts, targetParts);
    }

    @Nullable
    @Override
    public NonNullList<ItemStack> getClientExamples(Type type, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (type != Type.TYPE && type != Type.MATERIAL) return null;

        Set<String> parts = collectParts(stack, type);
        if (parts.isEmpty()) return null;

        Set<Item> seen = new HashSet<>();
        NonNullList<ItemStack> out = NonNullList.create();

        BuiltInRegistries.ITEM.getTags().forEach((HolderSet.Named<Item> named) -> {
            TagKey<Item> tag = named.key();
            String part = partOf(tag, type);
            if (!parts.contains(part)) return;
            for (Holder<Item> h : named) {
                Item item = h.value();
                if (seen.add(item)) {
                    out.add(new ItemStack(item));
                }
            }
        });
        return out;
    }

    @Nonnull
    @Override
    public List<String> describeMatch(Type type, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        if (type != Type.TYPE && type != Type.MATERIAL) return List.of();
        // Use LinkedHashSet to keep insertion order stable across frames (so the ledger doesn't
        // shuffle every time it re-renders).
        Set<String> out = new LinkedHashSet<>();
        tagsOf(stack).forEach(tag -> {
            String part = partOf(tag, type);
            out.add("#" + tag.location() + " (" + part + ")");
        });
        return new ArrayList<>(out);
    }

    private static java.util.stream.Stream<TagKey<Item>> tagsOf(ItemStack stack) {
        //? if >=26.1 {
        return stack.typeHolder().tags();
        //?} else {
        /*return stack.getItemHolder().tags();*/
        //?}
    }

    /** Type-parts or material-parts of every tag on {@code stack}. For MATERIAL, single-segment
     * tags (no slash) are a fallback only — {@code c:ingots} is a type umbrella, not a material,
     * and counting it would make every ingot share material "ingots". A stack with any slashed
     * tag ({@code c:ingots/iron}) is pinned by those alone; single-segment tags feed MATERIAL
     * only when no slashed tag exists (e.g. planks, sole tag {@code minecraft:planks}). */
    private static Set<String> collectParts(ItemStack stack, Type type) {
        List<TagKey<Item>> tags = tagsOf(stack).toList();
        Set<String> parts = new HashSet<>();
        if (type == Type.MATERIAL) {
            boolean hasSlashed = tags.stream().anyMatch(t -> t.location().getPath().indexOf('/') >= 0);
            for (TagKey<Item> tag : tags) {
                boolean slashed = tag.location().getPath().indexOf('/') >= 0;
                if (hasSlashed && !slashed) {
                    continue;
                }
                parts.add(partOf(tag, type));
            }
        } else {
            for (TagKey<Item> tag : tags) {
                parts.add(partOf(tag, type));
            }
        }
        return parts;
    }

    /** Extracts the type-part or material-part of a tag's path. For structured tags like
     * {@code c:ingots/iron}, TYPE returns {@code "ingots"} and MATERIAL returns {@code "iron"}.
     * For single-segment tags like {@code minecraft:planks}, both return the full path
     * ({@code "planks"}) — vanilla single-segment tags don't distinguish type from material,
     * so we treat them as contributing to both modes. */
    @Nonnull
    private static String partOf(TagKey<Item> tag, Type type) {
        String path = tag.location().getPath();
        int slash = path.indexOf('/');
        if (slash < 0) return path;
        if (type == Type.TYPE) {
            return path.substring(0, slash);
        }
        // MATERIAL
        if (slash == path.length() - 1) return path; // trailing slash — degenerate, treat as full path
        return path.substring(slash + 1);
    }
}
