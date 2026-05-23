/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import java.util.function.Supplier;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class BCTransportAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BCTransport.MODID);

    /** Player-side bitmask of pipe-wire colours the player has ever placed. One bit per
     *  {@link DyeColor#getId()}; when all sixteen are set the {@code colorful_electrician}
     *  advancement fires. Persisted across save/load and copied on respawn so death does
     *  not reset progress. */
    public static final Supplier<AttachmentType<WireColoursPlaced>> WIRE_COLOURS_PLACED =
        ATTACHMENTS.register("wire_colours_placed",
            () -> AttachmentType.serializable(WireColoursPlaced::new).copyOnDeath().build());

    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static final class WireColoursPlaced implements ValueIOSerializable {
        public static final int ALL_COLOURS_MASK = 0xFFFF; // 16 bits, one per DyeColor

        private int mask = 0;

        /** Marks {@code colour} as placed. Returns true iff this is the first time this
         *  colour has been seen — i.e. the bit flipped 0 → 1. */
        public boolean markPlaced(DyeColor colour) {
            int bit = 1 << colour.getId();
            if ((mask & bit) != 0) return false;
            mask |= bit;
            return true;
        }

        public boolean isComplete() {
            return mask == ALL_COLOURS_MASK;
        }

        @Override
        public void serialize(ValueOutput output) {
            output.putInt("mask", mask);
        }

        @Override
        public void deserialize(ValueInput input) {
            mask = input.getIntOr("mask", 0);
        }
    }
}
