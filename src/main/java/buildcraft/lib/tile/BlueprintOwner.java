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
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.core.BCCore;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * Who owns a block entity that a blueprint (or a single schematic) builds: whoever builds it, never the owner the
 * blueprint was scanned from. A machine asks claim and protection mods as its owner when it digs, places or breaks, so
 * a copy that kept the original's owner would act with the permissions of a player who never built it (and a copy of
 * someone's blueprint would lend its builder that player's rights). Two halves, both needed:
 * <ul>
 *   <li>{@link #strip} takes the owner out of the tag a build loads, so the built block entity starts with none;</li>
 *   <li>{@link #stamp} gives the built block entity its builder's owner afterwards. Every caller of a block
 *       schematic's {@code build} calls it with the owner it has: a Builder its own, a player placing a single
 *       schematic theirs. A caller with no owner to give stamps {@code null}, and the machine is unowned, which acts
 *       as the mod's own profile, as any unowned machine does.</li>
 * </ul>
 * An entity has the same problem and the first half of the answer: {@link #stripEntity}.
 */
public final class BlueprintOwner {

    private BlueprintOwner() {}

    /**
     * Takes the owner out of {@code tag}, the saved data a blueprint is about to load into a new block entity of
     * {@code block}: {@link #stripKeys}, only for a block of this mod. Another mod's block entity may use these key
     * names for something of its own, and its data is not ours to edit. The tag is the caller's own copy, never a
     * schematic's stored data.
     */
    public static void strip(CompoundTag tag, Block block) {
        if (BCCore.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
            stripKeys(tag);
        }
    }

    /**
     * Takes out of {@code tag} every shape this mod's own block entities save an owner in: the
     * {@code ownerUUID}/{@code ownerName} pair of the owner-bearing machines and of the pipe holder
     * ({@link OwnerData}; a laser or a tank saves none), and the {@code owner} child the Filler saved before it shared
     * that field (it still reads it). For the tag of one of this mod's blocks; a caller that only knows the block
     * entity is built on this mod's base class removes the pair alone ({@link OwnerData#removeOwner}).
     */
    public static void stripKeys(CompoundTag tag) {
        OwnerData.removeOwner(tag);
        if (tag.get("owner") instanceof CompoundTag) {
            tag.remove("owner");
        }
    }

    /**
     * Takes out of {@code entityTag}, the saved data a blueprint is about to spawn an entity from, who a robot of
     * this mod answers to: the player who deployed it ({@code deployer}) and the docking stations it is linked to and
     * docked at ({@code linkedStation}, {@code currentStation}, saved as positions in the world it was scanned in). A
     * robot asks a claim mod as its deployer, or else as the owner of its station, so a copy that kept either would
     * act as the player the blueprint was scanned from, and would take the original's station for its own. No rule
     * this mod ships lets a blueprint capture a robot, but a rule file of another mod could. The built robot is left
     * with no deployer and no station: it answers as the mod's own profile until it is given a home.
     */
    public static void stripEntity(CompoundTag entityTag) {
        // An id with no namespace is vanilla's, so the prefix is the whole test.
        if (NBTUtilBC.getString(entityTag, "id", "").startsWith(BCCore.MODID + ":")) {
            entityTag.remove("deployer");
            entityTag.remove("linkedStation");
            entityTag.remove("currentStation");
        }
    }

    /**
     * Makes {@code owner} the owner of the block entity a schematic just built at {@code pos}, when it is one of this
     * mod's ({@link AbstractBCBlockEntity}); any other block entity, or none, is left alone. Server side only, and for
     * a build that succeeded. {@code before} is the block entity that stood at {@code pos} before the build, or null:
     * when it is still the one standing there the build placed nothing (a schematic may report success without
     * placing a block), and a machine somebody else put in that cell keeps its owner. That holds for {@code build},
     * which refuses a cell that holds a block entity; {@code buildWithoutChecks}, which no code of this mod calls,
     * can place onto a machine of the same kind and keep its block entity, and such a machine is left unstamped. The
     * owner is set once the build is over, so a {@link BuiltFromBlueprint} hook, which runs inside the build, sees
     * none and must not act as the owner. Marks the chunk dirty so the owner persists, as a placement does; clients
     * get it with the placement's own block update, which the game sends at the end of the tick and reads the block
     * entity for then.
     */
    public static void stamp(Level level, BlockPos pos, @Nullable GameProfile owner, @Nullable BlockEntity before) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof AbstractBCBlockEntity tile)
            || tile == before) {
            return;
        }
        tile.setOwner(owner);
        tile.setChanged();
    }
}
