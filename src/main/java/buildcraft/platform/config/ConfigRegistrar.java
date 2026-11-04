/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.config;

/**
 * Registers a config spec with the loader. The spec is an {@code Object} because a contract may name only vanilla and
 * JDK types and the spec type is the loader's (NeoForge {@code ModConfigSpec}); the implementation casts it.
 */
public interface ConfigRegistrar {

    /**
     * Registers {@code spec} for the mod being constructed.
     *
     * @param spec     the loader's spec object, built by the config class
     * @param fileName the toml file name, or {@code null} for the loader's default name for {@code type}
     */
    void register(BCConfigType type, Object spec, String fileName);
}
