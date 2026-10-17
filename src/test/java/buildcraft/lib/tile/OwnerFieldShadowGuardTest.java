/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * No block entity keeps an owner of its own. {@link AbstractBCBlockEntity} owns the one {@code owner} field, its
 * {@code getOwner}/{@code setOwner} and the placement hook; a subclass that declares its own {@code owner} (or any other
 * {@link GameProfile} field) hides it, so the inherited setter and the inherited save change one value while
 * {@code getOwner()} reads another. That is how the pipe holder and the Filler each ended up unable to be given an owner
 * by generic code. Reflection over this node's compiled classes catches it whatever the field is named or typed.
 */
public class OwnerFieldShadowGuardTest {

    @Test
    public void noBlockEntitySubclassDeclaresAnOwnerFieldOfItsOwn() throws Exception {
        List<Path> roots = MainSourceSet.classesDirs();
        ClassLoader loader = OwnerFieldShadowGuardTest.class.getClassLoader();

        List<String> offenders = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Path classFile : MainSourceSet.classFiles()) {
            String name = classNameOf(roots, classFile);
            Class<?> type;
            try {
                type = Class.forName(name, false, loader);
            } catch (LinkageError e) {
                // A class that cannot link in the unit-test JVM cannot be a loaded block entity here either.
                continue;
            }
            if (type == AbstractBCBlockEntity.class || !AbstractBCBlockEntity.class.isAssignableFrom(type)) {
                continue;
            }
            seen.add(name);
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals("owner") || field.getType() == GameProfile.class) {
                    offenders.add(name + "." + field.getName() + " : " + field.getType().getSimpleName());
                }
            }
        }

        // Liveness: a scan that found no block entities passes vacuously.
        Assertions.assertTrue(seen.size() > 30, "expected the node's block-entity classes, found " + seen);
        Assertions.assertTrue(seen.contains("buildcraft.builders.tile.TileFiller"), "TileFiller must be scanned");
        Assertions.assertTrue(seen.contains("buildcraft.transport.tile.TilePipeHolder"), "TilePipeHolder must be scanned");
        Assertions.assertTrue(offenders.isEmpty(),
                "These block entities declare an owner of their own, hiding AbstractBCBlockEntity.owner. Use the "
                        + "inherited field (setOwner/getOwner) and let TileBC_Neptune / OwnerData save it: " + offenders);
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
