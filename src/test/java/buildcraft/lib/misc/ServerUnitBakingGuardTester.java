/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/** Keeps unit text out of strings the SERVER builds.
 *
 * <p>{@link LocaleUtil}'s energy/fluid/heat formatters answer from the display preferences — the FE/RF naming toggle,
 * full-vs-short unit names, number separators, the flow display mode — and those live on the CLIENT config spec
 * ({@code useRfNaming} and friends are {@code null} on a dedicated server, so the formatter silently answers with
 * defaults), while the language they read is the server's, not the player's. A message the server formats and then
 * ships therefore shows the wrong unit (or the wrong language) to exactly the players a dedicated server has. The fix
 * is to send data and let the client format it — see {@link buildcraft.lib.net.MessageEnergyOverlay}.
 *
 * <p>Two inputs are the exception and stay the server's: {@code powerMode} (DISPLAY_RF re-units MJ figures as FE/RF)
 * and the {@code mjRfConversionAmount} ratio are COMMON config, back the server's real conversion, and are never
 * synced to a remote client. The payload carries the server's values of both, so the client converts at the rate the
 * server's machines actually use.
 *
 * <p>Two source scans pin the audit that found this, so the next caller cannot reintroduce it unnoticed. */
public class ServerUnitBakingGuardTester {

    /** A call to one of {@link LocaleUtil}'s unit-bearing formatters (fully qualified or not). */
    private static final Pattern UNIT_FORMATTER = Pattern.compile(
            "\\bLocaleUtil\\s*\\.\\s*(?:localizeMj\\w*|localizeRf\\w*|energyUnit|mjUnit|fluidUnit|localizeFluid\\w*"
                    + "|localizeHeat)\\s*\\(");

    /** A call that hands a finished message to a player. */
    private static final Pattern PLAYER_MESSAGE_SENDER = Pattern.compile(
            "\\b(?:sendOverlayMessage|sendSystemMessage|displayClientMessage|sendSuccess|sendFailure)\\s*\\(");

    /** The one class that is the client-side resolution seam: it formats (in {@code resolve}, run by the client's
     *  payload handler) and shows (in {@code handle}) in the same file, by design. */
    private static final String CLIENT_RESOLVER = "buildcraft/lib/net/MessageEnergyOverlay.java";

    /** A class declaration that is a menu: server-side code, whatever package it sits in. A type bound
     *  ({@code C extends AbstractContainerMenu & BCContainer}) does not declare one. */
    private static final Pattern DECLARES_MENU = Pattern.compile(
            "\\bclass\\s+\\w+[^{]*?\\bextends\\s+(?:AbstractContainerMenu|RecipeBookMenu)\\b(?!\\s*&)");

    /** A file that imports Minecraft's client classes, or extends a screen: it cannot even load on a dedicated
     *  server, so it only ever runs where the client config exists. */
    private static final Pattern TOUCHES_MINECRAFT_CLIENT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?net\\.minecraft\\.client\\.|\\bextends\\s+\\w*Screen\\b");

    /** Code that only ever runs in a client JVM, where reading the client config is exactly right: the client
     *  packages and the JEI plugins by their path. A {@code gui} package is not enough on its own: the server-side
     *  menus, widgets and slots live in {@code buildcraft/lib/gui} too, so a file there is client-only only when its
     *  own code says so ({@link #TOUCHES_MINECRAFT_CLIENT}), and anything else in it is scanned like any other.
     *
     * @param code the file's live code ({@link MainSourceSet#codeOnly}) */
    static boolean isClientOnly(String relativePath, String code) {
        String path = "/" + relativePath;
        if (path.contains("/client/") || path.contains("/compat/jei/") || relativePath.equals(CLIENT_RESOLVER)) {
            return true;
        }
        return path.contains("/gui/") && TOUCHES_MINECRAFT_CLIENT.matcher(code).find();
    }

    @Test
    public void aGuiFileIsClientOnlyOnlyWhenItTouchesMinecraftClientCode() {
        String screen = source("package buildcraft.lib.gui;", "import net.minecraft.client.gui.GuiGraphics;",
                "class Ledger {}");
        String menu = source("package buildcraft.lib.gui;", "import net.minecraft.world.inventory.AbstractContainerMenu;",
                "class ContainerFoo extends AbstractContainerMenu {}");
        String bareScreen = source("package buildcraft.lib.gui;", "class GuiFoo extends AbstractContainerScreen<Menu> {}");
        Assertions.assertTrue(isClientOnly("buildcraft/lib/gui/ledger/Ledger.java", screen),
                "a gui class that imports Minecraft client code is a client screen");
        Assertions.assertTrue(isClientOnly("buildcraft/lib/gui/GuiFoo.java", bareScreen),
                "a gui class that extends a Screen is a client screen");
        Assertions.assertFalse(isClientOnly("buildcraft/lib/gui/ContainerFoo.java", menu),
                "a server-side menu under a gui package must be scanned, not exempted by its directory");
        Assertions.assertTrue(isClientOnly("buildcraft/energy/client/gui/ScreenFoo.java", menu),
                "everything under a client package is exempt whatever it imports");
        Assertions.assertTrue(isClientOnly("buildcraft/lib/compat/jei/Label.java", menu), "jei is client only");
        Assertions.assertFalse(isClientOnly("buildcraft/core/item/ItemFoo.java", screen),
                "outside gui, client packages and jei the path alone decides, whatever the file imports");
    }

    private static String source(String... lines) {
        return String.join("\n", lines);
    }

