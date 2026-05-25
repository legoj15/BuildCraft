/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import buildcraft.lib.misc.AdvancementUtil;

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

    /** Player-side bitmask of pluggable kinds (one bit per {@link PluggablesPlaced.Kind}) the
     *  player has ever attached to a pipe. When all eight are set the {@code all_plugged_up}
     *  advancement fires. Persisted across save/load and copied on respawn. The {@code WIRE}
     *  bit collapses all 16 wire colours into a single placement signal — per-colour progress
     *  for {@code colorful_electrician} stays in {@link WireColoursPlaced}. */
    public static final Supplier<AttachmentType<PluggablesPlaced>> PLUGGABLES_PLACED =
        ATTACHMENTS.register("pluggables_placed",
            () -> AttachmentType.serializable(PluggablesPlaced::new).copyOnDeath().build());

    private static final Identifier ALL_PLUGGED_UP =
        Identifier.parse("buildcraftunofficial:all_plugged_up");

    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** Records that {@code player} has placed a pluggable of {@code kind}. On the first sight of
     *  that kind, awards the matching criterion on the {@code all_plugged_up} advancement so the
     *  client UI shows {@code x/8} progress (matching how {@code colorful_electrician} displays
     *  per-colour progress). No-ops on the client and on repeat sightings. */
    public static void recordPluggablePlacement(Player player, PluggablesPlaced.Kind kind) {
        if (player.level().isClientSide()) return;
        PluggablesPlaced data = player.getData(PLUGGABLES_PLACED.get());
        if (data.markPlaced(kind)) {
            AdvancementUtil.unlockAdvancement(player, ALL_PLUGGED_UP, kind.criterionName());
        }
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

    public static final class PluggablesPlaced implements ValueIOSerializable {
        /** One enum value per kind shown in the "pluggables" creative tab. Ordinal is the bit
         *  index in {@link #mask}; {@link #criterionName()} matches the criterion ids defined in
         *  {@code data/buildcraftunofficial/advancement/all_plugged_up.json}. Reordering or
         *  inserting kinds is a save-breaking change — existing player attachments would map
         *  bits to the wrong kinds. */
        public enum Kind {
            BLOCKER, POWER_ADAPTOR, WIRE, GATE, LENS, PULSAR, LIGHT_SENSOR, TIMER;

            public int bit() { return 1 << ordinal(); }
            public String criterionName() { return name().toLowerCase(Locale.ROOT); }
        }

        public static final int ALL_KINDS_MASK = (1 << Kind.values().length) - 1;

        private int mask = 0;

        /** Marks {@code kind} as placed. Returns true iff this is the first time this kind has
         *  been seen — i.e. the bit flipped 0 → 1. */
        public boolean markPlaced(Kind kind) {
            int bit = kind.bit();
            if ((mask & bit) != 0) return false;
            mask |= bit;
            return true;
        }

        public boolean isComplete() {
            return mask == ALL_KINDS_MASK;
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
