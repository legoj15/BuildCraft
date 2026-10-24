/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * A tile that wipes its own saved data from the creative pick-block item has two lists that must agree: the keys its
 * {@code writeData} saves and the keys its {@code removeComponentsFromTag} discards. Vanilla attaches the saved tag
 * to a Ctrl+middle-click clone and then calls {@code removeComponentsFromTag}; a key saved but not discarded rides
 * into the clone, and placing it recreates the machine's contents (gate logic, filters, a bank of paintbrushes) for
 * free. Nothing fails when one list grows and the other does not, so this reads both from the source this node
 * compiled and fails on any saved key the discard list does not know; a discard counts only when it is made on the tag
 * the method receives, so one against some other object cannot stand in for the real thing. Like
 * {@link ClearableTwinGuardTest} for the wipe hooks, but it compares the two lists rather than the two methods'
 * existence.
 * <p>
 * Read from source rather than run: a key written under a condition (the pipe holder saves {@code wires} only when
 * a wire exists) would never show in an empty tile, and the keys are the same on every node. Anything the guard cannot
 * follow (the output handed to another method, a key that is not a literal) fails the test instead of passing
 * vacuously, so teach the guard or list the key by hand in that case.
 */
public class RemoveComponentsTwinGuardTest {

    /** The tiles that discard everything they save. {@code TileTank} also strips its tag, but its keys come from the
     *  fluid tank's own serializer and differ per node, so there is nothing to read here. */
    private static final List<String> TILES = List.of(
            "buildcraft.transport.tile.TilePipeHolder",
            "buildcraft.transport.tile.TileFilteredBuffer",
            "buildcraft.robotics.tile.TileZonePlanner");

    private static final String OWNER_DATA = "buildcraft/lib/tile/OwnerData.java";

