/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
//? if >=1.21.10 {
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;

/**
 * Builds the version-neutral {@link BCValueOutput}/{@link BCValueInput} pair over a plain {@link CompoundTag}, so a
 * unit test can run a tile's {@code writeData}/{@code readData} (or a codec such as {@code OwnerData}) without a world.
 * The 1.21.10 cliff lives here once: those nodes wrap vanilla's {@code TagValueOutput}/{@code TagValueInput}, older
 * ones wrap the tag directly.
 */
public final class ValueIoTestUtil {
    private ValueIoTestUtil() {}

    /** A write target and the tag it produces. */
    public static final class Written {
        public final BCValueOutput out;
        private final Supplier<CompoundTag> result;

        private Written(BCValueOutput out, Supplier<CompoundTag> result) {
            this.out = out;
            this.result = result;
        }

        /** Everything written so far, as NBT. */
        public CompoundTag tag() {
            return result.get();
        }
    }

    public static Written newOutput() {
        //? if >=1.21.10 {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        return new Written(new BCValueOutput(output), output::buildResult);
        //?} else {
        /*CompoundTag tag = new CompoundTag();
        return new Written(new BCValueOutput(tag), () -> tag);*/
        //?}
    }

    /** A reader over {@code tag}. */
    public static BCValueInput inputOf(CompoundTag tag) {
        //? if >=1.21.10 {
        return new BCValueInput(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        //?} else {
        /*return new BCValueInput(tag);*/
        //?}
    }

    /** The string stored under {@code key}, or {@code fallback} when absent. Version-neutral: {@code CompoundTag}'s own
     *  string getter returns an Optional from 1.21.5 and a bare String before it. */
    public static String stringOr(CompoundTag tag, String key, String fallback) {
        return inputOf(tag).getStringOr(key, fallback);
    }

    /** Runs {@code writer} against a fresh output and returns the NBT it produced. */
    public static CompoundTag write(Consumer<BCValueOutput> writer) {
        Written written = newOutput();
        writer.accept(written.out);
        return written.tag();
    }
}
