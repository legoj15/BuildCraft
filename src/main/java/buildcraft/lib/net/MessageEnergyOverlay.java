/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.function.Predicate;

import net.minecraft.locale.Language;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import buildcraft.energy.BCEnergyConfig;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MessageUtil;

/**
 * Server -> client: an action-bar message that names an energy figure, as DATA rather than text.
 *
 * <p>{@link LocaleUtil}'s formatters answer from the player's display settings (FE/RF naming, full or short unit
 * names, number separators, the flow display mode), all of which live on the CLIENT config spec, in the player's
 * language. A dedicated server has none of them, so a message it formatted itself would carry its own defaults and its
 * own English to every player. This payload ships only the translation key, which unit family the figure is in and the
 * raw amount; {@link #resolve()} builds the translatable Component on the client, where those settings exist. Send it
 * through {@link MessageUtil#sendEnergyOverlay}, never by formatting on the server (a source guard,
 * {@code ServerUnitBakingGuardTester}, fails the build if a message sender also calls a {@code LocaleUtil} formatter).
 *
 * <p>Two inputs to the figure are the SERVER's, not the player's: {@code powerMode} (DISPLAY_RF re-units MJ figures as
 * FE/RF) and the {@code mjRfConversionAmount} ratio. Both are COMMON config, back the server's real MJ⇄RF conversion,
 * and are never synced to a remote client, whose own copy may say anything. {@link #of} snapshots the sender's values
 * into the payload, so the client converts at the rate the server's machines actually use.
 *
 * @param key       base translation key of the message, taking the formatted figure as its one {@code %s}; the
 *                  client swaps in the key's {@code .rf} sibling when its RF-naming toggle is on and the language has
 *                  one
 * @param kind      which unit family {@code amount} is in
 * @param amount    the figure, in the unit {@code kind} names
 * @param displayRf the server's {@code powerMode == DISPLAY_RF}: an {@link Kind#MJ_FLOW} figure shows as FE/RF
 * @param mjPerRf   the server's MJ⇄RF ratio, micro-MJ per 1 RF (out of range falls back to the default)
 */
public record MessageEnergyOverlay(String key, Kind kind, long amount, boolean displayRf, long mjPerRf)
        implements CustomPacketPayload {

    /** The payload for a figure sent from THIS JVM (the server), carrying its own power mode and MJ⇄RF ratio. */
    public static MessageEnergyOverlay of(String key, Kind kind, long amount) {
        return new MessageEnergyOverlay(key, kind, amount, LocaleUtil.isDisplayRf(), LocaleUtil.currentMjPerRf());
    }

    /** The unit family of {@link #amount()}; also picks the formatter the client uses. */
    public enum Kind {
        /** Micro-MJ per tick, formatted like every MJ flow readout ({@link LocaleUtil#localizeMjFlow(long)}). */
        MJ_FLOW,
        /** FE/RF per tick, formatted like every RF flow readout ({@link LocaleUtil#localizeRfFlow(int)}). */
        RF_FLOW
    }

    public static final CustomPacketPayload.Type<MessageEnergyOverlay> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:energy_overlay"));

    public static final StreamCodec<FriendlyByteBuf, MessageEnergyOverlay> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.key);
                NetEnumUtil.write(buf, payload.kind);
                buf.writeVarLong(payload.amount);
                buf.writeBoolean(payload.displayRf);
                buf.writeVarLong(payload.mjPerRf);
            },
            buf -> new MessageEnergyOverlay(buf.readUtf(), NetEnumUtil.read(buf, Kind.class), buf.readVarLong(),
                    buf.readBoolean(), buf.readVarLong()));

    /** The Component to show, built against THIS JVM's language and display settings and the payload's (server's)
     *  power mode and ratio. Client-side only in effect: a dedicated server has no toggle and the server's language,
     *  which is exactly what this exists to avoid. */
    public Component resolve() {
        return resolve(BCEnergyConfig.rfNamingEnabled(), Language.getInstance()::has);
    }

    /** Pure form of {@link #resolve()} with the RF-naming toggle and the language lookup passed in. The formatted
     *  figure itself still reads the live {@link LocaleUtil} display settings. */
    public Component resolve(boolean rfNaming, Predicate<String> hasKey) {
        String figure = switch (kind) {
            case MJ_FLOW -> LocaleUtil.localizeMjFlow(amount, displayRf, mjPerRf);
            // localizeRfFlow takes an int: clamp rather than wrap a corrupt amount negative.
            case RF_FLOW -> LocaleUtil.localizeRfFlow((int) Math.max(0L, Math.min(Integer.MAX_VALUE, amount)));
        };
        return Component.translatable(BCEnergyConfig.rfNamedKey(key, rfNaming, hasKey), figure);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client-side handler: shows the resolved message on the action bar. Reaches no client-only class
     *  ({@code ctx.player()} is the local player), so it is safe to link on either side. */
    public static void handle(MessageEnergyOverlay message, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> MessageUtil.sendOverlayMessage(ctx.player(), message.resolve()));
    }
}
