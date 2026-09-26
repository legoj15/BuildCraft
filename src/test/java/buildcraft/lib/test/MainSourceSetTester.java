/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Pins {@link MainSourceSet#codeOnly}, which the code-inspecting guards trust to show only live code. */
public class MainSourceSetTester {

    @Test
    public void commentsAndLiteralInteriorsAreBlankedButCodeAndLinesSurvive() {
        String src = String.join("\n",
            "int a = 1; // LOCATION_BLOCKS in prose",
            "/* LOCATION_BLOCKS in a block",
            "   comment */ int b = 2;",
            "String s = \"LOCATION_BLOCKS \\\" still inside\"; char q = '\"'; char e = '\\'';",
            "String t = \"\"\"",
            "    import foo.Bar; \\\"\"\" still inside",
            "    \"\"\"; int c = 3;",
            "Object d = LOCATION_BLOCKS;");
        String code = MainSourceSet.codeOnly(src);

        Assertions.assertEquals(src.split("\n", -1).length, code.split("\n", -1).length, "line count must be kept");
        Assertions.assertEquals(1, code.split("LOCATION_BLOCKS", -1).length - 1,
            "only the live reference on the last line may remain:\n" + code);
        Assertions.assertFalse(code.contains("import"), "text-block contents are not code:\n" + code);
        for (String live : new String[] { "int a = 1;", "int b = 2;", "char q = '", "int c = 3;", "Object d" }) {
            Assertions.assertTrue(code.contains(live), "live code lost: " + live + "\n" + code);
        }
    }
}
