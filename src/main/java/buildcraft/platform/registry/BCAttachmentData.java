/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
*///?}

/**
 * The payload of a {@link BCAttachment}: a mutable value that saves itself, on vanilla's {@code ValueOutput} and
 * {@code ValueInput} (1.21.10+) or as an NBT compound (1.21.1). The method names are NeoForge's
 * ({@code ValueIOSerializable}, {@code INBTSerializable}), and the NeoForge adapter calls exactly these, so a payload
 * saves the same bytes it saved as a NeoForge serializable attachment.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 */
public interface BCAttachmentData {
    //? if >=1.21.10 {
    /** Writes this value's state. */
    void serialize(ValueOutput output);

    /** Reads the state {@link #serialize} wrote into this (default-constructed) value. */
    void deserialize(ValueInput input);
    //?} else {
    /*/^* Writes this value's state. *^/
    CompoundTag serializeNBT(HolderLookup.Provider provider);

    /^* Reads the state {@link #serializeNBT} wrote into this (default-constructed) value. *^/
    void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag);
    *///?}
}
