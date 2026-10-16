/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import buildcraft.api.registry.IReloadableRegistry.PackType;

import buildcraft.lib.script.SimpleScript.ScriptAction;
import buildcraft.lib.script.SimpleScript.ScriptActionAdd;

/** Pins that an {@code import} consumes exactly its own line. The interpreter used to advance the line cursor once
 *  in the import case and once more inside the line-replace helper, which silently deleted whatever line followed
 *  an import. guide.txt hid it for years because its first import is followed by a blank line; the config book's
 *  import was followed by a real registration ({@code cfg_res "json_insn_format"}) that never ran, so the page it
 *  registers was a dead link target. */
class SimpleScriptImportTest {

    @TempDir
    Path root;

    @Test
    void theLineAfterAnImportStillRuns() throws IOException {
        Path libDir = root.resolve("ns/compat/buildcraft/guide");
        Files.createDirectories(libDir);
        List<String> lib = List.of(
            "~{buildcraft/json/lib}",
            "~args 1",
            "alias mk 1 `add \"x/%0\" \\`{",
            "    \"book\": \"${0}\"",
            "}\\``"
        );
        Files.write(libDir.resolve("lib.txt"), lib, StandardCharsets.UTF_8);
        Path scriptFile = libDir.resolve("main.txt");
        List<String> script = new ArrayList<>(List.of(
            "// Valid file declaration was here",
            "import \"ns:lib\" \"bookA\"",
            "mk \"first\"",
            "mk \"second\""
        ));
        Files.write(scriptFile, script, StandardCharsets.UTF_8);

        ScriptableRegistry<Object> registry = new ScriptableRegistry<>(PackType.RESOURCE_PACK, "buildcraft/guide");
        SimpleScript parsed = new SimpleScript(registry, root, "ns", libDir, scriptFile, List.of(root), script);

        Map<String, String> books = new LinkedHashMap<>();
        for (ScriptAction action : parsed.actions) {
            if (action instanceof ScriptActionAdd add) {
                books.put(add.name.getPath(), add.json.get("book").getAsString());
            }
        }
        assertNotNull(books.get("x/first"), "the registration on the line right after the import was dropped: " + books);
        assertNotNull(books.get("x/second"), "the second registration was dropped: " + books);
        assertEquals("bookA", books.get("x/first"), "import arguments still substitute ${0}");
        assertEquals(2, books.size(), "exactly the two registrations: " + books);
    }
}
