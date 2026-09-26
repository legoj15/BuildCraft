/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.BCGraphics;

/**
 * What a {@link BCButton} draws on top of vanilla's button face. Every factory centres its content on the face;
 * icons that depend on live state (the current mode, the painted colour) are expressed with {@link #dynamic}, so a
 * screen never has to push state into its buttons.
 */
@FunctionalInterface
public interface ButtonIcon {

    /**
     * @param tint ARGB multiplier from {@link ButtonFace#iconTint}: vanilla's inactive grey on an inactive button,
     *             white otherwise, carrying the widget alpha. Item renders ignore it (vanilla items have no tint).
     */
    void draw(BCGraphics graphics, int x, int y, int width, int height, int tint);

    /** A {@link ButtonSprite} (GUI-atlas icon) at its native size. */
    static ButtonIcon sprite(ButtonSprite sprite) {
        return (g, x, y, w, h, tint) -> g.guiSprite(sprite.id(),
            x + (w - sprite.size()) / 2, y + (h - sprite.size()) / 2, sprite.size(), sprite.size(), tint);
    }

    /** An item, rendered like an inventory slot's (16×16, the item's own model — so resource packs apply). */
    static ButtonIcon item(Supplier<ItemStack> stack) {
        return (g, x, y, w, h, tint) -> {
            ItemStack s = stack.get();
            if (s != null && !s.isEmpty()) {
                g.item(s, x + (w - 16) / 2, y + (h - 16) / 2);
            }
        };
    }

    /** One of two sprites by a live condition — for toggles whose icon IS the state (the Filler's excavate/invert). */
    static ButtonIcon either(BooleanSupplier condition, ButtonSprite whenTrue, ButtonSprite whenFalse) {
        ButtonIcon t = sprite(whenTrue);
        ButtonIcon f = sprite(whenFalse);
        return (g, x, y, w, h, tint) -> (condition.getAsBoolean() ? t : f).draw(g, x, y, w, h, tint);
    }

    /** Picks the icon every frame from live state; {@code null} draws nothing. */
    static ButtonIcon dynamic(Supplier<? extends ButtonIcon> choice) {
        return (g, x, y, w, h, tint) -> {
            ButtonIcon icon = choice.get();
            if (icon != null) {
                icon.draw(g, x, y, w, h, tint);
            }
        };
    }

    /** This icon with {@code overlay} drawn on top of it. */
    default ButtonIcon with(ButtonIcon overlay) {
        return (g, x, y, w, h, tint) -> {
            draw(g, x, y, w, h, tint);
            overlay.draw(g, x, y, w, h, tint);
        };
    }
}
