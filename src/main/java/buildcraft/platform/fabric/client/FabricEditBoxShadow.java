/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && <1.21.10 {
/*/^*
 * The text-shadow switch {@code EditBoxMixin} adds to 1.21.1's {@code EditBox} (NeoForge patches the same setter in;
 * vanilla has it from 1.21.2). Every edit box implements it on Fabric 1.21.1.
 *^/
public interface FabricEditBoxShadow {
    void buildcraft$setTextShadow(boolean shadow);
}
*///?}
