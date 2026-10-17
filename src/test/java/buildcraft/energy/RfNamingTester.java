/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.common.ModConfigSpec;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.lib.test.MainSourceSet;

/** The FE/RF naming toggle ({@code useRfNaming}) lives on the CLIENT config spec, so it only exists in a client JVM:
 *  on a dedicated server the field is {@code null}, and in a client JVM it is unreadable until the client config has
 *  loaded. This pins the contract that follows from that: the SERVER never bakes an RF key into anything it sends (a
 *  server menu title is always the base key), and the CLIENT maps base keys to their {@code .rf} sibling at display
 *  time, only when the toggle is on and the sibling actually exists in the language. */
public class RfNamingTester extends VanillaSetupBaseTester {

    private static final String ENGINE_KEY = "block.buildcraftunofficial.engine_rf";
    private static final Predicate<String> HAS_ENGINE_RF = Set.of(ENGINE_KEY + ".rf")::contains;
    private static final Predicate<String> HAS_NOTHING = k -> false;

    private ModConfigSpec.BooleanValue original;

    @BeforeEach
    public void rememberToggle() {
        original = BCEnergyConfig.useRfNaming;
    }

    @AfterEach
    public void restoreToggle() {
        // A static field shared with every other test in the JVM: never leak an injected value.
        BCEnergyConfig.useRfNaming = original;
    }

