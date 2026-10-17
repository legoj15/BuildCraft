/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.lang.reflect.Field;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Level stand-ins for unit tests that only need to ask a level WHICH level it is — {@link Level#isClientSide()}
 * and {@link Level#dimension()} — and never touch its contents (chunks, entities, saved data).
 *
 * <p>A real client level cannot exist in the dedicated-server unit-test JVM, and building any {@link Level}
 * needs a registry access, a dimension type holder and level data whose constructor shape differs on every node
 * line. So this allocates a {@link ServerLevel} WITHOUT running a constructor and overwrites the two final
 * fields the marker caches key on. Anything else on the object is null: a test that reaches for the level's
 * data storage, chunk source or registry gets an NPE, which is the right failure for a test that has outgrown
 * this helper (use a game test and a real {@code ServerLevel} instead).
 *
 * <p>Each call returns a NEW object, standing in for a new level instance; two fakes with the same dimension key
 * model "the same dimension in two successive worlds".
 */
public final class FakeLevels {

    private FakeLevels() {}

    /** A level that reports the given side and dimension key, and nothing else. */
    public static Level fake(boolean clientSide, ResourceKey<Level> dimension) {
        try {
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Object level = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, ServerLevel.class);
            set("isClientSide", level, clientSide);
            set("dimension", level, dimension);
            return (Level) level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not build a fake level", e);
        }
    }

    private static void set(String field, Object level, Object value) throws ReflectiveOperationException {
        Field f = Level.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(level, value);
    }
}
