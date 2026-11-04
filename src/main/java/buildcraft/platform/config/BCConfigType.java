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
    /** Read on both sides and synced from the server to the client. */
    COMMON,
    /** Read on both sides from the global config directory and never synced (the legacy "common" behaviour). */
    LOCAL
}
