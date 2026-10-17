/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.Map;
import java.util.Set;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.locale.Language;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjRfConversion;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.misc.LocaleUtil;

/** The wire format and client-side resolution of {@link MessageEnergyOverlay}.
 *
 * <p>The server used to format "Switched to 80 Minecraft Joules per second limit" itself with {@code LocaleUtil} — whose
 * display settings (FE/RF naming, full unit names, separators, flow mode) are client config that reads as unset on a
 * dedicated server, and whose language is the server's. The payload carries the raw amount instead and the client
 * formats it against its own settings and language; this class pins both halves of that.
 *
 * <p>The two settings that are NOT the player's — {@code powerMode} (whether MJ figures display as FE/RF) and the
 * {@code mjRfConversionAmount} ratio — are COMMON config: the server's values are the ones its machines convert at,
 * and a remote client's local copy is never synced to them. The payload therefore carries the server's DISPLAY_RF
 * flag and ratio, and the client converts with those rather than its own. */
public class MessageEnergyOverlayTest {

    private static final String KEY = "chat.engine.creative.mode";

    /** 0.2 MJ per RF (5 RF make 1 MJ): the top of the config range, and half the default 10 RF per MJ. */
    private static final long FIVE_RF_PER_MJ = MjRfConversion.MAX_MJ_PER_RF;

    /** A payload from a server on MJ display at the default ratio. */
    private static MessageEnergyOverlay mjDisplay(String key, MessageEnergyOverlay.Kind kind, long amount) {
        return new MessageEnergyOverlay(key, kind, amount, false, MjRfConversion.DEFAULT_MJ_PER_RF);
    }

    private Language original;

    @BeforeEach
    public void rememberLanguage() {
        original = Language.getInstance();
    }

    @AfterEach
    public void restoreLanguage() {
        // Language.instance is static; leaking a stub would corrupt every later test in the JVM.
        Language.inject(original);
    }

    /** Makes {@code entries} the whole active language, as a client running some language would have it. */
    private static void injectLanguage(Map<String, String> entries) {
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String elementId, String defaultValue) {
                return entries.getOrDefault(elementId, defaultValue);
            }

