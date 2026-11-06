/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.config;

/**
 * Which side(s) a config file belongs to, in BuildCraft's own terms. Each loader maps these onto its own config
 * types, so common code never names a loader's enum.
 */
public enum BCConfigType {
    /** Per-player display preferences; read only on a physical client and never synced. */
    CLIENT,
    /**
     * Read on both sides and synced from the server to the client. Only NeoForge 26.3 (FML 12's {@code SYNCED}) syncs
     * it: before that NeoForge has no synced type this maps to, and Fabric has none either, so there it behaves as
     * {@link #LOCAL}. Nothing registers a spec as this yet; decide the older nodes before one does.
     */
    COMMON,
    /** Read on both sides from the global config directory and never synced (the legacy "common" behaviour). */
    LOCAL
}
