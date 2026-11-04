/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.model.data.ModelData;

import buildcraft.platform.Platform;
import buildcraft.platform.render.PipeRenderData;

/**
 * The render-data seam on NeoForge: what a block entity's {@code getModelData} returns through
 * {@link Platform#renderData()} must give the block model back the very {@link PipeRenderData} it was built from
 * (the pipe model reads the live tile through it), and model data without it must read as none.
 */
public class RenderDataSeamTest {
    @Test
    public void modelDataCarriesThePipeRenderData() {
        PipeRenderData data = new PipeRenderData() {};
        Object wrapped = Platform.renderData().blockEntityRenderData(data);
        ModelData modelData = assertInstanceOf(ModelData.class, wrapped);
        assertSame(data, NeoForgeRenderData.pipe(modelData));
    }

    @Test
    public void emptyModelDataCarriesNone() {
        assertNull(NeoForgeRenderData.pipe(ModelData.EMPTY));
    }
}
