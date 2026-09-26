/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The blank robot's "Not programmed" refusal is shown to every player, so its key must be translated in BOTH
 *  shipped languages — an English-only key is exactly the leak the translation sweep hunts for. */
public class ItemRobotNotProgrammedLangTest {

    private static JsonObject lang(String file) {
        try (InputStream in = ItemRobotNotProgrammedLangTest.class
                .getResourceAsStream("/assets/buildcraftunofficial/lang/" + file)) {
            Assertions.assertNotNull(in, file + " is not on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + file, e);
        }
    }

    @Test
    public void notProgrammedIsTranslatedInEnglishAndChinese() {
        JsonObject en = lang("en_us.json");
        JsonObject zh = lang("zh_cn.json");
        Assertions.assertTrue(en.has(ItemRobot.NOT_PROGRAMMED_KEY), "en_us must carry the refusal line");
        Assertions.assertTrue(zh.has(ItemRobot.NOT_PROGRAMMED_KEY), "zh_cn must carry the refusal line");
        String enText = en.get(ItemRobot.NOT_PROGRAMMED_KEY).getAsString();
        String zhText = zh.get(ItemRobot.NOT_PROGRAMMED_KEY).getAsString();
        Assertions.assertFalse(enText.isBlank(), "the English line must not be blank");
        Assertions.assertFalse(zhText.isBlank(), "the Chinese line must not be blank");
        Assertions.assertNotEquals(enText, zhText, "the Chinese line must be translated, not copied");
    }
}
