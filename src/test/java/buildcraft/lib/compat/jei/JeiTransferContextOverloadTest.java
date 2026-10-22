/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;

/**
 * JEI 30.30+ (the 26.2 node) routes "+" transfers through {@code transferRecipe(IRecipeTransferContext, boolean)}
 * and marks the old six-arg overload for removal. The context overrides sit behind a {@code //? if >=26.2}
 * directive, so a mangled directive or a dropped override would compile clean (JEI's default bridges to the old
 * one) and only break when JEI deletes it. Pins that on whichever node's JEI has the new overload; on older JEI
 * lines it checks the handlers still load and implement the interface.
 *
 * <p>Only two classes implement the interface directly: {@link AbstractTransferHandler}, the skeleton every
 * item/bucket machine handler extends, and the crafting-table {@link BlueprintTransferHandler}.
 */
public class JeiTransferContextOverloadTest {

    private static final List<Class<?>> DECLARING_HANDLERS = List.of(
        AbstractTransferHandler.class,
        BlueprintTransferHandler.class);

    @Test
    public void handlersOverrideTheContextOverloadWhenJeiHasIt() {
        Class<?> context = contextType();
        for (Class<?> handler : DECLARING_HANDLERS) {
            Assertions.assertTrue(IRecipeTransferHandler.class.isAssignableFrom(handler),
                handler.getName() + " must implement IRecipeTransferHandler");
            if (context == null) {
                continue;
            }
            Method override;
            try {
                override = handler.getDeclaredMethod("transferRecipe", context, boolean.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(handler.getName() + " does not override transferRecipe("
                    + context.getSimpleName() + ", boolean) on this node's JEI", e);
            }
            Assertions.assertTrue(Modifier.isPublic(override.getModifiers()), override + " must be public");
        }
    }

    private static Class<?> contextType() {
        try {
            return Class.forName("mezz.jei.api.recipe.transfer.IRecipeTransferContext", false,
                JeiTransferContextOverloadTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
