/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * A menu provider that writes its own opening data however its menu is opened: through {@link BCMenus#open}, or
 * handed straight to {@code ServerPlayer#openMenu} by vanilla (a spectator clicking a block opens the block entity's
 * menu that way). NeoForge reaches it through {@code IMenuProviderExtension#writeClientSideData}.
 */
public interface MenuOpeningData {
    void writeOpeningData(RegistryFriendlyByteBuf buffer);
}