    private static final Pattern WRITE_DATA = Pattern.compile("void\\s+writeData\\s*\\(\\s*BCValueOutput\\s+(\\w+)\\s*\\)");
    private static final Pattern STRING_ARG = Pattern.compile("\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern OWNER_PUT = Pattern.compile("\\bput\\s*\\.\\s*accept" + STRING_ARG);

    private static Map<String, String> sources;

    /** Live code with literals kept, by path under the main source root. */
    private static synchronized Map<String, String> sources() {
        if (sources == null) {
            sources = MainSourceSet.javaFiles().stream().collect(Collectors.toMap(
                    MainSourceSet.SourceFile::relativePath, f -> MainSourceSet.withoutComments(f.text()),
                    (first, second) -> first));
        }
        return sources;
    }

    @Test
    public void everySavedKeyIsDiscardedFromThePickedItem() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String tileName : TILES) {
            List<Class<?>> chain = chainOf(tileName);
            Set<String> unfollowed = new LinkedHashSet<>();
            Set<String> saved = collect(chain, code -> savedKeys(code, unfollowed));
            Set<String> discarded = collect(chain, RemoveComponentsTwinGuardTest::discardedKeys);

            // Liveness: a scan that found nothing would pass vacuously. The owner keys prove the delegation to
            // OwnerData.writeOwner was followed, which every one of these tiles relies on.
            Assertions.assertFalse(saved.isEmpty(), tileName + ": no saved key was found, so the scan is blind");
            Assertions.assertFalse(discarded.isEmpty(), tileName + ": no discarded key was found, so the scan is blind");
            Assertions.assertTrue(saved.containsAll(List.of("ownerUUID", "ownerName")),
                    tileName + ": the owner keys were not followed into OwnerData.writeOwner, found " + saved);

            for (String use : unfollowed) {
                offenders.add(tileName + " writeData uses its output in a way the guard cannot follow: " + use);
            }
            Set<String> missing = new TreeSet<>(saved);
            missing.removeAll(discarded);
            if (!missing.isEmpty()) {
                offenders.add(tileName + " saves " + missing + " but removeComponentsFromTag does not discard it "
                        + "(discarded: " + new TreeSet<>(discarded) + ")");
            }
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "Ctrl+middle-click pick-block would carry what these tiles save into the cloned item and recreate it "
                        + "for free. Discard every key writeData emits, in removeComponentsFromTag (both Stonecutter "
                        + "arms): " + offenders);
    }

    /** The tile's class and each superclass that has main source, nearest first. */
    private static List<Class<?>> chainOf(String tileName) throws ClassNotFoundException {
        ClassLoader loader = RemoveComponentsTwinGuardTest.class.getClassLoader();
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = Class.forName(tileName, false, loader); c != null; c = c.getSuperclass()) {
            if (sources().containsKey(sourcePath(c))) {
                chain.add(c);
            }
        }
        Assertions.assertFalse(chain.isEmpty(), tileName + " has no source in this node's main source set");
        return chain;
    }

    private static String sourcePath(Class<?> type) {
        return type.getName().replace('.', '/') + ".java";
    }

    private static Set<String> collect(List<Class<?>> chain, Function<String, Set<String>> reader) {
        Set<String> keys = new LinkedHashSet<>();
        for (Class<?> c : chain) {
            keys.addAll(reader.apply(sources().get(sourcePath(c))));
        }
        return keys;
    }

    /**
     * The keys {@code code}'s own {@code writeData} saves: every {@code store}, {@code child} or {@code put<Type>} call
     * on the output with a literal key, the owner pair behind {@code OwnerData.writeOwner}, and nothing from
     * {@code super.writeData} (the
     * chain is walked class by class). A computed key such as {@code "layer_" + i} counts as its literal prefix, which
     * is what the discard side spells too. Any other use of the output goes into {@code unfollowed}.
     */
    private static Set<String> savedKeys(String code, Set<String> unfollowed) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher decl = WRITE_DATA.matcher(code);
        if (!decl.find()) {
            return keys;
        }
        String out = Pattern.quote(decl.group(1));
        String body = MainSourceSet.methodBody(code, decl.group());

        Matcher direct = Pattern.compile("\\b" + out + "\\s*\\.\\s*(?:store|child|put[A-Z]\\w*)" + STRING_ARG)
                .matcher(body);
        while (direct.find()) {
            keys.add(direct.group(1));
        }
        String rest = direct.replaceAll("");

        Pattern ownerCall = Pattern.compile("\\bOwnerData\\s*\\.\\s*writeOwner\\s*\\(\\s*" + out + "\\s*,");
        if (ownerCall.matcher(rest).find()) {
            keys.addAll(ownerKeys());
            rest = ownerCall.matcher(rest).replaceAll("");
        }
        rest = Pattern.compile("\\bsuper\\s*\\.\\s*writeData\\s*\\(\\s*" + out + "\\s*\\)").matcher(rest).replaceAll("");

        Matcher leftover = Pattern.compile("[^;{}]*\\b" + out + "\\b[^;{}]*").matcher(rest);
        while (leftover.find()) {
            unfollowed.add(leftover.group().strip());
        }
        return keys;
    }

    /** The keys {@code OwnerData.writeOwner} saves, read from its own source. */
    private static Set<String> ownerKeys() {
        String code = sources().get(OWNER_DATA);
        Assertions.assertNotNull(code, "this node did not compile " + OWNER_DATA);
        Set<String> keys = new LinkedHashSet<>();
        Matcher put = OWNER_PUT.matcher(MainSourceSet.methodBody(code, "static void write("));
        while (put.find()) {
            keys.add(put.group(1));
        }
        return keys;
    }

    /**
     * The keys {@code code}'s {@code removeComponentsFromTag} discards: every {@code discard/remove/accept("key")} made
     * on the tag or output the method receives, in it and in the methods of the same class it hands that tag to (the
     * zone planner passes {@code output::discard} to a helper both Stonecutter arms share, which calls it as
     * {@code discard.accept("key")}). A discard made on anything else (a scratch set, another tag) takes nothing off
     * the picked item, so it is not counted.
     */
    private static Set<String> discardedKeys(String code) {
        Set<String> keys = new LinkedHashSet<>();
        collectDiscards(code, "removeComponentsFromTag", true, keys, new LinkedHashSet<>());
        return keys;
    }

    /**
     * @param sinkHandedOver whether the caller passed this method the tag it is stripping (the entry point gets it from
     *                       vanilla): a method that was not given it cannot discard anything from it
     */
    private static void collectDiscards(String code, String method, boolean sinkHandedOver, Set<String> keys,
            Set<String> visited) {
        if (!sinkHandedOver || !visited.add(method)) {
            return;
        }
        Matcher decl = Pattern.compile("\\bvoid\\s+" + Pattern.quote(method) + "\\s*\\(([^)]*)\\)").matcher(code);
        if (!decl.find()) {
            return;
        }
        Matcher param = Pattern.compile("(\\w+)\\s*$").matcher(decl.group(1).strip());
        Assertions.assertTrue(param.find(), "the guard cannot tell which parameter of " + decl.group()
                + " is the tag it strips; teach the guard or list the keys by hand");
        String sink = Pattern.quote(param.group(1));
        String body = MainSourceSet.methodBody(code, decl.group());
        Matcher discard = Pattern.compile("\\b" + sink + "\\s*\\.\\s*(?:discard|remove|accept)" + STRING_ARG)
                .matcher(body);
        while (discard.find()) {
            keys.add(discard.group(1));
        }
        // Each call with the rest of its statement: the callee is handed the tag when the statement names it.
        Matcher call = Pattern.compile("\\b(\\w+)\\s*\\(([^;]*)").matcher(body);
        while (call.find()) {
            boolean handedOver = Pattern.compile("\\b" + sink + "\\b").matcher(call.group(2)).find();
            collectDiscards(code, call.group(1), handedOver, keys, visited);
        }
    }
}
