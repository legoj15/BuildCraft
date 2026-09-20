/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import buildcraft.lib.BCLib;

/**
 * Pins every registered BuildCraft item to a client item definition, from both directions at once —
 * and the model pointers inside those definitions to model files that actually exist.
 *
 * <p>Since 1.21.4 an item's client look is defined by {@code assets/<ns>/items/<id>.json}, and on
 * this tree that directory is ALSO the source of truth on every node: the pre-1.21.10 nodes
 * synthesise the classic {@code models/item} JSONs from these definitions at build time
 * ({@code DataDownport1211.generateOldItemModels1211}). A registered item without a definition
 * renders missing-textured — the failure commit 79fe15f33 fixed for the Requester, whose
 * definition had been left behind — and the failure is SILENT at boot: nothing logs, the item is
 * simply purple-black in hand and GUI. This guard turns that into a test failure, and catches the
 * reverse too: a definition whose item was renamed or deleted is dead weight that still ships in
 * the jar (and still synthesises a model on the old nodes).
 *
 * <p>The live registry is the decision procedure, not a source scan: ids built by loops (the 16
 * wires, the 30 fluid buckets) or derived from block ids at runtime ({@code engine_rf}) are
 * invisible to greps over the registration call sites but obvious here. The comparison runs
 * against the DEV registry — the {@code test} task sets {@code -Dbuildcraft.dev=true} like every
 * other BuildCraft run environment — so the dev-gated items and their intentional definitions
 * compare 1:1 instead of reading as phantom orphans.
 *
 * <p>Node gate: the three assertions live only on the 1.21.10+ nodes — the sweep was scoped there,
 * because that is where a missing file is directly user-visible: 1.21.10+ consumes these JSONs as
 * the item's client definition, while on the 1.21.1 node they are only the input the downport
 * synthesises classic models from, where a gap instead leaves an item silently model-less at
 * runtime. The files and the registry are node-independent, so these runs still catch a gap that
 * would only show in-game on the old node.
 */
public class ClientItemDefinitionCoverageTester {

    /** The only namespace BuildCraft registers items under — every subsystem's
     * {@code DeferredRegister.createItems} uses {@code BCCore.MODID}; the legacy submod namespaces
     * appear only as {@code LegacyAliases} remaps, which are lookups, not registry entries. */
    private static final String NAMESPACE = "buildcraftunofficial";

    /** Definitions in the RAW source tree. The Stonecutter-processed copy under
     * {@code versions/<node>/} is a build artifact, never the truth. */
    private static final String DEFINITION_DIR = "src/main/resources/assets/buildcraftunofficial/items";

    /** Where every {@code minecraft:model} pointer in those definitions must land — the RAW
     * source tree again, never the Stonecutter copy under {@code versions/<node>/}. */
    private static final String MODEL_DIR = "src/main/resources/assets/buildcraftunofficial/models";

