/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.item;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the route from the Goggles' HEAD view to their 3D worn model, in the form this node ships.
 *
 * <p>The worn look is no equipment asset: the head layer draws a head item that has none through its own item model in
 * the HEAD display context. 1.21.10+ reads the switch from {@code items/goggles.json}; 1.21.1 has no item definitions
 * and gets it from {@code DataDownport1211.generateOldItemModels1211}, which rewrites the classic
 * {@code models/item/goggles.json} into NeoForge's {@code neoforge:separate_transforms}. A missing or wrong rewrite
 * fails nothing at boot: the goggles just wear their flat icon on the head, or show no icon at all, on that one node.
 *
 * <p>On 1.21.1 the rewrite replaces the very icon model it falls back to, so the base must carry the icon itself: a
 * base that points at {@code item/goggles} would be a model that is its own parent. The wrapper has no parent of its
 * own, so its {@code gui_light} must be "front" or the inventory icon is lit like a block.
 */
public class GogglesHeadModelTester {

    private static final String WORN = "buildcraftunofficial:item/goggles_worn";
    private static final String ICON_MODEL = "buildcraftunofficial:item/goggles";
    private static final String ICON_TEXTURE = "buildcraftunofficial:item/goggles";

    //? if >=1.21.10 {
    private static final boolean HAS_ITEM_DEFINITIONS = true;
    //?} else {
    /*private static final boolean HAS_ITEM_DEFINITIONS = false;*/
    //?}

    private static JsonObject read(String path) {
        try (InputStream in = GogglesHeadModelTester.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(in, path + " is not on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    /** The model of the case whose {@code when} names the head view, alone or in a list; null when none does. */
    private static String headCaseModel(JsonArray cases) {
        for (JsonElement c : cases) {
            JsonObject entry = c.getAsJsonObject();
            JsonElement when = entry.get("when");
            boolean head = when.isJsonArray() ? when.getAsJsonArray().contains(new JsonPrimitive("head"))
                : "head".equals(when.getAsString());
            if (head) {
                return entry.getAsJsonObject("model").get("model").getAsString();
            }
        }
        return null;
    }

    @Test
    public void testHeadViewIsTheWornModelAndTheFallbackTheIcon() {
        if (HAS_ITEM_DEFINITIONS) {
            JsonObject model = read("/assets/buildcraftunofficial/items/goggles.json").getAsJsonObject("model");
            Assertions.assertEquals("minecraft:select", model.get("type").getAsString());
            Assertions.assertEquals("minecraft:display_context", model.get("property").getAsString());
            Assertions.assertEquals(WORN, headCaseModel(model.getAsJsonArray("cases")),
                "the head view must draw the worn model");
            Assertions.assertEquals(ICON_MODEL, model.getAsJsonObject("fallback").get("model").getAsString());
        } else {
            JsonObject classic = read("/assets/buildcraftunofficial/models/item/goggles.json");
            Assertions.assertTrue(classic.has("loader"),
                "1.21.1's goggles model is the plain icon: the downport did not turn the head switch into a per-view"
                    + " model, so the goggles wear their flat icon on the head");
            Assertions.assertEquals("neoforge:separate_transforms", classic.get("loader").getAsString());
            Assertions.assertEquals("front", classic.get("gui_light").getAsString(),
                "without front GUI light the inventory icon is lit like a block");
            JsonObject base = classic.getAsJsonObject("base");
            Assertions.assertNotEquals(ICON_MODEL, base.has("parent") ? base.get("parent").getAsString() : null,
                "the base points at the model the rewrite replaced, so it is its own parent and never loads");
            JsonObject textures = base.getAsJsonObject("textures");
            Assertions.assertEquals(ICON_TEXTURE, textures.get("layer0").getAsString());
            Assertions.assertEquals(ICON_TEXTURE, textures.has("particle") ? textures.get("particle").getAsString() : null,
                "the per-view model takes its particle from the base: without one it is the missing texture");
            JsonObject perspectives = classic.getAsJsonObject("perspectives");
            Assertions.assertTrue(perspectives.has("head"), "the head view is not switched: the goggles wear their icon");
            Assertions.assertEquals(WORN, perspectives.getAsJsonObject("head").get("parent").getAsString());
        }
        // The route is only half of it: a worn model with no cuboids draws nothing on the head.
        JsonObject worn = read("/assets/buildcraftunofficial/models/item/goggles_worn.json");
        Assertions.assertTrue(worn.has("elements") && !worn.getAsJsonArray("elements").isEmpty(),
            "the worn model has no elements, so the goggles are invisible on the head");
    }
}
