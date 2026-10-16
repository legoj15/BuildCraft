/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import buildcraft.api.registry.IReloadableRegistry.PackType;
import buildcraft.api.statements.StatementManager;
import buildcraft.lib.script.ScriptableRegistry;
import buildcraft.lib.script.SimpleScript;
import buildcraft.lib.script.SimpleScript.ScriptAction;
import buildcraft.lib.script.SimpleScript.ScriptActionAdd;
import buildcraft.robotics.BCRoboticsBlocks;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.silicon.BCSiliconBlocks;

/** Pins the guide book's registration script: every main-book entry carries one of the eight game-system tags, the
 *  tags and subtypes it uses are translated, the robotics content is listed, and no retired subtype survives.
 *
 *  <p>This drives the <em>real</em> JSON-INSN interpreter ({@link SimpleScript}, the same class
 *  {@code ScriptableRegistry} uses at resource reload) over the shipped {@code guide.txt} + {@code util.txt}, so an
 *  alias with the wrong argument count (which the interpreter drops silently) shows up as a missing entry rather
 *  than as a green test. The scripts are read from the test classpath (this node's processed resources); the
 *  Java-source view in {@code MainSourceSet} does not cover resources. */
class GuideTxtSystemTagTest {

    private static final String MAIN_BOOK = "buildcraftunofficial:main";
    private static final String COMPAT_DIR = "/assets/buildcraftunofficial/compat/buildcraft/";
    private static final String LANG_DIR = "/assets/buildcraftunofficial/lang/";

    /** The fixed set of game systems, in contents-page order. */
    private static final Set<String> SYSTEMS = Set.of("getting_started", "power", "transport", "logic", "building",
        "mining_fluids", "silicon", "robots");

    /** Subtypes retired when the contents page was regrouped by system; none may come back. */
    private static final Set<String> REMOVED_SUBTYPES = Set.of("basic", "specialized", "automation", "item");

