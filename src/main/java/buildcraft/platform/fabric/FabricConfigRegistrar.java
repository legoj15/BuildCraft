/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.concurrent.atomic.AtomicBoolean;

import buildcraft.platform.config.BCConfigType;
import buildcraft.platform.config.ConfigRegistrar;

/^*
 * {@link ConfigRegistrar} on Fabric: a documented no-op. Fabric has no {@code ModConfigSpec}, and the choice between
 * Forge Config API Port and an in-house builder (open question 1 in {@code docs/platform-layer.md}) is the user's call,
 * so nothing is registered, the config classes keep their spec defaults, and the first call logs that once.
 *^/
public final class FabricConfigRegistrar implements ConfigRegistrar {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    @Override
    public void register(BCConfigType type, Object spec, String fileName) {
        if (WARNED.compareAndSet(false, true)) {
            org.slf4j.LoggerFactory.getLogger("buildcraft.platform")
                .warn("Config registration is not implemented on Fabric yet; using built-in defaults");
        }
    }
}
*///?}
