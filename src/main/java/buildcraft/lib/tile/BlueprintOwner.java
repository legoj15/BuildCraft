/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import buildcraft.core.BCCore;

/**
 * Who owns a block entity that a blueprint (or a single schematic) builds: whoever builds it, never the owner the
 * blueprint was scanned from. A machine asks claim and protection mods as its owner when it digs, places or breaks, so
 * a copy that kept the original's owner would act with the permissions of a player who never built it (and a copy of
 * someone's blueprint would lend its builder that player's rights). Two halves, both needed:
 * <ul>
 *   <li>{@link #strip} takes the owner out of the tag a build loads, so the built block entity starts with none;</li>
 *   <li>{@link #stamp} gives the built block entity its builder's owner afterwards. Every caller of a schematic's
 *       {@code build} calls it with the owner it has: a Builder its own, a player placing a single schematic theirs.
 *       A caller with no owner to give stamps {@code null}, and the machine is unowned, which acts as the mod's own
 *       profile, as any unowned machine does.</li>
 * </ul>
 */
public final class BlueprintOwner {

    private BlueprintOwner() {}

    /**
     * Takes the owner out of {@code tag}, the saved data a blueprint is about to load into a new block entity of
     * {@code block}: the {@code ownerUUID}/{@code ownerName} pair every BuildCraft machine and pipe holder saves
     * ({@link OwnerData}), and the {@code owner} child the Filler saved before it shared that field (it still reads
     * it). Only for a block of this mod: another mod's block entity may use these key names for something of its own,
     * and its data is not ours to edit. The tag is the caller's own copy, never a schematic's stored data.
     */
    public static void strip(CompoundTag tag, Block block) {
        if (!BCCore.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
            return;
        }
        OwnerData.removeOwner(tag);
        if (tag.get("owner") instanceof CompoundTag) {
            tag.remove("owner");
        }
    }

    /**
     * Makes {@code owner} the owner of the block entity a schematic just built at {@code pos}, when it is one of this
     * mod's ({@link AbstractBCBlockEntity}); any other block entity, or none, is left alone. Server side only, and for
     * a build that succeeded. The owner is set once the build is over, so a {@link BuiltFromBlueprint} hook, which runs
     * inside the build, sees none and must not act as the owner. Marks the chunk dirty so the owner persists, as a
     * placement does; the block entity's data reaches the clients with the placement's own update.
     */
    public static void stamp(Level level, BlockPos pos, @Nullable GameProfile owner) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof AbstractBCBlockEntity tile)) {
            return;
        }
        tile.setOwner(owner);
        tile.setChanged();
    }
}
