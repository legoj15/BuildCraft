/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.builders.snapshot.pattern.Pattern;

/** PATTERN_POPUP is the only list a pattern reaches the Filler GUI, the action provider and the guide through, so a
 *  pattern constant left out of it (or listed twice) is unreachable (or doubled) everywhere at once. */
public class FillerPatternPopupTest {
    @Test
    public void everyPatternConstantIsInThePopupOnce() throws IllegalAccessException {
        List<Pattern> popup = BCBuildersStatements.PATTERN_POPUP.stream().flatMap(List::stream).toList();
        List<String> problems = new ArrayList<>();
        for (Field field : BCBuildersStatements.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Pattern.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Pattern pattern = (Pattern) field.get(null);
            long count = popup.stream().filter(p -> p == pattern).count();
            if (count != 1) {
                problems.add(field.getName() + " appears " + count + " times");
            }
        }
        Assertions.assertEquals(List.of(), problems);
    }
}
