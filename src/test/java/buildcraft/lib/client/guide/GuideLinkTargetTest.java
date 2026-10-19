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
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import buildcraft.api.registry.IReloadableRegistry.PackType;
import buildcraft.lib.script.ScriptableRegistry;
import buildcraft.lib.script.SimpleScript;
import buildcraft.lib.script.SimpleScript.ScriptAction;
import buildcraft.lib.script.SimpleScript.ScriptActionAdd;

/** Every {@code <link>} in the shipped guide markdown must point at something the in-client loader can resolve.
 *
 *  <p>This mirrors {@code XmlPageLoader#loadLink} / {@code resolveLinkTarget} exactly, because a link that does not
 *  resolve does not fail loudly in-game: the author's tag is printed as raw text, or the link is silently dropped.
 *  The rules, in the loader's own order:
 *  <ul>
 *    <li>no {@code type}: the target must be a page registered by {@code guide.txt} (a reloadable entry, keyed
 *        {@code buildcraftunofficial:<dir>/<name>}) or a programmatic category id ({@code buildcraft:<group>},
 *        {@code GuideManager#CATEGORY_BODY_SOURCES}). There is <em>no</em> item-id fallback on this path.</li>
 *    <li>{@code type="item_stack"}: the target is parsed by {@code MarkdownPageLoader#parseItemStack} and must name a
 *        registered item.</li>
 *    <li>any other type: {@code PageValueType#createLink} answers "doesn't support links" unless the type overrides it,
 *        and only {@code PageEntryItemStack} does. {@code statement}, {@code fluid_stack} and {@code external} are
 *        therefore all unresolvable as links (link the statement's registered page instead).</li>
 *  </ul>
 *  The markdown is read from this node's processed resources on the test classpath, like
 *  {@link GuideTxtSystemTagTest} reads {@code guide.txt}. Set the environment variable
 *  {@code GUIDE_LINK_TEST_ROOT} to a directory of {@code .md} files to check that tree instead (used to prove the
 *  test catches a bad tree without editing the real one). */
class GuideLinkTargetTest {

    private static final String COMPAT_DIR = "/assets/buildcraftunofficial/compat/buildcraft/";
    private static final Pattern LINK_TAG = Pattern.compile("<link\\b([^>]*?)/?>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([A-Za-z_]+)\\s*=\\s*\"([^\"]*)\"");

    /** One parsed {@code <link>} and where it came from. */
    private record Link(String file, String to, String type, String raw) {}

    // ---------------------------------------------------------------------------------------------------------
    // Resolvable-target universe
    // ---------------------------------------------------------------------------------------------------------

