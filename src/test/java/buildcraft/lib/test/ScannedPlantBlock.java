/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A test-only plant: a class BuildCraft's setup lists wholesale as a soft block (the plant umbrella, {@code BushBlock}
 * or {@code VegetationBlock} by line), registered by {@link TestOnlyBlocks} the way a content mod registers one. It
 * stands for a block that setup must find however late its mod registers it (on Fabric after BuildCraft's own
 * initializer). A subclass because the vanilla plants' constructors are not public on every line.
 */
public final class ScannedPlantBlock extends NetherWartBlock {

    public ScannedPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
