/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import buildcraft.api.core.BCLog;

/** Persists GUI element state (currently: which ledgers are open) across MC restarts.
 *  Modern port of the 1.12.2 GuiConfigManager — simplified to boolean-only properties since
 *  no other type was wired up downstream.
 *
 *  <p>Lifecycle: call {@link #init(Path)} once at client startup with the target JSON path;
 *  the file is loaded immediately if it exists, and rewritten on every {@link #markDirty()}.
 *  If {@code init} is never called the manager still works as an in-memory map (no disk IO),
 *  which keeps unit tests and headless server contexts safe. */
public class GuiConfigManager {
    private static final Map<String, GuiConfigSet> properties = new TreeMap<>();
    private static Path configFile = null;

    /** Bind a JSON file path and load existing state from it (if present). */
    public static void init(Path file) {
        configFile = file;
        loadFromConfigFile();
    }

    /** Live boolean property for {@code (guiId, name)}. Creates a default-valued entry on first
     *  access; the returned reference stays valid for the lifetime of the JVM. Read with
     *  {@link GuiPropertyBoolean#get()}, write with {@link GuiPropertyBoolean#set(boolean)}
     *  (writes auto-trigger {@link #markDirty()}). */
    public static GuiPropertyBoolean getOrAddBoolean(String guiId, String name, boolean defaultValue) {
        GuiConfigSet set = properties.computeIfAbsent(guiId, id -> new GuiConfigSet());
        return set.getOrAddBoolean(name, defaultValue);
    }

    /** Called by {@link GuiPropertyBoolean#set} when a value changes. Writes synchronously —
     *  ledger clicks are user-paced so the small file write completes well within a frame. */
    static void markDirty() {
        if (configFile == null) return;
        try {
            Files.createDirectories(configFile.getParent());
            JsonObject json = writeToJson();
            String text = new GsonBuilder().setPrettyPrinting().create().toJson(json);
            Files.writeString(configFile, text, StandardCharsets.UTF_8);
        } catch (IOException io) {
            BCLog.logger.warn("[lib.gui.cfg] Failed to write gui state file: " + io.getMessage());
        }
    }

    private static void loadFromConfigFile() {
        if (configFile == null || !Files.exists(configFile)) return;
        String text;
        try {
            text = Files.readString(configFile, StandardCharsets.UTF_8);
        } catch (IOException io) {
            BCLog.logger.warn("[lib.gui.cfg] Failed to read gui state file: " + io.getMessage());
            return;
        }
        try {
            JsonObject json = new Gson().fromJson(text, JsonObject.class);
            if (json == null) return;
            readFromJson(json);
        } catch (JsonSyntaxException | ClassCastException ex) {
            // Malformed file — log and continue with empty state. User can delete to reset.
            BCLog.logger.warn("[lib.gui.cfg] Malformed gui state file (delete to reset): " + ex.getMessage());
        }
    }

    private static JsonObject writeToJson() {
        JsonObject json = new JsonObject();
        for (Entry<String, GuiConfigSet> entry : properties.entrySet()) {
            json.add(entry.getKey(), entry.getValue().writeToJson());
        }
        return json;
    }

    private static void readFromJson(JsonObject json) {
        for (Entry<String, JsonElement> entry : json.entrySet()) {
            String guiId = entry.getKey();
            JsonElement elem = entry.getValue();
            if (!elem.isJsonObject()) {
                BCLog.logger.warn("[lib.gui.cfg] Non-object entry for '" + guiId + "', skipping");
                continue;
            }
            GuiConfigSet set = properties.computeIfAbsent(guiId, k -> new GuiConfigSet());
            set.readFromJson(elem.getAsJsonObject());
        }
    }

    /** Reset in-memory state and unbind the config file. For tests only. */
    static void resetForTesting() {
        properties.clear();
        configFile = null;
    }
}