    /** A toggle whose config has never loaded, exactly what a client JVM holds before its CLIENT config is read. */
    private static ModConfigSpec.BooleanValue unloadedToggle() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ModConfigSpec.BooleanValue value = builder.define("useRfNaming", false);
        builder.build();
        return value;
    }

    // -- rfFeKey / rfNamingEnabled: the server path ------------------------

    @Test
    public void serverPath_noToggle_yieldsBaseKeyAndDoesNotCrash() {
        BCEnergyConfig.useRfNaming = null; // dedicated server: BCUnifiedClientConfig is never initialised
        Assertions.assertFalse(BCEnergyConfig.rfNamingEnabled());
        Assertions.assertEquals(ENGINE_KEY, BCEnergyConfig.rfFeKey(ENGINE_KEY));
    }

    @Test
    public void toggleNotYetLoaded_yieldsBaseKeyInsteadOfThrowing() {
        BCEnergyConfig.useRfNaming = unloadedToggle();
        Assertions.assertFalse(BCEnergyConfig.rfNamingEnabled());
        Assertions.assertEquals(ENGINE_KEY, BCEnergyConfig.rfFeKey(ENGINE_KEY),
                "reading the client toggle before its config loads must fall back to FE wording, not throw");
    }

    @Test
    public void engineFeMenuTitle_isAlwaysTheBaseKey_serverNeverConsultsTheClientToggle() {
        // The menu title is built SERVER-side and sent to the client. If it consulted the client-only toggle it
        // would bake the wrong wording (and on a client JVM with an unloaded config, throw). It must be the base
        // key unconditionally; the client screen maps it (see clientMenuTitle below).
        BCEnergyConfig.useRfNaming = unloadedToggle();
        TileEngineFE tile = new TileEngineFE(BlockPos.ZERO, BCEnergyBlocks.ENGINE_FE.get().defaultBlockState());
        Component title = tile.getDisplayName();
        Assertions.assertInstanceOf(TranslatableContents.class, title.getContents());
        Assertions.assertEquals(ENGINE_KEY, ((TranslatableContents) title.getContents()).getKey());
    }

    // -- rfFeKey / rfNamedKey: key selection -------------------------------

    @Test
    public void rfFeKey_explicitToggle_selectsTheRfSibling() {
        Assertions.assertEquals(ENGINE_KEY + ".rf", BCEnergyConfig.rfFeKey(ENGINE_KEY, true));
        Assertions.assertEquals(ENGINE_KEY, BCEnergyConfig.rfFeKey(ENGINE_KEY, false));
    }

    @Test
    public void rfNamedKey_picksTheSiblingOnlyWhenToggleOnAndSiblingExists() {
        String upgrades = "buildcraft.help.dynamo.upgrades";
        Assertions.assertEquals(ENGINE_KEY + ".rf", BCEnergyConfig.rfNamedKey(ENGINE_KEY, true, HAS_ENGINE_RF));
        Assertions.assertEquals(ENGINE_KEY, BCEnergyConfig.rfNamedKey(ENGINE_KEY, false, HAS_ENGINE_RF),
                "toggle off: base key even though the sibling exists");
        Assertions.assertEquals(upgrades, BCEnergyConfig.rfNamedKey(upgrades, true, HAS_ENGINE_RF),
                "a key with no .rf sibling keeps its base wording instead of rendering as a raw key");
        // The point of the guard: the day a sibling is added to the lang file, the same call picks it up.
        Predicate<String> hasUpgradesRf = Set.of(upgrades + ".rf")::contains;
        Assertions.assertEquals(upgrades + ".rf", BCEnergyConfig.rfNamedKey(upgrades, true, hasUpgradesRf));
    }

    // -- clientMenuTitle: the client-side resolution -----------------------

    @Test
    public void clientMenuTitle_toggleOn_mapsServerBaseKeyToTheRfKey() {
        Component fromServer = Component.translatable(ENGINE_KEY);
        Component shown = BCEnergyConfig.clientMenuTitle(fromServer, true, HAS_ENGINE_RF);
        Assertions.assertEquals(ENGINE_KEY + ".rf", ((TranslatableContents) shown.getContents()).getKey());
    }

    @Test
    public void clientMenuTitle_toggleOff_leavesTheTitleAlone() {
        Component fromServer = Component.translatable(ENGINE_KEY);
        Assertions.assertSame(fromServer, BCEnergyConfig.clientMenuTitle(fromServer, false, HAS_ENGINE_RF));
    }

    @Test
    public void clientMenuTitle_missingSibling_leavesTheTitleAlone() {
        Component fromServer = Component.translatable(ENGINE_KEY);
        Assertions.assertSame(fromServer, BCEnergyConfig.clientMenuTitle(fromServer, true, HAS_NOTHING));
    }

    @Test
    public void clientMenuTitle_nonTranslatableTitle_isUntouched() {
        Component literal = Component.literal("Custom Name");
        Assertions.assertSame(literal, BCEnergyConfig.clientMenuTitle(literal, true, HAS_ENGINE_RF));
    }

    @Test
    public void clientMenuTitle_noToggleOnThisJvm_isAServerSafeNoOp() {
        BCEnergyConfig.useRfNaming = null;
        Component fromServer = Component.translatable(ENGINE_KEY);
        Assertions.assertSame(fromServer, BCEnergyConfig.clientMenuTitle(fromServer));
    }

    // -- source guards: the screens must go through the shared helper ------

    private static String source(String relativePath) {
        return MainSourceSet.javaFiles().stream()
                .filter(f -> f.relativePath().equals(relativePath))
                .map(MainSourceSet.SourceFile::text)
                .findFirst()
                .orElseThrow(() -> new AssertionError("this node did not compile " + relativePath));
    }

    @Test
    public void energyScreens_helpInfoKeysAllGoThroughRfNamedKey() {
        // ScreenDynamoMJ once built its upgrade ElementHelpInfo from bare literal keys, so a future .rf sibling
        // could never be picked up. Every key handed to an ElementHelpInfo in the FE screens must be wrapped.
        Pattern wrapped = Pattern.compile("(?:[\\w.]*\\.)?rfNamedKey\\(\"[^\"]*\"\\)");
        Pattern ctor = Pattern.compile("(?:new ElementHelpInfo|ElementHelpInfo\\s*\\.preTranslated)\\s*\\(");
        for (String file : new String[] {
                "buildcraft/energy/client/gui/ScreenDynamoMJ.java",
                "buildcraft/energy/client/gui/ScreenEngineFE.java" }) {
            String text = source(file);
            Matcher m = ctor.matcher(text);
            int found = 0;
            while (m.find()) {
                found++;
                int end = text.indexOf(';', m.end());
                String args = wrapped.matcher(text.substring(m.end(), end)).replaceAll("");
                Assertions.assertFalse(args.contains("\"buildcraft."),
                        file + ": ElementHelpInfo built from a bare literal key (not via rfNamedKey): " + args);
            }
            Assertions.assertTrue(found >= 2, file + ": expected its help elements to be found, saw " + found);
        }
    }

    @Test
    public void hasGuardedRfLookup_isDefinedOnce() {
        // Four hand-rolled copies of "rfFeKey + Language.has" had drifted (one screen skipped the guard entirely).
        // BCEnergyConfig.rfNamedKey is the only definition; everyone else calls it.
        Pattern copy = Pattern.compile("static\\s+String\\s+rfNamed\\s*\\(");
        for (MainSourceSet.SourceFile f : MainSourceSet.javaFiles()) {
            Assertions.assertFalse(copy.matcher(f.text()).find(),
                    f.relativePath() + " re-implements the .rf key lookup; call BCEnergyConfig.rfNamedKey");
        }
    }
}
