/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.entry.PageEntry;
import buildcraft.lib.client.guide.entry.PageEntryExternal;

/** Pins the {@code system} entry tag that drives the default "Sort By System" contents order:
 *  the system level comes first, an entry without a system just loses that level (so it files
 *  at the root instead of under an invented "Unknown" chapter), and the type order is
 *  untouched by the new field. */
class JsonTypeTagsSystemOrderTest {

    private static final TypeOrder SYSTEM_ORDER = new TypeOrder("k.system", ETypeTag.SYSTEM, ETypeTag.SUB_TYPE);
    private static final TypeOrder TYPE_ORDER = new TypeOrder("k.type", ETypeTag.TYPE, ETypeTag.SUB_TYPE);

    @Test
    void systemPreTextIsTheChapterSystemPrefix() {
        assertEquals("buildcraft.guide.chapter.system.", ETypeTag.SYSTEM.preText);
    }

    @Test
    void systemOrder_putsSystemBeforeSubtype() {
        JsonTypeTags tags = new JsonTypeTags("buildcraftunofficial", "block", "engine", "power");
        assertArrayEquals(
            new String[] { "buildcraft.guide.chapter.system.power", "buildcraft.guide.chapter.subtype.engine" },
            tags.getOrdered(SYSTEM_ORDER));
    }

    @Test
    void typeOrder_isUnchangedBySystem() {
        JsonTypeTags tags = new JsonTypeTags("buildcraftunofficial", "block", "engine", "power");
        assertArrayEquals(
            new String[] { "buildcraft.guide.chapter.type.block", "buildcraft.guide.chapter.subtype.engine" },
            tags.getOrdered(TYPE_ORDER));
    }

    @Test
    void systemOrder_withoutSystem_dropsThatLevel() {
        JsonTypeTags tags = new JsonTypeTags("buildcraftunofficial", "block", "engine");
        assertNull(tags.system);
        assertArrayEquals(
            new String[] { "buildcraft.guide.chapter.subtype.engine" },
            tags.getOrdered(SYSTEM_ORDER));
    }

    @Test
    void inheritMissingTags_inheritsSystem_butNeverInventsOne() {
        JsonTypeTags parent = new JsonTypeTags("buildcraftunofficial", "block", "engine", "power");
        JsonTypeTags child = new JsonTypeTags("buildcraftunofficial", "block", "engine");
        assertEquals("power", child.inheritMissingTags(parent).system);

        JsonTypeTags bareParent = new JsonTypeTags("buildcraftunofficial", "block", "engine");
        assertNull(child.inheritMissingTags(bareParent).system,
            "no system on either side must stay null, not \"unknown\"");
    }

    @Test
    void pageEntryJson_readsTagSystem() {
        JsonObject json = new JsonObject();
        json.addProperty("title_raw", "Engines");
        json.addProperty("tag_type", "block");
        json.addProperty("tag_subtype", "engine");
        json.addProperty("tag_system", "power");
        PageEntry<String> entry = new PageEntry<>(PageEntryExternal.INSTANCE,
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "block/engine"), json, "Engines");

        assertEquals("power", entry.typeTags.system);
        assertArrayEquals(
            new String[] { "buildcraft.guide.chapter.system.power", "buildcraft.guide.chapter.subtype.engine" },
            entry.typeTags.getOrdered(SYSTEM_ORDER));
    }

    @Test
    void pageEntryJson_withoutTagSystem_hasNoSystem() {
        JsonObject json = new JsonObject();
        json.addProperty("title_raw", "Engines");
        json.addProperty("tag_type", "block");
        json.addProperty("tag_subtype", "engine");
        PageEntry<String> entry = new PageEntry<>(PageEntryExternal.INSTANCE,
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "block/engine"), json, "Engines");

        assertNull(entry.typeTags.system);
    }
}
