/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the GUI state persistence pipeline: in-memory updates flush to JSON on
 *  {@link GuiPropertyBoolean#set} and re-loading the file restores the value. Protects against
 *  regressions like the one between 1.12.2 → 26.1 where ledger open/closed state was reset every
 *  time a GUI was reopened. */
public class GuiConfigManagerTester {

    @BeforeEach
    public void resetState() {
        GuiConfigManager.resetForTesting();
    }

    @AfterEach
    public void cleanupState() {
        GuiConfigManager.resetForTesting();
    }

    /** Round-trip: change a property → assert disk has the new value → reset memory →
     *  re-init from the same path → property reads the persisted value. */
    @Test
    public void persistsBooleanAcrossReload(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("gui_state.json");

        GuiConfigManager.init(file);
        GuiPropertyBoolean prop = GuiConfigManager.getOrAddBoolean("GuiFoo", "LedgerBar.is_open", false);
        Assertions.assertFalse(prop.get(), "fresh property should take its default");
        Assertions.assertFalse(Files.exists(file), "no file should exist until a value actually changes");

        prop.set(true);
        Assertions.assertTrue(prop.get());
        Assertions.assertTrue(Files.exists(file), "set() should flush to disk synchronously");

        String written = Files.readString(file);
        Assertions.assertTrue(written.contains("GuiFoo"), "GUI id should appear in file: " + written);
        Assertions.assertTrue(written.contains("LedgerBar.is_open"), "property name should appear in file: " + written);
        Assertions.assertTrue(written.contains("true"), "true value should appear in file: " + written);

        // Simulate MC restart: clear in-memory state, re-init from the same path.
        GuiConfigManager.resetForTesting();
        GuiConfigManager.init(file);
        GuiPropertyBoolean restored = GuiConfigManager.getOrAddBoolean("GuiFoo", "LedgerBar.is_open", false);
        Assertions.assertTrue(restored.get(), "restored value should be true (was set before restart)");

        // Unrelated property in the same GUI takes its default — no cross-contamination.
        GuiPropertyBoolean other = GuiConfigManager.getOrAddBoolean("GuiFoo", "LedgerHelp.is_open", false);
        Assertions.assertFalse(other.get());
    }

    /** Setting the same value twice should not rewrite the file (no-op short-circuit in set()). */
    @Test
    public void unchangedSetDoesNotTriggerWrite(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("gui_state.json");
        GuiConfigManager.init(file);
        GuiPropertyBoolean prop = GuiConfigManager.getOrAddBoolean("GuiFoo", "LedgerBar.is_open", false);

        prop.set(false); // same as default
        Assertions.assertFalse(Files.exists(file), "no-op set should not write the file");

        prop.set(true);
        Assertions.assertTrue(Files.exists(file));
        long firstMtime = Files.getLastModifiedTime(file).toMillis();

        // Pause so any second write would produce a distinguishable mtime, then assert no rewrite.
        Thread.sleep(10);
        prop.set(true);
        long secondMtime = Files.getLastModifiedTime(file).toMillis();
        Assertions.assertEquals(firstMtime, secondMtime, "redundant set(true) should not rewrite the file");
    }

    /** If init is never called, the manager still acts as a pure in-memory map without IO errors —
     *  important for headless test contexts and for graceful degradation if the lifecycle hook
     *  somehow fails to fire at mod load. */
    @Test
    public void inMemoryFallbackWhenNotInitialised() throws Exception {
        // No init() call.
        GuiPropertyBoolean prop = GuiConfigManager.getOrAddBoolean("GuiFoo", "LedgerBar.is_open", true);
        Assertions.assertTrue(prop.get(), "should take the default");
        prop.set(false); // must not throw, must not attempt disk write
        Assertions.assertFalse(prop.get());
    }

    /** Malformed JSON on disk should not crash init — the manager should log and continue
     *  with an empty in-memory map so the user can keep playing. */
    @Test
    public void malformedFileDoesNotCrashInit(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gui_state.json");
        Files.writeString(file, "{ this is not valid json");

        Assertions.assertDoesNotThrow(() -> GuiConfigManager.init(file));
        GuiPropertyBoolean prop = GuiConfigManager.getOrAddBoolean("GuiFoo", "LedgerBar.is_open", false);
        Assertions.assertFalse(prop.get(), "manager should fall back to defaults after a parse failure");
    }

}