    /** Walks up from the working directory to the repo root. Tests run from a Stonecutter node
     * directory, not the root, so the location cannot be assumed (same approach as
     * GameTestManifestTester). */
    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/buildcraft"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root from " + Paths.get("").toAbsolutePath());
    }

    /** The path part of every registered {@code buildcraftunofficial} item id, straight from the
     * live registry the mod load populated. */
    private static Set<String> registeredItemIds() {
        Set<String> ids = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (NAMESPACE.equals(id.getNamespace())) {
                ids.add(id.getPath());
            }
        }
        Assertions.assertFalse(ids.isEmpty(),
            "no " + NAMESPACE + " items found in the live item registry — the FML-JUnit environment"
                + " did not load the mod, so this guard's comparison would be vacuous."
                + " Do not weaken the guard; fix the test boot instead.");
        return ids;
    }

    /** Every {@code items/*.json} definition's id (file name minus the extension; slashes kept for
     * nested ids), sorted, from the raw source tree. */
    private static Set<String> definitionIds() {
        Path dir = repoRoot().resolve(DEFINITION_DIR);
        Assertions.assertTrue(Files.isDirectory(dir),
            DEFINITION_DIR + " not found - if the definitions moved, update"
                + " ClientItemDefinitionCoverageTester.DEFINITION_DIR");
        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Assertions.assertFalse(files.isEmpty(),
            DEFINITION_DIR + " contains no item definitions at all — if the asset root moved, update"
                + " ClientItemDefinitionCoverageTester.DEFINITION_DIR; an empty match set makes the"
                + " coverage check vacuous.");
        Set<String> ids = new TreeSet<>();
        for (Path p : files) {
            ids.add(dir.relativize(p).toString().replace('\\', '/').replaceFirst("\\.json$", ""));
        }
        return ids;
    }

    /** Fails fast when the JVM was not launched with {@code -Dbuildcraft.dev=true}: Gradle's
     * {@code test} task sets it, but a plain IDE JUnit launch does not, and without it the
     * dev-gated items are absent from the registry while their definitions still exist — the
     * reverse check would then report them as orphans and advise deleting required content. */
    private static void requireDevRegistry() {
        Assertions.assertTrue(BCLib.DEV,
            "this guard compares against the DEV item registry — the JVM was launched without"
                + " -Dbuildcraft.dev=true, so the dev-gated items are missing and their definitions"
                + " would read as phantom orphans. Run through Gradle's test task (which sets the"
                + " flag) or add it to the JUnit run config; do not weaken the guard instead.");
    }

    /** Collects the {@code model} id of every node whose {@code type} is exactly
     *  {@code minecraft:model}, recursing into every other value regardless of its key: condition
     *  chains, {@code range_dispatch} entries and tint arrays all carry their file ids on typed
     *  {@code minecraft:model} leaves, and a {@code range_dispatch} entry's bare {@code model}
     *  member is a nested NODE, not an id — keying on the {@code model} name alone would collect
     *  tint sources and fluid ids instead of file targets. */
    private static void collectModelTargets(JsonElement node, String source, Set<String> out) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            JsonElement type = object.get("type");
            boolean isModelNode = type != null && type.isJsonPrimitive()
                && "minecraft:model".equals(type.getAsString());
            if (isModelNode) {
                JsonElement model = object.get("model");
                Assertions.assertTrue(model != null && model.isJsonPrimitive(),
                    "items/" + source + ".json has a minecraft:model node with no string model id"
                        + " — that is a broken definition, not a model-less one");
                out.add(model.getAsString());
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectModelTargets(entry.getValue(), source, out);
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                collectModelTargets(element, source, out);
            }
        }
    }

    /** Whether a definition's root model node is a type whose model NeoForge generates at
     * runtime, with no static JSON for the walker to point at. {@code neoforge:fluid_container}
     * is the only such type in this tree — the 31 fluid buckets. */
    private static boolean hasRuntimeGeneratedRoot(JsonObject root) {
        JsonElement node = root.get("model");
        if (node == null || !node.isJsonObject()) {
            return false;
        }
        JsonElement type = node.getAsJsonObject().get("type");
        return type != null && type.isJsonPrimitive()
            && "neoforge:fluid_container".equals(type.getAsString());
    }

    /** The footgun that motivated this guard: a registered item with no definition is never
     * diagnosed — it just renders missing-textured. */
    //? if >=1.21.10 {
    @Test
    public void everyRegisteredItemHasADefinition() {
        requireDevRegistry();
        Path dir = repoRoot().resolve(DEFINITION_DIR);
        List<String> missing = new ArrayList<>();
        for (String id : registeredItemIds()) {
            if (!Files.isRegularFile(dir.resolve(id + ".json"))) {
                missing.add(NAMESPACE + ":" + id);
            }
        }
        Assertions.assertTrue(missing.isEmpty(),
            "registered item(s) with no client item definition — they render missing-textured"
                + " on 1.21.10+:\n    "
                + String.join("\n    ", missing)
                + "\n    Author " + DEFINITION_DIR + "/<id>.json following items/requester.json:"
                + " a BlockItem's definition points at buildcraftunofficial:block/<block id>, a"
                + " plain item's at buildcraftunofficial:item/<id> — and that model (plus"
                + " texture) must already exist.");
    }

    /** The reverse direction: an orphan definition never renders for anything, and usually means
     * an item was renamed or deleted while its JSON stayed behind. */
    @Test
    public void everyDefinitionBacksARegisteredItem() {
        requireDevRegistry();
        Set<String> registered = registeredItemIds();
        List<String> orphans = new ArrayList<>();
        for (String id : definitionIds()) {
            if (!registered.contains(id)) {
                orphans.add(NAMESPACE + ":" + id);
            }
        }
        Assertions.assertTrue(orphans.isEmpty(),
            "item definition(s) in items/ that no registered item uses:\n    "
                + String.join("\n    ", orphans)
                + "\n    Orphans are dead weight — they still ship in the jar and still synthesise a"
                + " model on the pre-1.21.10 nodes. Verify the id is truly unregistered (the"
                + " registration call sites AND any dynamic producers), then git rm the file —"
                + " never delete speculatively.");
    }

    /** The definitions' own pointers: every {@code minecraft:model} target must resolve to a real
     *  model JSON, or the item renders missing-textured exactly as silently as a missing
     *  definition does. {@code buildcraftunofficial} ids are file-checked against the raw tree;
     *  {@code minecraft} is trusted (vanilla ships its own models); any other namespace fails —
     *  the resolver needs an explicit decision, not a silent guess. A definition with no targets
     *  at all is legitimate ONLY when its root model node is {@code neoforge:fluid_container}
     *  (runtime-generated, no static JSON to point at); any other model-less definition — a
     *  typo'd type string reads exactly like one — fails. The vacuousness guard is still the
     *  TOTAL: zero targets across every definition means the walker itself broke. */
    @Test
    public void everyDefinitionModelResolves() {
        Path definitionDir = repoRoot().resolve(DEFINITION_DIR);
        Path modelDir = repoRoot().resolve(MODEL_DIR);
        Set<String> total = new TreeSet<>();
        List<String> unresolvable = new ArrayList<>();
        List<String> modelLess = new ArrayList<>();
        for (String id : definitionIds()) {
            JsonObject root;
            try (InputStream in = Files.newInputStream(definitionDir.resolve(id + ".json"))) {
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                throw new AssertionError(
                    "items/" + id + ".json does not parse as a JSON object: " + e);
            }
            Set<String> targets = new TreeSet<>();
            collectModelTargets(root, id, targets);
            total.addAll(targets);
            if (targets.isEmpty() && !hasRuntimeGeneratedRoot(root)) {
                modelLess.add(id);
            }
            for (String target : targets) {
                int colon = target.indexOf(':');
                if (colon < 0) {
                    unresolvable.add("items/" + id + ".json -> " + target
                        + " (not namespaced — the resolver needs an explicit decision for it)");
                    continue;
                }
                String namespace = target.substring(0, colon);
                if ("minecraft".equals(namespace)) {
                    continue;
                }
                String path = target.substring(colon + 1);
                if (!NAMESPACE.equals(namespace)) {
                    unresolvable.add("items/" + id + ".json -> " + target
                        + " (namespace is neither " + NAMESPACE + " nor minecraft — the resolver"
                        + " needs an explicit decision for it)");
                } else if (!Files.isRegularFile(modelDir.resolve(path + ".json"))) {
                    unresolvable.add("items/" + id + ".json -> " + target
                        + " (no " + MODEL_DIR + "/" + path + ".json)");
                }
            }
        }
        Assertions.assertFalse(total.isEmpty(),
            "zero minecraft:model targets collected from any definition in " + DEFINITION_DIR
                + " — an empty total means the walker silently broke, not that every definition"
                + " went model-less. Do not weaken the guard; fix the walker instead.");
        Assertions.assertTrue(modelLess.isEmpty(),
            "definition(s) whose walker collected no model target and whose root is not the"
                + " runtime-generated neoforge:fluid_container:\n    "
                + String.join("\n    ", modelLess)
                + "\n    A typo'd type string reads as model-less exactly like a typo'd pointer —"
                + " the item renders missing-textured. Fix the type (or, when a new kind of"
                + " runtime-generated root model is introduced on purpose, extend"
                + " hasRuntimeGeneratedRoot deliberately).");
        Assertions.assertTrue(unresolvable.isEmpty(),
            "model target(s) in the item definitions that resolve to no model file:\n    "
                + String.join("\n    ", unresolvable)
                + "\n    The item renders missing-textured, as silently as a missing definition."
                + " Retarget the definition at an existing model, or author "
                + MODEL_DIR + "/<path>.json.");
    }
    //?}
}
