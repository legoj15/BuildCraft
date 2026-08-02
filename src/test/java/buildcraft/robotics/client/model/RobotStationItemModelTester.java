/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the docking-station ITEM model, which nothing else in the build validates — a wrong number in
 * it parses, bakes and renders, just wrong.
 *
 * <p>Two traps this guards. First, the item model is vanilla-dialect while the in-world pedestal is
 * built in Java from {@code ModelUtil.mapBoxToUvs}; the two conventions disagree on {@code up},
 * {@code north} and {@code east}, so a uv array copied from one world into the other silently
 * mirrors those faces. Second, the file is hand-authored and {@code generateOldItemModels1211} only
 * synthesises a classic model when none exists — regenerating or deleting this file would leave
 * 1.21.1 with a flat stub while every other node still looked right.
 *
 * <p>1.7.10 drew the whole item pedestal with the single {@code pipeRobotStation} icon (this port's
 * {@code robot_station_available}), so every face resolving to that one texture is the parity
 * condition, not an oversight.
 */
public class RobotStationItemModelTester {

    private static final String PATH = "/assets/buildcraftunofficial/models/item/robot_station.json";
    private static final String SPRITE = "buildcraftunofficial:pipes/robot_station_available";

    private static JsonObject model() {
        try (InputStream in = RobotStationItemModelTester.class.getResourceAsStream(PATH)) {
            Assertions.assertNotNull(in, PATH + " is not on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + PATH, e);
        }
    }

    @Test
    public void testPedestalIsTwoElementsWithTheSevenOneGeometry() {
        JsonArray elements = model().getAsJsonArray("elements");
        Assertions.assertNotNull(elements, "item model must declare elements");
        Assertions.assertEquals(2, elements.size(), "the pedestal is a plate plus a post");

        JsonObject plate = elements.get(0).getAsJsonObject();
        assertVec(plate, "from", 3, 4, 4);
        assertVec(plate, "to", 4, 12, 12);
        Assertions.assertTrue(plate.get("shade").getAsBoolean(), "plate must be shaded");

        JsonObject post = elements.get(1).getAsJsonObject();
        assertVec(post, "from", 0, 6.92f, 6.92f);
        assertVec(post, "to", 3, 9.08f, 9.08f);
        Assertions.assertTrue(post.get("shade").getAsBoolean(), "post must be shaded");
    }

    @Test
    public void testPlateHasSixFacesAndPostOmitsEast() {
        JsonArray elements = model().getAsJsonArray("elements");
        Assertions.assertEquals(
            Set.of("down", "up", "north", "south", "west", "east"),
            elements.get(0).getAsJsonObject().getAsJsonObject("faces").keySet(),
            "the plate is a full box — its EAST face shows through the pipe's glass windows");
        Assertions.assertEquals(
            Set.of("down", "up", "north", "south", "west"),
            elements.get(1).getAsJsonObject().getAsJsonObject("faces").keySet(),
            "the post's EAST face abuts the plate and must be omitted");
    }

    @Test
    public void testEveryFaceResolvesToTheAvailableSprite() {
        JsonObject model = model();
        JsonObject textures = model.getAsJsonObject("textures");
        Assertions.assertNotNull(textures, "item model must declare textures");

        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                String var = face.getValue().getAsJsonObject().get("texture").getAsString();
                Assertions.assertEquals(SPRITE, resolve(textures, var),
                    "face " + face.getKey() + " must draw the available sprite (1.7.10 used one icon "
                        + "for the whole item pedestal)");
            }
        }
        Assertions.assertEquals(SPRITE, resolve(textures, "#particle"),
            "the particle texture must follow the pedestal");
    }

    @Test
    public void testDisplayBlockIsUntouched() {
        JsonObject model = model();
        Assertions.assertEquals("block/block", model.get("parent").getAsString());
        Assertions.assertEquals("front", model.get("gui_light").getAsString());

        JsonObject display = model.getAsJsonObject("display");
        Assertions.assertNotNull(display, "the hand-authored display block must survive");
        assertVec(display.getAsJsonObject("gui"), "rotation", 0, 90, 0);
        assertVec(display.getAsJsonObject("gui"), "scale", 1f, 1f, 1f);
        assertVec(display.getAsJsonObject("ground"), "translation", 0, 3, 0);
        assertVec(display.getAsJsonObject("thirdperson_righthand"), "rotation", 75, 45, 0);
        assertVec(display.getAsJsonObject("firstperson_lefthand"), "rotation", 0, 225, 0);
    }

    /** Follows vanilla's {@code #var} indirection (the map keys carry no {@code #}). */
    private static String resolve(JsonObject textures, String var) {
        String key = var;
        for (int hop = 0; hop < 10 && key.startsWith("#"); hop++) {
            JsonElement next = textures.get(key.substring(1));
            Assertions.assertNotNull(next, "unresolved texture variable " + key);
            key = next.getAsString();
        }
        return key;
    }

    private static void assertVec(JsonObject owner, String member, float x, float y, float z) {
        JsonArray arr = owner.getAsJsonArray(member);
        Assertions.assertNotNull(arr, "missing " + member);
        Assertions.assertEquals(3, arr.size(), member + " must have 3 components");
        Assertions.assertEquals(x, arr.get(0).getAsFloat(), 1e-6f, member + "[0]");
        Assertions.assertEquals(y, arr.get(1).getAsFloat(), 1e-6f, member + "[1]");
        Assertions.assertEquals(z, arr.get(2).getAsFloat(), 1e-6f, member + "[2]");
    }
}
