/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * Keeps BuildCraft on ONE button implementation. A button is either vanilla's own {@code Button}/{@code ImageButton}
 * (plain text, or vanilla's own sprites) or a {@link BCButton} configured through its builder: vanilla's button face
 * with a BuildCraft icon on top, or with a {@link ButtonImage} drawn in place of the face where the art is the whole
 * button (the guide book). A source scan cannot prove the absence of every hand-painted hotspot; it rejects
 * the three fingerprints the unification removed: a screen playing the click sound by hand, a hover-variant
 * {@code GuiIcon} swapped on mouse-over, and a button widget subclassed outside the shared package.
 * <p>
 * The scan reads this node's own view of the main sources ({@link MainSourceSet}) with comments and literal contents
 * blanked, so prose, strings and Stonecutter's commented-out branches for other lines never match.
 */
public class ButtonUnificationGuardTester {

    /** Button widget subclasses may only live in the shared button package. */
    private static final String BUTTON_PACKAGE = "buildcraft/lib/gui/button/";

    private static final Pattern EXTENDS_BUTTON =
        Pattern.compile("\\bextends\\s+(AbstractButton|BCButton|Button|ImageButton)\\b");

    /**
     * A {@code GuiIcon} with a hover variant ({@code FOO_HOVERED}) is a button painted by its screen: the screen
     * swaps the art on {@code contains(mouse)} and hit-tests the click itself. Whole-art buttons are a
     * {@link BCButton} built with {@code .art(ButtonImage)}; the hover art is the image's highlighted sprite.
     */
    private static final Pattern HOVER_VARIANT_ICON = Pattern.compile("\\bGuiIcon\\s+\\w+_HOVER(ED)?\\b");

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

    /** A main source file as this node compiled it: its lines, and the same lines with comments and literal
     *  contents blanked ({@link MainSourceSet#codeOnly}). Line {@code i} of both lists is the same source line. */
    private record Source(List<String> lines, List<String> code) {}

    /** This node's main sources, keyed by path relative to the source root. */
    private static Map<String, Source> mainSources() {
        Map<String, Source> sources = new TreeMap<>();
        for (MainSourceSet.SourceFile f : MainSourceSet.javaFiles()) {
            sources.put(f.relativePath(),
                new Source(f.text().lines().toList(), MainSourceSet.codeOnly(f.text()).lines().toList()));
        }
        return sources;
    }

    /** Every line of {@code src} whose live code matches {@code pattern}, as {@code "\n  path: line"} entries. */
    private static String matches(String path, Source src, Pattern pattern) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < src.code().size(); i++) {
            if (pattern.matcher(src.code().get(i)).find()) {
                out.append("\n  ").append(path).append(": ").append(src.lines().get(i).trim());
            }
        }
        return out.toString();
    }

    @Test
    public void theScanSeesTheSourceTree() {
        Map<String, Source> sources = mainSources();
        Assertions.assertTrue(sources.containsKey(BUTTON_PACKAGE + "BCButton.java"), "scan missed BCButton.java");
        Assertions.assertTrue(sources.size() > 500, "scan found only " + sources.size() + " files");
    }

    @Test
    public void onlyTheSharedPackageSubclassesButtonWidgets() {
        StringBuilder offenders = new StringBuilder();
        for (Map.Entry<String, Source> e : mainSources().entrySet()) {
            if (!e.getKey().startsWith(BUTTON_PACKAGE)) {
                offenders.append(matches(e.getKey(), e.getValue(), EXTENDS_BUTTON));
            }
        }
        Assertions.assertEquals("", offenders.toString(),
            "configure a BCButton through BCButton.builder(...) (or move a genuinely new kind of button into "
                + BUTTON_PACKAGE + ") instead of subclassing a button widget in a screen:");
    }

    @Test
    public void noScreenHandRollsAButtonClick() {
        StringBuilder offenders = new StringBuilder();
        for (Map.Entry<String, Source> e : mainSources().entrySet()) {
            if (!HAND_ROLLED_CLICK_ALLOWED.containsKey(e.getKey())) {
                offenders.append(matches(e.getKey(), e.getValue(), HAND_ROLLED_CLICK));
            }
        }
        Assertions.assertEquals("", offenders.toString(),
            "a screen plays the button-click sound itself, i.e. it paints and hit-tests its own button — "
                + "use a BCButton widget (vanilla plays the sound):");
    }

    @Test
    public void noScreenPaintsHoverVariantButtons() {
        StringBuilder offenders = new StringBuilder();
        for (Map.Entry<String, Source> e : mainSources().entrySet()) {
            offenders.append(matches(e.getKey(), e.getValue(), HOVER_VARIANT_ICON));
        }
        Assertions.assertEquals("", offenders.toString(),
            "a hover-variant GuiIcon means the screen paints and hit-tests its own button — use "
                + "BCButton.builder(x, y, ButtonImage) (whole-button art) or BCButton.builder(...) instead:");
    }

    @Test
    public void everyAllowedExceptionStillExists() {
        // An exception whose file stopped hand-rolling (or vanished) must be dropped, not left to excuse a newcomer.
        Map<String, Source> sources = mainSources();
        for (String allowed : HAND_ROLLED_CLICK_ALLOWED.keySet()) {
            Source src = sources.get(allowed);
            Assertions.assertNotNull(src, "allowlisted file is gone: " + allowed);
            Assertions.assertFalse(matches(allowed, src, HAND_ROLLED_CLICK).isEmpty(),
                "allowlisted file no longer plays the click sound itself; drop it from the allowlist: " + allowed);
        }
    }
}