    @Test
    public void aFileThatMessagesAPlayerNeverFormatsUnitsItself() {
        List<String> offenders = new ArrayList<>();
        int senders = 0;
        int guiMenusScanned = 0;
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            String code = MainSourceSet.codeOnly(file.text());
            if (isClientOnly(file.relativePath(), code)) {
                continue;
            }
            if (("/" + file.relativePath()).contains("/gui/") && DECLARES_MENU.matcher(code).find()) {
                guiMenusScanned++;
            }
            if (!PLAYER_MESSAGE_SENDER.matcher(code).find()) {
                continue;
            }
            senders++;
            if (UNIT_FORMATTER.matcher(code).find()) {
                offenders.add(file.relativePath());
            }
        }
        // Liveness: the scan must actually see the message senders, or "no offenders" is vacuous.
        Assertions.assertTrue(senders >= 5, "the scan lost the server-side message senders (saw " + senders + ")");
        // ... and must still reach the server-side menus under lib/gui, or the narrowed gui exemption has quietly
        // grown back to the whole directory.
        Assertions.assertTrue(guiMenusScanned > 0,
                "the scan skips every menu declared in a gui package as client-only again");
        Assertions.assertTrue(offenders.isEmpty(),
                "These files send a message to a player AND call a LocaleUtil unit formatter " + offenders
                        + " — the formatter reads client-only display settings, so on a dedicated server the unit"
                        + " is baked in the server's defaults and language. Send the raw amount to the client"
                        + " instead (MessageUtil.sendEnergyOverlay) and let it format.");
    }

    @Test
    public void theWrenchCycledEnergyMessagesTravelAsData() {
        // The two server-side messages that name an energy figure: the creative engine's output and the iron/diamond
        // pipes' limit. Both must go through the payload, so the client formats them.
        for (String file : new String[] { "buildcraft/core/tile/TileEngineCreative.java",
                "buildcraft/transport/pipe/behaviour/PipeBehaviourLimiter.java" }) {
            Assertions.assertTrue(MainSourceSet.codeOf(file).contains("sendEnergyOverlay("),
                    file + " must send its energy figure with MessageUtil.sendEnergyOverlay");
        }
        // The power mode and MJ/RF ratio are the SERVER's (COMMON config, never synced to a remote client), so the
        // sender must snapshot them into the payload through the factory rather than leave the client to its own copy.
        Assertions.assertTrue(MainSourceSet.codeOf("buildcraft/lib/misc/MessageUtil.java").contains("MessageEnergyOverlay.of("),
                "MessageUtil.sendEnergyOverlay must build the payload with MessageEnergyOverlay.of, which carries the"
                        + " server's powerMode and mjRfConversionAmount");
    }

    /** Every file outside client-only packages that calls a {@link LocaleUtil} unit formatter, with why that is safe.
     *  A new entry is a deliberate classification: the string must be built where the player's own settings are read
     *  (a tooltip the client assembles) or be developer output that is English by design. */
    private static final Map<String, String> AUDITED_NON_CLIENT_CALLERS = new TreeMap<>(Map.of(
            "buildcraft/core/item/ItemFragileFluidContainer.java",
            "item tooltip: appendHoverText is only ever called by the client building a tooltip",
            "buildcraft/transport/item/ItemPipeHolder.java",
            "item tooltip: appendHoverText is only ever called by the client building a tooltip",
            "buildcraft/builders/tile/TileQuarry.java",
            "getDebugInfo: developer F3 text, English labels by design",
            "buildcraft/core/tile/TilePowerConsumerTester.java",
            "getDebugInfo: developer F3 text, English labels by design",
            "buildcraft/factory/tile/TileDistiller_BC8.java",
            "getDebugInfo: developer F3 text, English labels by design",
            "buildcraft/lib/engine/TileEngineBase_BC8.java",
            "getDebugInfo: developer F3 text, English labels by design",
            "buildcraft/transport/pipe/flow/PipeFlowPower.java",
            "getDebugInfo (formatMaxPower): developer F3 text, English labels by design"));

    @Test
    public void everyServerReachableUnitFormatterCallerIsAudited() {
        Map<String, Integer> found = new TreeMap<>();
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            String code = MainSourceSet.codeOnly(file.text());
            if (isClientOnly(file.relativePath(), code)
                    || file.relativePath().equals("buildcraft/lib/misc/LocaleUtil.java")) {
                continue;
            }
            Matcher m = UNIT_FORMATTER.matcher(code);
            int calls = 0;
            while (m.find()) {
                calls++;
            }
            if (calls > 0) {
                found.put(file.relativePath(), calls);
            }
        }

        List<String> unaudited = found.keySet().stream()
                .filter(f -> !AUDITED_NON_CLIENT_CALLERS.containsKey(f)).toList();
        Assertions.assertTrue(unaudited.isEmpty(),
                "These files outside client-only packages call a LocaleUtil unit formatter but are not in"
                        + " AUDITED_NON_CLIENT_CALLERS " + unaudited + " — decide whether the string is built on the"
                        + " client (tooltip, screen) or the server. Server-built strings must send data and let the"
                        + " client format (see MessageEnergyOverlay); otherwise add the file with the reason.");

        List<String> stale = AUDITED_NON_CLIENT_CALLERS.keySet().stream().filter(f -> !found.containsKey(f)).toList();
        Assertions.assertTrue(stale.isEmpty(),
                "AUDITED_NON_CLIENT_CALLERS names files that no longer call a unit formatter (or are gone) " + stale
                        + " — drop them from the audit.");
    }
}
