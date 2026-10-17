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
import buildcraft.lib.misc.LocaleUtil;

/** The wire format and client-side resolution of {@link MessageEnergyOverlay}.
 *
 * <p>The server used to format "Switched to 80 Minecraft Joules per second limit" itself with {@code LocaleUtil} — whose
 * display settings (FE/RF naming, full unit names, separators, flow mode) are client config that reads as unset on a
 * dedicated server, and whose language is the server's. The payload carries the raw amount instead and the client
 * formats it against its own settings and language; this class pins both halves of that. */
public class MessageEnergyOverlayTest {

    private static final String KEY = "chat.engine.creative.mode";

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
            MessageEnergyOverlay sent = new MessageEnergyOverlay("chat.pipe.iron.mode", kind, 123_456_789_012L);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            MessageEnergyOverlay.STREAM_CODEC.encode(buf, sent);

            Assertions.assertEquals(sent, MessageEnergyOverlay.STREAM_CODEC.decode(buf), kind + " must round-trip");
            Assertions.assertEquals(0, buf.readableBytes(), kind + ": the decoder must consume the whole payload");
        }
    }

    @Test
    public void thePayloadIsDataNotText() {
        // The server fills in these three fields and nothing else: a key to translate, which unit family the figure
        // is in, and the raw amount. There is no String a server could bake a unit or a number format into.
        var components = MessageEnergyOverlay.class.getRecordComponents();
        Assertions.assertEquals(3, components.length);
        Assertions.assertEquals(String.class, components[0].getType());
        Assertions.assertEquals(MessageEnergyOverlay.Kind.class, components[1].getType());
        Assertions.assertEquals(long.class, components[2].getType());
    }

    // -- client-side resolution --------------------------------------------

    @Test
    public void anMjFlowIsFormattedByTheClientWithTheClientsOwnLanguage() {
        MessageEnergyOverlay fromServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, 4 * MjAPI.MJ);

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
        MessageEnergyOverlay fromServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.RF_FLOW, 40);

        injectLanguage(Map.of("buildcraft.unit.energy.full", "FE_FULL_X", "buildcraft.unit.per_second.full", "%s EACH_SECOND"));
        String shown = translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString();

        Assertions.assertEquals("800 FE_FULL_X EACH_SECOND", shown);
        Assertions.assertEquals(LocaleUtil.localizeRfFlow(40), shown);
    }

    @Test
    public void theMessageKeyKeepsTheServersWordingAndTakesTheFormattedFigureAsItsOnlyArgument() {
        MessageEnergyOverlay fromServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ);

        TranslatableContents contents = translatable(fromServer.resolve(false, k -> false));

        Assertions.assertEquals(KEY, contents.getKey());
        Assertions.assertEquals(1, contents.getArgs().length);
    }

    @Test
    public void theMessageKeyFollowsTheClientsRfNamingToggleWhenASiblingExists() {
        MessageEnergyOverlay fromServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.MJ_FLOW, MjAPI.MJ);
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
        MessageEnergyOverlay fromServer = new MessageEnergyOverlay(KEY, MessageEnergyOverlay.Kind.RF_FLOW, Long.MAX_VALUE);

        Assertions.assertEquals(LocaleUtil.localizeRfFlow(Integer.MAX_VALUE),
                translatable(fromServer.resolve(false, k -> false)).getArgs()[0].toString());
    }
}
