/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;
import buildcraft.lib.test.MainSourceSet.SourceFile;

/** The guide book must not depend on a mod's JEI compat package. The guide's recipe lookups share the robotics table
 *  collectors with the JEI categories, so those collectors (and the identity helper that keeps boards and robots
 *  apart) live in {@code buildcraft.robotics.recipe}; {@code buildcraft.robotics.compat.jei} holds only what talks to
 *  JEI. This pins both halves: nothing under a {@code client/guide} directory touches a {@code compat.jei} package,
 *  and nothing outside the robotics JEI package uses it either.
 *
 *  <p>Reads this node's compiled view of the sources ({@link MainSourceSet}) with comments and string literals
 *  blanked, so a Javadoc that merely names the JEI package is not a use of it. */
class GuideJeiIndependenceTest {

    private static final String JEI_PACKAGE_MARKER = "compat.jei.";
    private static final String ROBOTICS_JEI_DIR = "buildcraft/robotics/compat/jei/";

    @Test
    void guideSourcesDoNotUseAnyCompatJeiPackage() {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (SourceFile file : MainSourceSet.javaFiles()) {
            if (!file.relativePath().contains("/client/guide/")) {
                continue;
            }
            scanned++;
            if (MainSourceSet.codeOnly(file.text()).contains(JEI_PACKAGE_MARKER)) {
                offenders.add(file.relativePath());
            }
        }
        assertTrue(scanned > 50, "expected the guide's source files, scanned " + scanned);
        assertTrue(offenders.isEmpty(), "guide code depends on a compat.jei package:\n" + String.join("\n", offenders));
    }

    @Test
    void nothingOutsideTheRoboticsJeiPackageUsesIt() {
        List<String> offenders = new ArrayList<>();
        boolean jeiPackageExists = false;
        for (SourceFile file : MainSourceSet.javaFiles()) {
            if (file.relativePath().startsWith(ROBOTICS_JEI_DIR)) {
                jeiPackageExists = true;
                continue;
            }
            if (MainSourceSet.codeOnly(file.text()).contains("robotics." + JEI_PACKAGE_MARKER)) {
                offenders.add(file.relativePath());
            }
        }
        assertTrue(jeiPackageExists, "buildcraft.robotics.compat.jei is gone; update this test with it");
        assertTrue(offenders.isEmpty(), "code outside buildcraft.robotics.compat.jei uses it:\n"
            + String.join("\n", offenders));
    }
}
