/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Version-neutral {@link BlockEntityType} construction. The constructor diverged across the 1.21.5 cliff:
 * 1.21.1 takes {@code (BlockEntitySupplier, Set<Block>, Type<?>)} — including a datafixer {@code Type} that
 * mods pass {@code null} for — whereas 1.21.10+ takes {@code (BlockEntitySupplier, Block...)}. There is no
 * single call form valid on both, so BuildCraft's {@code BC*BlockEntities} registries route construction
 * through here. The {@code >=1.21.10} branch is exactly the call BuildCraft already makes today, so the
 * released nodes are unchanged.
 */
public class BlockEntityTypeUtilBC {
    public static <T extends BlockEntity> BlockEntityType<T> create(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... validBlocks) {
        //? if >=1.21.10 {
        return new BlockEntityType<T>(factory, validBlocks);
        //?} else {
        /*return new BlockEntityType<T>(factory, java.util.Set.of(validBlocks), null);*/
        //?}
    }
}
