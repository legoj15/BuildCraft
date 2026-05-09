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