    private static Path compatRoot() {
        URL url = GuideLinkTargetTest.class.getResource(COMPAT_DIR + "guide.txt");
        assertNotNull(url, "guide.txt is not on the test classpath");
        try {
            return Path.of(url.toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /** Every page path guide.txt registers, as the full {@code namespace:path} entry key. */
    private static Set<String> registeredPages() {
        Path compat = compatRoot();
        Path root = compat.getParent().getParent().getParent(); // .../assets
        Path scriptFile = compat.resolve("guide.txt");
        List<String> contents;
        try {
            contents = new ArrayList<>(Files.readAllLines(scriptFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertEquals("~{buildcraft/json/insn}", contents.set(0, "// Valid file declaration was here"));
        ScriptableRegistry<Object> registry = new ScriptableRegistry<>(PackType.RESOURCE_PACK, "buildcraft/guide");
        SimpleScript script = new SimpleScript(registry, root, "buildcraftunofficial", compat.resolve("guide"),
            scriptFile, List.of(root), contents);
        Set<String> pages = new HashSet<>();
        for (ScriptAction action : script.actions) {
            if (action instanceof ScriptActionAdd add) {
                pages.add(add.name.toString());
            }
        }
        assertTrue(pages.size() > 150, "expected the full guide catalogue, got " + pages.size());
        return pages;
    }

    /** The {@code domain:groupName} ids of the programmatic category entries (private source-of-truth table). */
    private static Set<String> categoryIds() {
        try {
            Field f = GuideManager.class.getDeclaredField("CATEGORY_BODY_SOURCES");
            f.setAccessible(true);
            String[][] rows = (String[][]) f.get(null);
            Set<String> ids = new HashSet<>();
            for (String[] row : rows) {
                ids.add(row[0] + ":" + row[1]);
            }
            assertFalse(ids.isEmpty(), "CATEGORY_BODY_SOURCES is empty");
            return ids;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not read GuideManager.CATEGORY_BODY_SOURCES", e);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Link extraction and resolution
    // ---------------------------------------------------------------------------------------------------------

    private static List<Link> extractLinks(String file, String markdown) {
        List<Link> out = new ArrayList<>();
        Matcher m = LINK_TAG.matcher(markdown);
        while (m.find()) {
            Map<String, String> attrs = new TreeMap<>();
            Matcher a = ATTR.matcher(m.group(1));
            while (a.find()) {
                attrs.put(a.group(1), a.group(2));
            }
            String to = attrs.get("to");
            String inline = attrs.get("inline");
            // resolveLinkTarget: `inline="<target>"` with no `to` promotes the inline value (not "true"/empty).
            if (to == null && inline != null && !inline.isEmpty() && !"true".equals(inline)) {
                to = inline;
            }
            out.add(new Link(file, to, attrs.get("type"), m.group().replaceAll("\\s+", " ")));
        }
        return out;
    }

    /** @return null when the link resolves, otherwise why it does not. */
    private static String problem(Link link, Set<String> pages, Set<String> categories) {
        if (link.to() == null) {
            return "link has no target";
        }
        if (link.type() == null) {
            if (pages.contains(link.to()) || categories.contains(link.to())) {
                return null;
            }
            return "'" + link.to() + "' is neither a page registered by guide.txt nor a category entry";
        }
        if (link.type().equals("item_stack")) {
            // MarkdownPageLoader#parseItemStack: first comma-separated arg is the item id.
            Identifier id = Identifier.tryParse(link.to().split(",")[0].trim());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return null;
            }
            return "'" + link.to() + "' is not a registered item";
        }
        return "type=\"" + link.type() + "\" does not support links (only item_stack does)";
    }

    private static List<String> problems(Map<String, String> files) {
        Set<String> pages = registeredPages();
        Set<String> categories = categoryIds();
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            for (Link link : extractLinks(file.getKey(), file.getValue())) {
                String why = problem(link, pages, categories);
                if (why != null) {
                    bad.add(file.getKey() + ": " + link.raw() + " -> " + why);
                }
            }
        }
        return bad;
    }

    private static Map<String, String> shippedMarkdown() {
        String override = System.getenv("GUIDE_LINK_TEST_ROOT");
        Path dir = override != null && !override.isBlank() ? Path.of(override) : compatRoot().resolve("guide/en_us");
        assertTrue(Files.isDirectory(dir), "no guide markdown directory at " + dir);
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".md"))::iterator) {
                out.put(dir.relativize(p).toString().replace('\\', '/'), Files.readString(p, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(out.size() > 100, "expected the full set of guide pages, got " + out.size());
        return out;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void everyGuideLinkResolves() {
        Map<String, String> files = shippedMarkdown();
        long links = files.entrySet().stream().mapToLong(e -> extractLinks(e.getKey(), e.getValue()).size()).sum();
        assertTrue(links > 100, "expected the guide to contain many links, found " + links);
        List<String> bad = problems(files);
        assertTrue(bad.isEmpty(), bad.size() + " unresolvable guide link(s):\n" + String.join("\n", bad));
    }

    /** MarkdownPageLoader only translates {@code #} headings; 1.12.2's deprecated {@code $[special.*]} lines and
     *  markdown {@code [text](target)} links are not parsed and print verbatim on the page (the guide item's own
     *  page shipped both). Use {@code <new_page/>}, {@code <recipes_usages stack=…/>} and {@code <link>} instead. */
    @Test
    void noGuidePageUsesUnparsedMarkdownSyntax() {
        Pattern raw = Pattern.compile("\\$\\[special\\.|\\[[^\\]\\n]+\\]\\([^)\\n]*\\)");
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> file : shippedMarkdown().entrySet()) {
            Matcher m = raw.matcher(file.getValue());
            while (m.find()) {
                bad.add(file.getKey() + ": " + m.group());
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + " piece(s) of markup the guide prints as raw text:\n"
            + String.join("\n", bad));
    }

    /** The exact tags that shipped in robots.md before the statement pages existed: they printed as raw text. */
    @Test
    void theRetiredStatementTypeLinksAreRejected() {
        String old = """
            <link to="buildcraft:robot.work_in_area" type="statement"/>
            Work in Area is the order most boards need.
            <link to="buildcraft:station.provide_items" type="statement"/>
            <link to="buildcraft:station.accept_items" type="statement"/>
            """;
        List<String> bad = problems(Map.of("concept/robots.md", old));
        assertEquals(3, bad.size(), String.valueOf(bad));
        for (String line : bad) {
            assertTrue(line.contains("type=\"statement\""), line);
        }
    }

    @Test
    void theCheckerAcceptsAndRejectsEachLinkShape() {
        Map<String, String> files = Map.of(
            "ok.md", """
                <link to="buildcraftunofficial:concept/robots"/>
                <link inline="buildcraftunofficial:item/robot"/>
                <link inline="true" to="buildcraftunofficial:item/robot"/>
                <link to="buildcraft:pipe_signals"/>
                <link to="minecraft:iron_ingot" type="item_stack"/>
                <link inline="minecraft:iron_ingot" type="item_stack"/>
                """,
            "bad.md", """
                <link to="buildcraftunofficial:concept/no_such_page"/>
                <link to="buildcraft:no_such_category"/>
                <link to="minecraft:iron_ingot"/>
                <link to="buildcraftunofficial:no_such_item" type="item_stack"/>
                <link to="minecraft:water" type="fluid_stack"/>
                <link to="whatever" type="external"/>
                <link to="whatever" type="no_such_type"/>
                <link type="item_stack"/>
                """);
        List<String> bad = problems(files);
        assertEquals(8, bad.size(), String.join("\n", bad));
        for (String line : bad) {
            assertTrue(line.startsWith("bad.md"), line);
        }
    }
}