    /** One expanded {@code add}: the registered entry path (domain stripped) and its JSON. */
    private record Entry(String path, JsonObject json) {
        String str(String key) {
            return json.has(key) ? json.get(key).getAsString() : "";
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------------------

    private static Path assetsRoot() {
        URL url = GuideTxtSystemTagTest.class.getResource(COMPAT_DIR + "guide.txt");
        assertNotNull(url, "guide.txt is not on the test classpath");
        try {
            // .../assets/buildcraftunofficial/compat/buildcraft/guide.txt -> .../assets
            return Path.of(url.toURI()).getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /** Runs the shipped guide.txt through the real interpreter and returns every entry it registers. */
    private static List<Entry> expandGuide() {
        Path root = assetsRoot();
        Path scriptFile = root.resolve("buildcraftunofficial/compat/buildcraft/guide.txt");
        Path scriptDir = root.resolve("buildcraftunofficial/compat/buildcraft/guide");
        List<String> contents;
        try {
            contents = new ArrayList<>(Files.readAllLines(scriptFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertEquals("~{buildcraft/json/insn}", contents.set(0, "// Valid file declaration was here"));

        ScriptableRegistry<Object> registry = new ScriptableRegistry<>(PackType.RESOURCE_PACK, "buildcraft/guide");
        SimpleScript script = new SimpleScript(registry, root, "buildcraftunofficial", scriptDir, scriptFile,
            List.of(root), contents);

        List<Entry> entries = new ArrayList<>();
        for (ScriptAction action : script.actions) {
            if (action instanceof ScriptActionAdd add) {
                entries.add(new Entry(add.name.getPath(), add.getJson()));
            }
        }
        assertFalse(entries.isEmpty(), "the interpreter produced no entries at all");
        return entries;
    }

    private static List<Entry> mainBook() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : expandGuide()) {
            if (MAIN_BOOK.equals(e.str("book"))) {
                out.add(e);
            }
        }
        return out;
    }

    private static Map<String, String> lang(String file) {
        try (InputStream in = GuideTxtSystemTagTest.class.getResourceAsStream(LANG_DIR + file)) {
            assertNotNull(in, file + " is not on the test classpath");
            JsonObject obj = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, String> out = new LinkedHashMap<>();
            obj.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String describe(Entry e) {
        return e.path() + " (" + e.json() + ")";
    }

    // ---------------------------------------------------------------------------------------------------------
    // (1) every main-book entry carries a system
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void everyMainBookEntryHasOneOfTheEightSystems() {
        List<Entry> entries = mainBook();
        assertTrue(entries.size() > 150, "expected the full main-book catalogue, got " + entries.size());
        List<String> bad = new ArrayList<>();
        for (Entry e : entries) {
            String system = e.str("tag_system");
            if (!SYSTEMS.contains(system)) {
                bad.add(e.path() + " -> '" + system + "'");
            }
        }
        assertTrue(bad.isEmpty(), "entries without a valid tag_system:\n" + String.join("\n", bad));
    }

    @Test
    void everySystemIsUsedAtLeastOnce() {
        Set<String> used = new HashSet<>();
        for (Entry e : mainBook()) {
            used.add(e.str("tag_system"));
        }
        Set<String> unused = new TreeSet<>(SYSTEMS);
        unused.removeAll(used);
        assertTrue(unused.isEmpty(), "systems with no entries: " + unused);
    }

    @Test
    void gettingStartedFloatsFirst() {
        for (Entry e : mainBook()) {
            if (e.path().equals("item/guide")) {
                assertEquals("getting_started", e.str("tag_system"));
                assertEquals(-10, e.json().get("sort").getAsInt(), "the guide book sorts first in its system");
                return;
            }
        }
        throw new AssertionError("item/guide is not registered");
    }

    // ---------------------------------------------------------------------------------------------------------
    // (2) every tag used is translated in both languages
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void everySystemAndSubtypeHasALangKeyInBothLanguages() {
        Map<String, String> en = lang("en_us.json");
        Map<String, String> zh = lang("zh_cn.json");

        Set<String> keys = new TreeSet<>();
        // All eight systems are translated even if one were temporarily empty.
        for (String s : SYSTEMS) {
            keys.add("buildcraft.guide.chapter.system." + s);
        }
        for (Entry e : mainBook()) {
            keys.add("buildcraft.guide.chapter.system." + e.str("tag_system"));
            String sub = e.str("tag_subtype");
            if (!sub.isEmpty()) {
                keys.add("buildcraft.guide.chapter.subtype." + sub);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!en.containsKey(key) || en.get(key).isBlank()) {
                missing.add("en_us: " + key);
            }
            if (!zh.containsKey(key) || zh.get(key).isBlank()) {
                missing.add("zh_cn: " + key);
            }
        }
        assertTrue(missing.isEmpty(), "missing guide chapter translations:\n" + String.join("\n", missing));
    }

    @Test
    void everyTitleOverrideHasALangKeyInBothLanguages() {
        // `title` is a suffix under buildcraft.guide.page.; a missing key shows the raw suffix as the page title.
        Map<String, String> en = lang("en_us.json");
        Map<String, String> zh = lang("zh_cn.json");
        Set<String> keys = new TreeSet<>();
        for (Entry e : mainBook()) {
            String title = e.str("title");
            if (!title.isEmpty()) {
                keys.add("buildcraft.guide.page." + title);
            }
        }
        assertFalse(keys.isEmpty(), "no entry carries a title override; the robots overview and boards should");
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!en.containsKey(key) || en.get(key).isBlank()) {
                missing.add("en_us: " + key);
            }
            if (!zh.containsKey(key) || zh.get(key).isBlank()) {
                missing.add("zh_cn: " + key);
            }
        }
        assertTrue(missing.isEmpty(), "missing guide page-title translations:\n" + String.join("\n", missing));
    }

    // ---------------------------------------------------------------------------------------------------------
    // (3) robotics content is registered
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void robotItemsAndBlocksAreRegisteredUnderTheirRealIds() {
        Set<String> stacks = new HashSet<>();
        for (Entry e : mainBook()) {
            stacks.add(e.str("stack"));
        }
        // Ids come from the live registrations, so a rename in code fails here instead of leaving a dead entry.
        List<String> expected = List.of(
            BCRoboticsItems.ROBOT.getId().toString(),
            BCRoboticsItems.REDSTONE_BOARD.getId().toString(),
            BCRoboticsItems.ROBOT_STATION.getId().toString(),
            BCSiliconBlocks.PROGRAMMING_TABLE.getId().toString(),
            BCSiliconBlocks.INTEGRATION_TABLE.getId().toString(),
            BCRoboticsBlocks.ZONE_PLANNER.getId().toString(),
            BCRoboticsBlocks.REQUESTER.getId().toString());
        for (String id : expected) {
            assertTrue(stacks.contains(id), "guide.txt does not register " + id);
        }
    }

    /** Robot statement ids the guide must list. There are 18, not the 20 the lang file names: "Drop Items In Pipe"
     *  is only a second tag of Accept Items ({@code station.drop_in_pipe}) and "Allow Craft" has no statement class
     *  at all, so neither has an id to register. */
    private static final List<String> ROBOT_STATEMENTS = List.of(
        "buildcraft:robot.sleep", "buildcraft:robot.in.station", "buildcraft:robot.linked",
        "buildcraft:robot.reserved",
        "buildcraft:robot.goto_station", "buildcraft:robot.work_in_area", "buildcraft:robot.load_unload_area",
        "buildcraft:robot.wakeup", "buildcraft:robot.work_filter", "buildcraft:robot.work_filter_tool",
        "buildcraft:station.provide_items", "buildcraft:station.accept_items", "buildcraft:station.request_items",
        "buildcraft:station.provide_machine_request", "buildcraft:station.accept_fluids",
        "buildcraft:station.provide_fluids", "buildcraft:station.forbid_robot", "buildcraft:station.force_robot");

    @Test
    void everyRobotStatementIsRegisteredAndNothingRobotShapedIsMissed() {
        Set<String> registered = new HashSet<>();
        for (Entry e : mainBook()) {
            if ("statement".equals(e.str("type"))) {
                registered.add(e.str("statement"));
            }
        }
        for (String id : ROBOT_STATEMENTS) {
            assertTrue(registered.contains(id), "guide.txt does not register the statement " + id);
        }
        assertEquals(18, ROBOT_STATEMENTS.size());

        // And the other direction: the code's own robot/station statements are exactly this list. The
        // station.drop_in_pipe alias is a second tag of Accept Items, not a statement of its own.
        assertNotNull(BCRoboticsStatements.TRIGGER_ROBOT_SLEEP); // forces the self-registering statics
        Set<String> inCode = new TreeSet<>();
        for (String tag : StatementManager.statements.keySet()) {
            if ((tag.startsWith("buildcraft:robot.") || tag.startsWith("buildcraft:station."))
                && !tag.equals("buildcraft:station.drop_in_pipe")) {
                inCode.add(tag);
            }
        }
        assertEquals(new TreeSet<>(ROBOT_STATEMENTS), inCode,
            "the robot statements the code registers differ from the ones the guide must list");
    }

    @Test
    void robotsSystemOwnsTheRobotContent() {
        Set<String> robotSystemStatements = new HashSet<>();
        for (Entry e : mainBook()) {
            if ("statement".equals(e.str("type")) && ROBOT_STATEMENTS.contains(e.str("statement"))) {
                robotSystemStatements.add(e.str("statement"));
                assertEquals("robots", e.str("tag_system"), describe(e));
                assertTrue(Set.of("robot", "station").contains(e.str("tag_subtype")), describe(e));
            }
        }
        assertEquals(ROBOT_STATEMENTS.size(), robotSystemStatements.size());
    }

    // ---------------------------------------------------------------------------------------------------------
    // (4) retired subtypes stay retired
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void noEntryUsesARemovedSubtype() {
        List<String> bad = new ArrayList<>();
        for (Entry e : expandGuide()) {
            if (REMOVED_SUBTYPES.contains(e.str("tag_subtype"))) {
                bad.add(describe(e));
            }
        }
        assertTrue(bad.isEmpty(), "entries still on a retired subtype:\n" + String.join("\n", bad));
    }

    @Test
    void retiredSubtypeKeysAreGoneFromBothLangFiles() {
        for (String file : List.of("en_us.json", "zh_cn.json")) {
            Map<String, String> lang = lang(file);
            for (String sub : REMOVED_SUBTYPES) {
                assertFalse(lang.containsKey("buildcraft.guide.chapter.subtype." + sub),
                    file + " still carries the retired subtype key '" + sub + "'");
            }
        }
        assertEquals("Lasers", lang("en_us.json").get("buildcraft.guide.chapter.subtype.laser"));
    }

    // ---------------------------------------------------------------------------------------------------------
    // (5) every id the script names exists in the game
    // ---------------------------------------------------------------------------------------------------------

    /** Every {@code stack} id (an item entry's registry id, optionally followed by {@code ,count}) must be a
     *  registered item: a typo or a renamed block item leaves a guide entry that renders as an empty stack. */
    @Test
    void everyStackIdIsARegisteredItem() {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (Entry e : expandGuide()) {
            String stack = e.str("stack");
            if (stack.isEmpty()) {
                continue;
            }
            checked++;
            Identifier id = Identifier.tryParse(stack.split(",")[0].trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                bad.add(e.path() + " -> '" + stack + "'");
            }
        }
        assertTrue(checked > 100, "expected the full set of stack entries, checked " + checked);
        assertTrue(bad.isEmpty(), "guide.txt names unregistered items:\n" + String.join("\n", bad));
    }

    /** Every {@code statement} id must resolve in {@link StatementManager#statements}; a stale id would generate
     *  the "runtime stub" page instead of the authored one. */
    @Test
    void everyStatementIdIsARegisteredStatement() {
        // Statements self-register from static initialisers of their holder classes; the FML-JUnit boot loads
        // BC's content, but touch the holders explicitly so this test does not depend on load order.
        assertNotNull(BCRoboticsStatements.TRIGGER_ROBOT_SLEEP);
        assertFalse(StatementManager.statements.isEmpty(), "StatementManager is empty under the test JVM");
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (Entry e : expandGuide()) {
            String statement = e.str("statement");
            if (statement.isEmpty()) {
                continue;
            }
            checked++;
            if (!StatementManager.statements.containsKey(statement)) {
                bad.add(e.path() + " -> '" + statement + "'");
            }
        }
        assertTrue(checked > 50, "expected the full set of statement entries, checked " + checked);
        assertTrue(bad.isEmpty(), "guide.txt names unregistered statements:\n" + String.join("\n", bad));
    }

    // ---------------------------------------------------------------------------------------------------------
    // util.txt hygiene
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void utilDefinesEachAliasOnce() throws IOException {
        String util;
        try (InputStream in = GuideTxtSystemTagTest.class.getResourceAsStream(COMPAT_DIR + "guide/util.txt")) {
            assertNotNull(in, "util.txt is not on the test classpath");
            util = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("(?m)^alias\\s+(\\S+)\\s+\\d+").matcher(util);
        Set<String> seen = new HashSet<>();
        List<String> dupes = new ArrayList<>();
        while (m.find()) {
            if (!seen.add(m.group(1))) {
                dupes.add(m.group(1));
            }
        }
        assertTrue(dupes.isEmpty(), "aliases defined more than once in util.txt: " + dupes);
    }
}
