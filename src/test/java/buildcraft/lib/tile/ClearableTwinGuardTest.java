/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * Every spill hook has its wipe twin in the same class. On 1.21.1 a command replace ({@code /setblock}, {@code /fill},
 * {@code /clone}, structure placement) calls {@code Clearable.clearContent} on the old block entity and then removes it
 * through the {@code onRemove} catch-all that calls {@code dropContentsOnRemoval}; a tile that spills something its
 * wipe does not clear drops it on the floor (or, for {@code /clone ... move}, duplicates it). The game-test rigs in
 * {@code CommandReplaceTester} prove the wipe for a sample of tiles; this scans every compiled block entity so a new
 * spill override cannot ship without its wipe.
 */
public class ClearableTwinGuardTest {

    /** Spill hook name -> the wipe hook that must be declared beside it. The pipe holder's spill hook is
     *  {@code dropPipeCargo} (it spills only the in-transit cargo), wiped by the same {@code clearContent}. */
    private static final Map<String, String> TWINS = Map.of(
            "dropContentsOnRemoval", "clearContent",
            "dropExtraContentsOnRemoval", "clearExtraContents",
            "dropPipeCargo", "clearContent");

    @Test
    public void everySpillOverrideDeclaresItsWipe() throws Exception {
        List<Path> roots = MainSourceSet.classesDirs();
        ClassLoader loader = ClearableTwinGuardTest.class.getClassLoader();

        List<String> offenders = new ArrayList<>();
        List<String> spillers = new ArrayList<>();
        for (Path classFile : MainSourceSet.classFiles()) {
            String name = classNameOf(roots, classFile);
            Class<?> type;
            Method[] declared;
            try {
                type = Class.forName(name, false, loader);
                if (!AbstractBCBlockEntity.class.isAssignableFrom(type)) {
                    continue;
                }
                declared = type.getDeclaredMethods();
            } catch (LinkageError e) {
                // A class that cannot link in the unit-test JVM cannot be a loaded block entity here either.
                continue;
            }
            for (Map.Entry<String, String> twin : TWINS.entrySet()) {
                if (declares(declared, twin.getKey())) {
                    spillers.add(name + "#" + twin.getKey());
                    if (!declares(declared, twin.getValue())) {
                        offenders.add(name + " overrides " + twin.getKey() + " but not " + twin.getValue());
                    }
                }
            }
        }

        // Liveness: a scan that found no spill overrides of a kind would pass vacuously for that kind.
        for (String spillHook : TWINS.keySet()) {
            Assertions.assertTrue(spillers.stream().anyMatch(s -> s.endsWith("#" + spillHook)),
                    "No block entity overriding " + spillHook + " was scanned, found " + spillers);
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "A command replace on 1.21.1 would spill what these tiles' wipe leaves behind. Override the wipe hook "
                        + "over exactly the contents the spill hook drops: " + offenders);
    }

    private static boolean declares(Method[] methods, String name) {
        for (Method m : methods) {
            if (m.getName().equals(name) && !m.isSynthetic() && !m.isBridge()) {
                return true;
            }
        }
        return false;
    }

    private static String classNameOf(List<Path> roots, Path classFile) {
        for (Path root : roots) {
            if (classFile.startsWith(root)) {
                String rel = root.relativize(classFile).toString().replace('\\', '/');
                return rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
            }
        }
        throw new IllegalStateException(classFile + " is not under any main classes directory " + roots);
    }
}
