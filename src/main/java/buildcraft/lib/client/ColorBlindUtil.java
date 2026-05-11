/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client;

import net.minecraft.client.Minecraft;

import buildcraft.lib.BCLibConfig;
import buildcraft.lib.BCLibConfig.ColorBlindMode;

/** Resolves the effective state of {@link BCLibConfig#colorBlindMode} for client rendering.
 *
 * <p>The config is a tri-state ({@link ColorBlindMode#AUTO AUTO}, {@link ColorBlindMode#ON ON},
 * {@link ColorBlindMode#OFF OFF}). {@code AUTO} mirrors Minecraft's
 * Options → Accessibility → High Contrast toggle so a player who has expressed an accessibility
 * preference at the vanilla level doesn't also have to flip a separate BuildCraft knob. The
 * vanilla High Contrast option enables the built-in {@code minecraft:high_contrast} resource
 * pack and triggers a resource pack reload — which restitches the block atlas — so toggling
 * it propagates to BuildCraft pipes via the cache-clear listener registered in
 * {@link BCLibClient}.
 *
 * <p>This helper lives in {@code buildcraft.lib.client} rather than {@code buildcraft.lib}
 * because it imports {@link Minecraft}; keeping the import out of {@link BCLibConfig} preserves
 * that class as common-side config-spec code. Call sites: {@code GuiDiamondPipe} and
 * {@code PipeBaseModelGenStandard#isCb}. */
public final class ColorBlindUtil {

    private ColorBlindUtil() {}

    /** Returns true when colourblind-friendly textures should be used right now.
     *
     * <p>Defensive against early classload (config field not yet bound — happens during
     * mod-construction phase of NeoForge boot, and under JUnit which never boots NeoForge)
     * and against being called before {@link Minecraft#getInstance()} has finished
     * initialising its options object (theoretically unreachable from any client render
     * path, but the guard is cheap). */
    public static boolean isActive() {
        if (BCLibConfig.colorBlindMode == null) {
            return false;
        }
        ColorBlindMode mode = BCLibConfig.colorBlindMode.get();
        switch (mode) {
            case ON:
                return true;
            case OFF:
                return false;
            case AUTO:
            default:
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.options == null) {
                    // Pre-init or non-client context. Default off — the listener that triggers
                    // a re-render on config or resource-pack reload will pick up the real value
                    // once everything is wired.
                    return false;
                }
                return mc.options.highContrast().get();
        }
    }
}
