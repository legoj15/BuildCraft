/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

//? if <1.21.10 {
/*/^*
 * A 1.21.1 item model that names the sheet its quads draw on, in place of the one vanilla picks for the stack
 * (NeoForge's {@code getRenderTypes}, which this overrides there; Fabric's client mixin on {@code ItemRenderer} reads
 * the first one where vanilla picks). BuildCraft's models name exactly one.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 *^/
public interface ClassicItemRenderType extends net.minecraft.client.resources.model.BakedModel {
    /^* The sheets {@code stack} draws on with this model, in order ({@code fabulous}: the fabulous-graphics pass). *^/
    java.util.List<net.minecraft.client.renderer.rendertype.RenderType> getRenderTypes(
            net.minecraft.world.item.ItemStack stack, boolean fabulous);
}
*///?}
