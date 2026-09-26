/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

/**
 * PipeItemModel picks a painted fluid pipe's dyed item sprite by {@code itemModelTop.getTexture(0)} instead of the
 * deprecated {@code itemTextureTop}; the dyed-sprite lookup falls back to sprite 0 on a bad index rather than failing,
 * so a divergence between the two would only ever show as a wrong texture. Pin that they agree for every pipe.
 */
public class PipeItemTextureIndexTester {

    @Test
    @SuppressWarnings("deprecation") // reading the deprecated mirror field is the point
    public void itemModelTopMatchesTheDeprecatedTextureIndex() {
        int count = 0;
        for (PipeDefinition def : PipeApi.pipeRegistry.getAllRegisteredPipes()) {
            Assertions.assertEquals(def.itemTextureTop, def.itemModelTop.getTexture(0),
                "item top texture index diverged for " + def.identifier);
            count++;
        }
        Assertions.assertTrue(count > 20, "expected BuildCraft's pipe definitions to be registered, found " + count);
    }
}
