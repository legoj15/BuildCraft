/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.TestHelper;

/**
 * Keeps BuildCraft on ONE button implementation. A button is either vanilla's own {@code Button}/{@code ImageButton}
 * (plain text, or vanilla's own sprites) or a {@link BCButton} configured through its builder: vanilla's button face
 * with a BuildCraft icon on top. What this rejects is the pattern the unification removed — a screen painting a
 * "button" out of its background texture, hit-testing the mouse itself and playing the click sound by hand, or a
 * screen subclassing a button widget to re-implement face/press handling.
 */
public class ButtonUnificationGuardTester {

    /** Button widget subclasses may only live in the shared button package. */
    private static final String BUTTON_PACKAGE = "buildcraft/lib/gui/button/";

    private static final Pattern EXTENDS_BUTTON =
        Pattern.compile("\\bextends\\s+(AbstractButton|BCButton|Button|ImageButton)\\b");

    /** A screen that plays vanilla's button-click sound itself is hand-rolling a button. */
    private static final Pattern HAND_ROLLED_CLICK = Pattern.compile("\\bUI_BUTTON_CLICK\\b");

    /**
     * Deliberate exceptions, each with the reason it is not a button. Adding one is a design decision: say why
     * vanilla's button face would be wrong there.
     */
    private static final Map<String, String> HAND_ROLLED_CLICK_ALLOWED = Map.of(
        "buildcraft/silicon/gui/GuiGate.java",
        "the trigger-row connectors are part of the gate's wiring diagram: the wire itself is the control, "
            + "there is no button art to replace");

    private static Map<String, List<String>> mainSources() throws IOException {
        Path base = TestHelper.repoRoot().resolve("src/main/java");
        Map<String, List<String>> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                sources.put(base.relativize(p).toString().replace('\\', '/'), Files.readAllLines(p, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    @Test
    public void theScanSeesTheSourceTree() throws IOException {
        Map<String, List<String>> sources = mainSources();
        Assertions.assertTrue(sources.containsKey(BUTTON_PACKAGE + "BCButton.java"), "scan missed BCButton.java");
        Assertions.assertTrue(sources.size() > 500, "scan found only " + sources.size() + " files");
    }

    @Test
    public void onlyTheSharedPackageSubclassesButtonWidgets() throws IOException {
        StringBuilder offenders = new StringBuilder();
        for (Map.Entry<String, List<String>> e : mainSources().entrySet()) {
            if (e.getKey().startsWith(BUTTON_PACKAGE)) {
                continue;
            }
            for (String line : e.getValue()) {
                if (!line.trim().startsWith("//") && !line.trim().startsWith("*") && EXTENDS_BUTTON.matcher(line).find()) {
                    offenders.append("\n  ").append(e.getKey()).append(": ").append(line.trim());
                }
            }
        }
        Assertions.assertEquals("", offenders.toString(),
            "configure a BCButton through BCButton.builder(...) (or move a genuinely new kind of button into "
                + BUTTON_PACKAGE + ") instead of subclassing a button widget in a screen:");
    }

    @Test
    public void noScreenHandRollsAButtonClick() throws IOException {
        StringBuilder offenders = new StringBuilder();
        for (Map.Entry<String, List<String>> e : mainSources().entrySet()) {
            if (HAND_ROLLED_CLICK_ALLOWED.containsKey(e.getKey())) {
                continue;
            }
            for (String line : e.getValue()) {
                if (HAND_ROLLED_CLICK.matcher(line).find()) {
                    offenders.append("\n  ").append(e.getKey()).append(": ").append(line.trim());
                }
            }
        }
        Assertions.assertEquals("", offenders.toString(),
            "a screen plays the button-click sound itself, i.e. it paints and hit-tests its own button — "
                + "use a BCButton widget (vanilla plays the sound):");
    }

    @Test
    public void everyAllowedExceptionStillExists() throws IOException {
        // An exception whose file stopped hand-rolling (or vanished) must be dropped, not left to excuse a newcomer.
        Map<String, List<String>> sources = mainSources();
        for (String allowed : HAND_ROLLED_CLICK_ALLOWED.keySet()) {
            List<String> lines = sources.get(allowed);
            Assertions.assertNotNull(lines, "allowlisted file is gone: " + allowed);
            Assertions.assertTrue(lines.stream().anyMatch(l -> HAND_ROLLED_CLICK.matcher(l).find()),
                "allowlisted file no longer plays the click sound itself; drop it from the allowlist: " + allowed);
        }
    }
}
