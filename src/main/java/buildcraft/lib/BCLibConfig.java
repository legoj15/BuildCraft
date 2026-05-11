/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib;

import net.neoforged.neoforge.common.ModConfigSpec;

import buildcraft.api.mj.IMjToRfStatus;
import buildcraft.api.mj.MjRfConversion;

/**
 * Configuration file for lib. Uses NeoForge ModConfigSpec.
 */
public class BCLibConfig {

    public static ModConfigSpec.EnumValue<PowerMode> powerMode;
    public static ModConfigSpec.DoubleValue mjRfConversionAmount;

    public static ModConfigSpec.EnumValue<ThousandsSeparator> thousandsSeparator;
    public static ModConfigSpec.EnumValue<DecimalSeparator> decimalSeparator;
    public static ModConfigSpec.BooleanValue abbreviateLargeNumbers;
    public static ModConfigSpec.EnumValue<FlowDisplay> flowDisplay;
    public static ModConfigSpec.EnumValue<ColorBlindMode> colorBlindMode;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        powerMode = builder
                .comment("Power mode. Options are MJ_ONLY, MJ_AUTOCONVERT_RF, DISPLAY_RF")
                .defineEnum("powerMode", PowerMode.MJ_ONLY);

        mjRfConversionAmount = builder
                .comment("Conversion ratio for MJ <-> RF if autoconvert is enabled (MJ per RF)")
                .defineInRange("mjRfConversionAmount", 0.1, 0.0001, 0.2);
    }

    public static void buildDisplay(ModConfigSpec.Builder builder) {
        thousandsSeparator = builder
                .comment(
                        "Thousands grouping for large numbers in machine readouts and JEI labels.",
                        "COMMA: 1,000   DOT: 1.000   SPACE: 1 000   NONE: 1000."
                )
                .defineEnum("thousandsSeparator", ThousandsSeparator.COMMA);

        decimalSeparator = builder
                .comment(
                        "Separator before the decimal portion of fractional readouts (e.g. MJ values, heat).",
                        "DOT: 1.5   COMMA: 1,5."
                )
                .defineEnum("decimalSeparator", DecimalSeparator.DOT);

        abbreviateLargeNumbers = builder
                .comment(
                        "If true, collapse FE/RF readouts at or above 1,000 into compact suffixed form:",
                        "1,234 -> 1.2k, 1,500,000 -> 1.5M, 2,500,000,000 -> 2.5G, 4,000,000,000,000 -> 4.0T.",
                        "Suffixes (k = thousand, M = million, G = billion, T = trillion) honour the configured",
                        "decimal separator. MJ readouts and JEI category power labels stay unabbreviated since",
                        "those values are either small in normal play or load-bearing precision. Default false."
                )
                .define("abbreviateLargeNumbers", false);

        flowDisplay = builder
                .comment(
                        "How flow rates render in machine readouts (engine ledgers, conversion-help bodies,",
                        "kinesis-pipe hovers, the silicon laser ledger).",
                        "PER_SECOND: 100.00 MJ/s   (default, matches 1.12.2's display convention)",
                        "PER_TICK:   5.00 MJ/t    (same figure as the underlying API gives)",
                        "BOTH:       100.00 MJ/s (5.00 MJ/t)"
                )
                .defineEnum("flowDisplay", FlowDisplay.PER_SECOND);

        colorBlindMode = builder
                .comment(
                        "Whether BuildCraft swaps to colourblind-friendly texture variants where available:",
                        "the diamond pipe filter GUI shows numbered slot labels, and diamond item/fluid",
                        "pipes show numbered west-face textures so each filter row is identifiable without",
                        "colour vision. Restored from 1.12.2.",
                        "AUTO: follow Minecraft's Options → Accessibility → High Contrast toggle (default).",
                        "      The MC option triggers a resource pack reload when toggled, which restitches",
                        "      the block atlas — BuildCraft picks up the change on the next chunk re-bake.",
                        "OFF:  force off regardless of MC's accessibility setting.",
                        "ON:   force on regardless of MC's accessibility setting."
                )
                .defineEnum("colorBlindMode", ColorBlindMode.AUTO);
    }

    /** Thousands grouping separator used by {@link buildcraft.lib.misc.LocaleUtil}'s number formatters. */
    public enum ThousandsSeparator {
        COMMA(','),
        DOT('.'),
        SPACE(' '),
        NONE('\0');

        public final char ch;

        ThousandsSeparator(char ch) {
            this.ch = ch;
        }
    }

    /** Separator placed before the decimal portion of a fractional number. */
    public enum DecimalSeparator {
        DOT('.'),
        COMMA(',');

        public final char ch;

        DecimalSeparator(char ch) {
            this.ch = ch;
        }
    }

    /** How {@link buildcraft.lib.misc.LocaleUtil#localizeMjFlow(long)} and
     *  {@link buildcraft.lib.misc.LocaleUtil#localizeRfFlow(int)} render flow rates. */
    public enum FlowDisplay {
        PER_SECOND,
        PER_TICK,
        BOTH
    }

    /** Tri-state toggle for colourblind-friendly texture variants. {@code AUTO} (default) reads
     *  Minecraft's Options → Accessibility → High Contrast and mirrors that — keeps a player who
     *  has set their MC accessibility preference from having to also flip a separate BuildCraft
     *  knob. {@code ON} / {@code OFF} override regardless of the MC option, for players who want
     *  the BuildCraft variants without enabling the broader MC high-contrast resource pack
     *  (or vice versa). The effective state is resolved by
     *  {@link buildcraft.lib.client.ColorBlindUtil#isActive()} on the client side. */
    public enum ColorBlindMode {
        AUTO,
        OFF,
        ON
    }

    public enum PowerMode {
        /** MJ <-> RF conversion disabled, all machines require MJ exclusively to operate. */
        MJ_ONLY(false),
        /** MJ <-> RF conversion enabled, machines accept both MJ and RF. */
        MJ_AUTOCONVERT_RF(true),
        /** MJ <-> RF conversion enabled, machines accept both MJ and RF. Additionally machines will display power
         * amounts in RF rather than MJ. */
        DISPLAY_RF(true);

        public final boolean autoconvert;

        PowerMode(boolean autoconvert) {
            this.autoconvert = autoconvert;
        }
    }

    public static final class MjToRfStatus implements IMjToRfStatus {

        @Override
        public MjRfConversion getConversion() {
            return MjRfConversion.createParsed(BCLibConfig.mjRfConversionAmount.get());
        }

        @Override
        public boolean isAutoconvertEnabled() {
            return powerMode.get().autoconvert;
        }
    }
}
