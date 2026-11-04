/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.render;

/**
 * What a pipe holder's block model reads from its block entity. The block entity hands it over through
 * {@link RenderDataServices#blockEntityRenderData} (NeoForge: one {@code ModelProperty} in its {@code ModelData};
 * Fabric: {@code RenderDataBlockEntity#getRenderData}), and the model passes it to the
 * {@code buildcraft.platform.client.PipeModelQuads} it was registered with, which alone knows what is inside.
 *
 * <p>A view, not a copy: the quads are read through it while the chunk section meshes, exactly as the model used to
 * read the block entity itself, so a change that re-meshes the section is drawn whether or not it also refreshed the
 * render data.
 */
public interface PipeRenderData {}
