/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.util.function.Function;

//? if <1.21.10 {
/*import javax.annotation.Nullable;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;*/
//?}
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The one place BuildCraft registers a JEI item subtype interpreter, so every plugin states only the KEY that tells
 * two stacks of an item apart. Equal keys merge into one JEI entry; different keys get their own entry (and their own
 * recipe lookup); a {@code null} key means "no subtype" (the bare item).
 *
 * <p>Why a helper: the interpreter API forks at the 1.21.10 cliff. JEI 20+ has a single-method
 * {@code ISubtypeInterpreter} that takes a lambda. The 1.21.1 node's JEI 19.x lambda form binds to
 * {@code IIngredientSubtypeInterpreter} + {@code registerSubtypeInterpreter(Item, IIngredientSubtypeInterpreter)},
 * both deprecated FOR REMOVAL since 19.9.0 — so there the key is wrapped in the non-deprecated two-method
 * {@code ISubtypeInterpreter}, which is what JEI 19.9+ reads for identity. Its second method,
 * {@code getLegacyStringSubtypeInfo}, is still abstract on that line (deprecated, not for removal); it returns the
 * key's {@code toString()} — the exact string the old lambdas returned — so 1.21.1 bookmarks keep resolving.
 */
public final class JeiSubtypes {
    private JeiSubtypes() {}

    /** @param key Maps a stack to its identity; may return null for "no subtype". */
    public static void register(ISubtypeRegistration registration, Item item, Function<ItemStack, ?> key) {
        //? if >=1.21.10 {
        registration.registerSubtypeInterpreter(item, (stack, context) -> key.apply(stack));
        //?} else {
        /*registration.registerSubtypeInterpreter(item, new KeyInterpreter(key));*/
        //?}
    }

    //? if <1.21.10 {
    /*private record KeyInterpreter(Function<ItemStack, ?> key) implements ISubtypeInterpreter<ItemStack> {
        @Override
        @Nullable
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return key.apply(stack);
        }

        // Still abstract on JEI 19.x, so it must be implemented; deprecated (not for removal) with no successor
        // on this pin. Suppressed here only — the override is the sole remaining touch point of the old API.
        @Override
        @SuppressWarnings("deprecation")
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            Object data = key.apply(stack);
            return data == null ? "" : data.toString();
        }
    }*/
    //?}
}
