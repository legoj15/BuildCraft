/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import java.util.function.Consumer;

/**
 * Client-only game hooks, typed over vanilla and BuildCraft-owned values (docs/platform-layer.md, "Client render").
 * Each call adds one listener, at the loader's default priority unless the method says otherwise; listeners of
 * one hook run in the order they were added.
 */
public interface ClientHooks {

    /**
     * Draws into the world once per frame, after the translucent blocks (NeoForge: {@code SubmitCustomGeometryEvent}
     * from 26.1, {@code RenderLevelStageEvent} at {@code AFTER_TRANSLUCENT_BLOCKS} before it).
     */
    void onLevelRender(Consumer<BCLevelRenderContext> renderer);
}
