/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The loader-neutral {@code c:} convention tags BuildCraft reads. NeoForge and Fabric publish the same ids, so these are
 * plain tag keys rather than the loader's own constants; a NeoForge-side test pins that each equals its
 * {@code Tags.Items} twin on every node.
 */
public final class CommonTags {
    private CommonTags() {}

    /** Items recipe viewers should not list ({@code c:hidden_from_recipe_viewers}). */
    public static final TagKey<Item> HIDDEN_FROM_RECIPE_VIEWERS_ITEM =
        TagKey.create(Registries.ITEM, Identifier.parse("c:hidden_from_recipe_viewers"));

    /** Wrenches from any mod ({@code c:tools/wrench}). */
    public static final TagKey<Item> TOOLS_WRENCH = TagKey.create(Registries.ITEM, Identifier.parse("c:tools/wrench"));
}
