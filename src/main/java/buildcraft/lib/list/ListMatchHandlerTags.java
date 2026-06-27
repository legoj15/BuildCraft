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

/** Replaces the 1.12.2 OreDictionary handler. Splits a {@code c:} convention tag's path on
 * {@code /} into a (type, material) pair so a tag like {@code c:ingots/iron} contributes
 * type=ingots and material=iron. A slash in any OTHER namespace is not a family/material split
 * (e.g. vanilla {@code minecraft:sulfur_cube_archetype/slow_bouncy} is a gameplay tag, not a
 * material family); such tags are taken whole. Single-segment tags like {@code minecraft:planks}
 * act as a material fallback when no {@code c:} family is present. */
public class ListMatchHandlerTags extends ListMatchHandler {

    /** Vanilla grouping tags that lump otherwise-unrelated items together for reasons that have
     * nothing to do with material/type equivalence. {@code minecraft:completes_find_tree_tutorial}
     * is the offender: it contains logs, leaves AND wart blocks (it only exists to drive the
     * "punch a tree" tutorial hint), so treating it as a shared part makes a log filter match
     * leaves. The handler drops these tags before deriving any parts, so they never feed matching,
     * the auto-filled examples, or the match-info ledger — in any mode. */
    private static final Set<String> IGNORED_TAGS = Set.of(
            "minecraft:completes_find_tree_tutorial"
    );

    private static boolean isIgnored(TagKey<Item> tag) {
        return IGNORED_TAGS.contains(tag.location().toString());
    }

    /** Trailing segments of a {@code c:<family>/<variant>} tag that name a *form*, not a material.
     * The convention tags share these leaves across unrelated families — {@code c:bricks/normal},
     * {@code c:cobblestones/normal}, {@code c:obsidians/normal} and {@code c:pumpkins/normal} all
     * end in {@code normal} — so the MATERIAL split (which keys off the after-slash segment) used
     * to make a baked brick, cobblestone and a pumpkin all "equivalent" under material "normal".
     * These leaves are dropped from MATERIAL parts; the family is still captured by TYPE mode via
     * the {@code <family>} segment before the slash. Genuine materials ({@code c:ingots/iron}) are
     * untouched — only this small, unambiguous set of form words is filtered. */
    private static final Set<String> MATERIAL_VARIANT_QUALIFIERS = Set.of("normal");

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (type != Type.TYPE && type != Type.MATERIAL) return false;
        // Valid iff the stack actually yields parts for this mode. For TYPE that's any tag; for
        // MATERIAL it can be empty even when tags exist — e.g. cobblestone's only structured tag
        // is c:cobblestones/normal, whose "normal" leaf is a form qualifier, not a material. An
        // item with no real material then falls through to the List's identity match (matches
        // itself) rather than being wrongly claimed as a material exemplar.
        return !collectParts(stack, type).isEmpty();
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