            @Override
            public boolean has(String elementId) {
                return entries.containsKey(elementId);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
                return FormattedCharSequence.EMPTY;
            }
        });
    }

    private static TranslatableContents translatable(Component component) {
        Assertions.assertInstanceOf(TranslatableContents.class, component.getContents(),
                "the resolved message must stay a translatable Component: " + component);
        return (TranslatableContents) component.getContents();
    }

    // -- wire format -------------------------------------------------------

    @Test
    public void theWireFormatRoundTripsEveryKind() {
        for (MessageEnergyOverlay.Kind kind : MessageEnergyOverlay.Kind.values()) {
            MessageEnergyOverlay sent = mjDisplay("chat.pipe.iron.mode", kind, 123_456_789_012L);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            MessageEnergyOverlay.STREAM_CODEC.encode(buf, sent);

            Assertions.assertEquals(sent, MessageEnergyOverlay.STREAM_CODEC.decode(buf), kind + " must round-trip");
            Assertions.assertEquals(0, buf.readableBytes(), kind + ": the decoder must consume the whole payload");
        }
        MessageEnergyOverlay rfServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, 7, true,
                FIVE_RF_PER_MJ);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        MessageEnergyOverlay.STREAM_CODEC.encode(buf, rfServer);
        Assertions.assertEquals(rfServer, MessageEnergyOverlay.STREAM_CODEC.decode(buf),
                "the server's DISPLAY_RF flag and ratio must survive the wire");
        Assertions.assertEquals(0, buf.readableBytes());
    }

    @Test
    public void thePayloadIsDataNotText() {
        // The server fills in these fields and nothing else: a key to translate, which unit family the figure is in,
        // the raw amount, and its own power mode and MJ/RF ratio. There is no String a server could bake a unit or a
        // number format into.
        var components = MessageEnergyOverlay.class.getRecordComponents();
        Assertions.assertEquals(5, components.length);
        Assertions.assertEquals(String.class, components[0].getType());
        Assertions.assertEquals(MessageEnergyOverlay.Kind.class, components[1].getType());
        Assertions.assertEquals(long.class, components[2].getType());
        Assertions.assertEquals(boolean.class, components[3].getType());
        Assertions.assertEquals(long.class, components[4].getType());
    }

    @Test
    public void theFactorySnapshotsTheSendingJvmsPowerModeAndRatio() {
        // MessageUtil.sendEnergyOverlay builds the payload with of(), on the server: what it carries is the server's.
        MessageEnergyOverlay sent = MessageEnergyOverlay.of(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ);
        Assertions.assertEquals(LocaleUtil.isDisplayRf(), sent.displayRf());
        Assertions.assertEquals(LocaleUtil.currentMjPerRf(), sent.mjPerRf());

        BCLibConfig.PowerMode before = BCLibConfig.powerMode.get();
        try {
            BCLibConfig.powerMode.set(BCLibConfig.PowerMode.DISPLAY_RF);
            Assertions.assertTrue(MessageEnergyOverlay.of(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, 1).displayRf(),
                    "a server on DISPLAY_RF must say so in the payload");
        } finally {
            BCLibConfig.powerMode.set(before);
        }
    }

    // -- the server's power mode and ratio --------------------------------

    @Test
    public void anMjFigureIsConvertedAtTheServersRatioNotTheClients() {
        // Server: powerMode = DISPLAY_RF, mjRfConversionAmount = 0.2 (5 RF per MJ). This client is on the defaults
        // (MJ display, 10 RF per MJ). 1 MJ/t at the server's ratio is 5 RF/t = 100 RF/s; at the client's it would be
        // 200 RF/s, and on the client's own mode it would read in MJ.
        Assertions.assertFalse(LocaleUtil.isDisplayRf(), "precondition: this JVM is on MJ display");
        Assertions.assertEquals(MjRfConversion.DEFAULT_MJ_PER_RF, LocaleUtil.currentMjPerRf(),
                "precondition: this JVM is at the default ratio");
        MessageEnergyOverlay fromServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ,
                true, FIVE_RF_PER_MJ);

        injectLanguage(Map.of("buildcraft.unit.energy.full", "FE_FULL_X", "buildcraft.unit.per_second.full", "%s EACH_SECOND"));
        Assertions.assertEquals("100 FE_FULL_X EACH_SECOND",
                translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString());
    }

    @Test
    public void aServerOnMjDisplayShowsMjEvenToAClientWhoseOwnCopySaysDisplayRf() {
        MessageEnergyOverlay fromServer = mjDisplay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, 4 * MjAPI.MJ);
        injectLanguage(Map.of("buildcraft.unit.mj.full", "MJ_FULL", "buildcraft.unit.per_second.full", "%s EACH_SECOND"));

        BCLibConfig.PowerMode before = BCLibConfig.powerMode.get();
        try {
            BCLibConfig.powerMode.set(BCLibConfig.PowerMode.DISPLAY_RF);
            Assertions.assertTrue(LocaleUtil.isDisplayRf(), "precondition: this JVM's own copy now says DISPLAY_RF");
            Assertions.assertEquals("80.00 MJ_FULL EACH_SECOND",
                    translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString());
        } finally {
            BCLibConfig.powerMode.set(before);
        }
    }

    @Test
    public void anRfFigureIsNeverConvertedWhateverTheServersMode() {
        // An RF pipe's limit is FE/RF natively: the power mode only re-units MJ figures.
        MessageEnergyOverlay onRfServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.RF_FLOW, 40, true,
                FIVE_RF_PER_MJ);
        Assertions.assertEquals(LocaleUtil.localizeRfFlow(40),
                translatable(onRfServer.resolve(false, k -> false)).getArgs()[0].toString());
    }

    @Test
    public void anOutOfRangeRatioFallsBackToTheDefaultInsteadOfDividingByZero() {
        MessageEnergyOverlay corrupt = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ, true, 0);
        MessageEnergyOverlay atDefault = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ, true,
                MjRfConversion.DEFAULT_MJ_PER_RF);

        Assertions.assertEquals(translatable(atDefault.resolve(false, k -> false)).getArgs()[0].toString(),
                translatable(corrupt.resolve(false, k -> false)).getArgs()[0].toString());
    }

    // -- client-side resolution --------------------------------------------

    @Test
    public void anMjFlowIsFormattedByTheClientWithTheClientsOwnLanguage() {
        MessageEnergyOverlay fromServer = mjDisplay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, 4 * MjAPI.MJ);

        injectLanguage(Map.of("buildcraft.unit.mj.full", "MJ_FULL_ONE", "buildcraft.unit.per_second.full", "%s EACH_SECOND_ONE"));
        String shownByFirstClient = translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString();
        injectLanguage(Map.of("buildcraft.unit.mj.full", "MJ_FULL_TWO", "buildcraft.unit.per_second.full", "%s EACH_SECOND_TWO"));
        String shownBySecondClient = translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString();

        // The same payload, resolved against two languages, reads in each of them: nothing was baked when it was built.
        Assertions.assertEquals("80.00 MJ_FULL_ONE EACH_SECOND_ONE", shownByFirstClient);
        Assertions.assertEquals("80.00 MJ_FULL_TWO EACH_SECOND_TWO", shownBySecondClient);
        Assertions.assertEquals(LocaleUtil.localizeMjFlow(4 * MjAPI.MJ), shownBySecondClient,
                "the figure is exactly what every other MJ flow readout in the UI shows");
    }

    @Test
    public void anRfFlowIsFormattedByTheClientsEnergyUnit() {
        MessageEnergyOverlay fromServer = mjDisplay(KEY, MessageEnergyOverlay.Kind.RF_FLOW, 40);

        injectLanguage(Map.of("buildcraft.unit.energy.full", "FE_FULL_X", "buildcraft.unit.per_second.full", "%s EACH_SECOND"));
        String shown = translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString();

        Assertions.assertEquals("800 FE_FULL_X EACH_SECOND", shown);
        Assertions.assertEquals(LocaleUtil.localizeRfFlow(40), shown);
    }

    @Test
    public void theMessageKeyKeepsTheServersWordingAndTakesTheFormattedFigureAsItsOnlyArgument() {
        MessageEnergyOverlay fromServer = mjDisplay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ);

        TranslatableContents contents = translatable(fromServer.resolve(false, k -> false));

        Assertions.assertEquals(KEY, contents.getKey());
        Assertions.assertEquals(1, contents.getArgs().length);
    }

    @Test
    public void theMessageKeyFollowsTheClientsRfNamingToggleWhenASiblingExists() {
        MessageEnergyOverlay fromServer = mjDisplay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ);
        Set<String> hasRfSibling = Set.of(KEY + ".rf");

        Assertions.assertEquals(KEY + ".rf", translatable(fromServer.resolve(true, hasRfSibling::contains)).getKey());
        Assertions.assertEquals(KEY, translatable(fromServer.resolve(false, hasRfSibling::contains)).getKey(),
                "toggle off: the base key");
        Assertions.assertEquals(KEY, translatable(fromServer.resolve(true, k -> false)).getKey(),
                "no sibling in the language: the base key, never a raw .rf key");
    }

    @Test
    public void anOutOfRangeRfAmountClampsInsteadOfWrapping() {
        // localizeRfFlow takes an int; a corrupt or hostile long must not wrap negative.
        MessageEnergyOverlay fromServer = mjDisplay(KEY, MessageEnergyOverlay.Kind.RF_FLOW, Long.MAX_VALUE);

        Assertions.assertEquals(LocaleUtil.localizeRfFlow(Integer.MAX_VALUE),
                translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString());
    }
}