        //? if >=1.21.10 {
        BuiltInRegistries.ITEM.getTags().forEach((HolderSet.Named<Item> named) -> {
        //?} else {
        /*BuiltInRegistries.ITEM.getTags().forEach((com.mojang.datafixers.util.Pair<TagKey<Item>, HolderSet.Named<Item>> pair) -> {
            HolderSet.Named<Item> named = pair.getSecond();*/
        //?}
            TagKey<Item> tag = named.key();
            if (isIgnored(tag)) return;
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
            if (type == Type.MATERIAL && MATERIAL_VARIANT_QUALIFIERS.contains(part)) {
                // Form qualifier, not a material — it doesn't drive Accept Equivalents matching,
                // so listing it in the ledger would misrepresent what the filter actually selects.
                return;
            }
            out.add("#" + tag.location() + " (" + part + ")");
        });
        return new ArrayList<>(out);
    }

    private static java.util.stream.Stream<TagKey<Item>> tagsOf(ItemStack stack) {
        //? if >=26.1 {
        return stack.typeHolder().tags().filter(t -> !isIgnored(t));
        //?} else {
        /*return stack.getItemHolder().tags().filter(t -> !isIgnored(t));*/
        //?}
    }

    /** A NeoForge convention structured tag: {@code c:<family>/<variant>} (namespace {@code c} with
     * a slash). Only these carry the family/variant semantic the MATERIAL/TYPE split relies on;
     * vanilla {@code minecraft:.../...} slashed tags (e.g. {@code sulfur_cube_archetype/slow_bouncy}
     * on MC 26.2) are gameplay groupings, not material families. */
    private static boolean isConventionStructured(TagKey<Item> tag) {
        return "c".equals(tag.location().getNamespace()) && tag.location().getPath().indexOf('/') >= 0;
    }

    /** Type-parts or material-parts of every tag on {@code stack}. For MATERIAL, single-segment
     * tags (no slash) are a fallback only — {@code c:ingots} is a type umbrella, not a material,
     * and counting it would make every ingot share material "ingots". A stack with any {@code c:}
     * family tag ({@code c:ingots/iron}) is pinned by those alone; single-segment tags feed MATERIAL
     * only when no such family exists (e.g. planks, sole tag {@code minecraft:planks}). A non-{@code c:}
     * slashed tag is neither a family nor a fallback and contributes nothing. */
    private static Set<String> collectParts(ItemStack stack, Type type) {
        List<TagKey<Item>> tags = tagsOf(stack).toList();
        Set<String> parts = new HashSet<>();
        if (type == Type.MATERIAL) {
            boolean hasStructured = tags.stream().anyMatch(ListMatchHandlerTags::isConventionStructured);
            for (TagKey<Item> tag : tags) {
                boolean structured = isConventionStructured(tag);
                boolean singleSegment = tag.location().getPath().indexOf('/') < 0;
                // A non-c: slashed tag (e.g. vanilla minecraft:sulfur_cube_archetype/slow_bouncy on
                // MC 26.2) is a gameplay grouping — not a material family and not a single-segment
                // fallback — so it never names a material. Drop it entirely.
                if (!structured && !singleSegment) {
                    continue;
                }
                // When the stack has a real convention family (c:.../...), single-segment umbrella
                // tags (c:ingots, minecraft:planks) are too broad to be the material — the family pins it.
                if (hasStructured && !structured) {
                    continue;
                }
                String part = partOf(tag, type);
                if (MATERIAL_VARIANT_QUALIFIERS.contains(part)) {
                    // "normal" et al. are form qualifiers shared across unrelated families
                    // (c:bricks/normal, c:cobblestones/normal, ...) — not materials. Skip them.
                    continue;
                }
                parts.add(part);
            }
        } else {
            for (TagKey<Item> tag : tags) {
                parts.add(partOf(tag, type));
            }
        }
        return parts;
    }

    /** Extracts the type-part or material-part of a tag's path. For {@code c:} convention tags like
     * {@code c:ingots/iron}, TYPE returns {@code "ingots"} and MATERIAL returns {@code "iron"}.
     * For single-segment tags like {@code minecraft:planks} — and for slashed tags OUTSIDE the
     * {@code c:} namespace (vanilla gameplay tags like {@code minecraft:sulfur_cube_archetype/slow_bouncy},
     * whose slash is not a family/material boundary) — both modes return the full path, so the
     * non-material "variant" never leaks in as a bogus material or type. */
    @Nonnull
    private static String partOf(TagKey<Item> tag, Type type) {
        String path = tag.location().getPath();
        int slash = path.indexOf('/');
        // Only the NeoForge `c:` convention uses path slashes as a family/variant boundary.
        if (slash < 0 || !"c".equals(tag.location().getNamespace())) {
            return path;
        }
        if (type == Type.TYPE) {
            return path.substring(0, slash);
        }
        // MATERIAL
        if (slash == path.length() - 1) return path; // trailing slash — degenerate, treat as full path
        return path.substring(slash + 1);
    }
}
