/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * Fails when this node's main code touches any API that its owner has marked
 * {@code @Deprecated(forRemoval = true)} — NeoForge, Minecraft, JEI, Guava or the JDK alike.
 *
 * <p><b>Why a test, when javac already warns?</b> javac's {@code [removal]} warnings scroll past in a green build,
 * {@code @SuppressWarnings("removal")} silences them, and since JEP 211 javac says nothing at all about an
 * {@code import} of a doomed type. The removal then lands at the next NeoForge/JEI bump as a compile break on one
 * node, usually mid-way through something else. This guard turns "deprecated for removal" into a red test on the
 * node where it happens, at the moment the pin that deprecated it is bumped.
 *
 * <p><b>What it reads.</b> Three independent views of the node's main code, all node-accurate (see
 * {@link MainSourceSet}):
 * <ol>
 *   <li>the compiled classes' constant pools — every type, field, method and constructor they reference, resolved
 *       reflectively against this node's own libraries to the declaration javac bound to;</li>
 *   <li>every BuildCraft method that overrides a supertype method (an override is not a reference, so the
 *       constant pool cannot see it);</li>
 *   <li>the {@code import} lines of the sources javac compiled on this node — the JEP 211 blind spot, including an
 *       import whose type is never used.</li>
 * </ol>
 * Compile-time constants are inlined by javac and leave no reference behind, so a doomed {@code static final}
 * primitive/String constant is the one thing this cannot see (javac still warns about it).
 *
 * <p><b>When it fails:</b> migrate the call site to the replacement the deprecation names (check
 * {@code .neoforge-ref/} for the node's line). If the migration genuinely has to wait, add the declaration to
 * {@link #PENDING} with the reason and the todo it is tracked under — never widen the scan's exclusions.
 */
public class ForRemovalApiGuardTester {

    /**
     * Deprecated-for-removal declarations BuildCraft knowingly still uses, each with the reason. Keys are a type's
     * binary name ({@code a.b.Outer$Inner}), which also covers that type's members, or {@code type#member}. An
     * entry that matches nothing on this node is fine — entries are node-agnostic, and another change may retire
     * the use first — but remove it once no node needs it.
     */
    private static final Map<String, String> PENDING = Map.of();

    private static final ClassLoader LOADER = ForRemovalApiGuardTester.class.getClassLoader();

    @Test
    public void mainCodeUsesNoApiDeprecatedForRemoval() throws IOException {
        Scanner scanner = new Scanner();
        List<Path> classFiles = MainSourceSet.classFiles();
        for (Path classFile : classFiles) {
            scanner.scanClassFile(Files.readAllBytes(classFile));
        }
        for (String bcClass : scanner.scannedClasses) {
            scanner.scanOverrides(bcClass);
        }
        int importsResolved = 0;
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            importsResolved += scanner.scanImports(file);
        }

        // Liveness: a scan that resolves nothing passes vacuously, so prove it saw real code on both sides.
        Assertions.assertTrue(classFiles.size() > 500,
            "expected the node's compiled main classes, found only " + classFiles.size() + " class files");
        Assertions.assertTrue(importsResolved > 5000,
            "expected thousands of resolvable imports in the node's main sources, resolved " + importsResolved);
        Assertions.assertTrue(scanner.resolvedTypes.contains("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen"),
            "client classes must be resolvable in the unit-test JVM, or every client-side reference goes unchecked");
        Assertions.assertTrue(scanner.resolvedTypes.contains("net.neoforged.neoforge.common.NeoForge"),
            "NeoForge classes must be resolvable in the unit-test JVM");
        // Every JEI plugin implements IModPlugin: if JEI ever drops off the test runtime, the JEI compat code (and the
        // PENDING entries below) would go silently unchecked rather than failing.
        Assertions.assertTrue(scanner.resolvedTypes.contains("mezz.jei.api.IModPlugin"),
            "JEI must be resolvable in the unit-test JVM, or every JEI reference goes unchecked");

        Map<String, Set<String>> violations = new TreeMap<>();
        Set<String> pendingHit = new TreeSet<>();
        scanner.findings.forEach((declaration, users) -> {
            Optional<String> pending = pendingKey(declaration);
            if (pending.isPresent()) {
                pendingHit.add(pending.get());
            } else {
                violations.put(declaration, users);
            }
        });
        if (!pendingHit.isEmpty()) {
            System.out.println("[ForRemovalApiGuard] still pending on this node: " + pendingHit);
        }

        if (!violations.isEmpty()) {
            StringBuilder msg = new StringBuilder("Main code uses API deprecated FOR REMOVAL on this node "
                + "(migrate it, or list it in ForRemovalApiGuardTester.PENDING with a reason):\n");
            violations.forEach((declaration, users) -> {
                msg.append("  ").append(declaration).append('\n');
                users.stream().limit(8).forEach(u -> msg.append("      <- ").append(u).append('\n'));
                if (users.size() > 8) {
                    msg.append("      ... and ").append(users.size() - 8).append(" more\n");
                }
            });
            Assertions.fail(msg.toString());
        }
    }

    /** The scanner itself, against fixtures whose every doomed use is suppressed exactly the way a real one could
     *  be — so a green main-code run means "nothing found", never "could not see". */
    @Test
    public void scannerSeesThroughSuppressionsAndOverrides() throws IOException {
        Scanner scanner = new Scanner();
        String user = FixtureUser.class.getName();
        try (InputStream in = FixtureUser.class.getResourceAsStream("/" + user.replace('.', '/') + ".class")) {
            Assertions.assertNotNull(in, "fixture class bytes");
            scanner.scanClassFile(in.readAllBytes());
        }
        scanner.scanOverrides(user);

        String fixture = Fixture.class.getName();
        Set<String> expected = Set.of(
            fixture + "#doomedMethod",
            fixture + "#doomedField",
            fixture + "$DoomedType",
            fixture + "$Base#doomedHook");
        for (String declaration : expected) {
            Assertions.assertTrue(scanner.findings.containsKey(declaration),
                "scanner missed " + declaration + "; it found " + scanner.findings.keySet());
        }
        Assertions.assertFalse(scanner.findings.containsKey(fixture + "#fineMethod"),
            "a merely-deprecated (not for removal) member is not this guard's business");

        // Import view: a doomed type imported but never used — the case javac cannot report at all.
        Scanner imports = new Scanner();
        imports.scanImports(new MainSourceSet.SourceFile("fixture/Imports.java",
            "package fixture;\n"
                + "/*import static " + fixture.replace('$', '.') + ".doomedField;*/\n"
                + "// import static " + fixture.replace('$', '.') + ".doomedField;\n"
                + "import " + fixture.replace('$', '.') + ".DoomedType;\n"
                + "import static " + fixture.replace('$', '.') + ".doomedMethod;\n"
                + "import static " + fixture.replace('$', '.') + ".ConstantHolder.DOOMED_CONSTANT;\n"
                + "class Imports {\n"
                + "    String sample = \"\"\"\n"
                + "import static " + fixture.replace('$', '.') + ".doomedField;\n"
                + "        \"\"\";\n"
                + "}\n"));
        Assertions.assertTrue(imports.findings.containsKey(fixture + "$DoomedType"), "unused doomed import missed");
        Assertions.assertTrue(imports.findings.containsKey(fixture + "#doomedMethod"), "doomed static import missed");
        Assertions.assertTrue(imports.findings.containsKey(fixture + "$DoomedConstants#DOOMED_CONSTANT"),
            "a doomed constant statically imported through an implementing class's interface was missed");
        Assertions.assertEquals(3, imports.findings.size(),
            "a commented-out import, or import text inside a literal, must not count: " + imports.findings);
    }

    /** Which {@link #PENDING} entry (if any) covers {@code declaration} — the entry itself, or its owning type. */
    private static Optional<String> pendingKey(String declaration) {
        if (PENDING.containsKey(declaration)) {
            return Optional.of(declaration);
        }
        int hash = declaration.indexOf('#');
        String type = hash < 0 ? declaration : declaration.substring(0, hash);
        while (true) {
            if (PENDING.containsKey(type)) {
                return Optional.of(type);
            }
            int dollar = type.lastIndexOf('$');
            if (dollar < 0) {
                return Optional.empty();
            }
            type = type.substring(0, dollar);
        }
    }

    // ─── The scanner ─────────────────────────────────────────────────────────────

    private static final class Scanner {
        /** Doomed declaration key -> the BuildCraft classes/files that use it. */
        final Map<String, Set<String>> findings = new TreeMap<>();
        final Set<String> scannedClasses = new LinkedHashSet<>();
        final Set<String> resolvedTypes = new HashSet<>();

        private final Map<String, Optional<Class<?>>> classCache = new HashMap<>();
        private final Map<Class<?>, Map<String, Optional<Executable>>> executableCache = new HashMap<>();

        private static final Pattern DESCRIPTOR_TYPE = Pattern.compile("L([\\w$]+(?:/[\\w$]+)+)[;<]");
        private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?([\\w$]+(?:\\s*\\.\\s*[\\w$]+)*)(\\s*\\.\\s*\\*)?\\s*;", Pattern.MULTILINE);

        // ── view 1: constant pool ──

        void scanClassFile(byte[] bytes) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) {
                throw new IOException("not a class file");
            }
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count = in.readUnsignedShort();
            int[] tags = new int[count];
            String[] utf8 = new String[count];
            int[] a = new int[count];
            int[] b = new int[count];
            Set<Integer> stringLiterals = new HashSet<>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                tags[i] = tag;
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 3, 4 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        i++;
                    }
                    case 7, 16, 19, 20 -> a[i] = in.readUnsignedShort();
                    case 8 -> stringLiterals.add(in.readUnsignedShort());
                    case 9, 10, 11, 12, 17, 18 -> {
                        a[i] = in.readUnsignedShort();
                        b[i] = in.readUnsignedShort();
                    }
                    case 15 -> {
                        in.readUnsignedByte();
                        a[i] = in.readUnsignedShort();
                    }
                    default -> throw new IOException("unknown constant-pool tag " + tag);
                }
            }
            in.readUnsignedShort(); // access
            String self = utf8[a[in.readUnsignedShort()]].replace('/', '.');
            scannedClasses.add(self);

            // Types: Class entries, plus every descriptor/signature (method params, field types, generics, annotations).
            for (int i = 1; i < count; i++) {
                if (tags[i] == 7) {
                    String name = utf8[a[i]];
                    if (name.startsWith("[")) {
                        collectDescriptorTypes(name, self);
                    } else {
                        checkType(name.replace('/', '.'), self);
                    }
                } else if (tags[i] == 1 && !stringLiterals.contains(i) && utf8[i].indexOf('/') >= 0) {
                    collectDescriptorTypes(utf8[i], self);
                }
            }
            // Members: field / method / interface-method references.
            for (int i = 1; i < count; i++) {
                if (tags[i] == 9 || tags[i] == 10 || tags[i] == 11) {
                    String owner = utf8[a[a[i]]];
                    if (owner.startsWith("[")) {
                        continue; // array clone() etc.
                    }
                    int nat = b[i];
                    String name = utf8[a[nat]];
                    String desc = utf8[b[nat]];
                    checkMember(owner.replace('/', '.'), name, desc, tags[i] == 9, self);
                }
            }
        }

        private void collectDescriptorTypes(String text, String user) {
            Matcher m = DESCRIPTOR_TYPE.matcher(text);
            while (m.find()) {
                checkType(m.group(1).replace('/', '.'), user);
            }
        }

        private void checkType(String binaryName, String user) {
            load(binaryName).ifPresent(type -> {
                Class<?> doomed = doomedTypeOf(type);
                if (doomed != null) {
                    record(doomed.getName(), user);
                }
            });
        }

        private void checkMember(String owner, String name, String desc, boolean field, String user) {
            Optional<Class<?>> ownerType = load(owner);
            if (ownerType.isEmpty()) {
                return;
            }
            AnnotatedElement member;
            Class<?> declaring;
            if (field) {
                Field f = findField(ownerType.get(), name);
                member = f;
                declaring = f == null ? null : f.getDeclaringClass();
            } else {
                Executable e = findExecutable(ownerType.get(), name, desc);
                member = e;
                declaring = e == null ? null : e.getDeclaringClass();
            }
            if (member == null) {
                return; // unresolvable (signature-polymorphic MethodHandle calls, missing optional deps)
            }
            if (isForRemoval(member)) {
                record(declaring.getName() + "#" + name, user);
            }
        }

        // ── view 2: overrides ──

        void scanOverrides(String bcClass) {
            Optional<Class<?>> type = load(bcClass);
            if (type.isEmpty()) {
                return;
            }
            Method[] declared;
            try {
                declared = type.get().getDeclaredMethods();
            } catch (LinkageError e) {
                return;
            }
            for (Method m : declared) {
                int mods = m.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isPrivate(mods)) {
                    continue;
                }
                for (Class<?> sup : supertypes(type.get())) {
                    Method overridden = declaredMethod(sup, m.getName(), m.getParameterTypes());
                    if (overridden != null && overridable(overridden, type.get()) && isForRemoval(overridden)) {
                        record(overridden.getDeclaringClass().getName() + "#" + m.getName(),
                            bcClass + " (overrides it)");
                    }
                }
            }
        }

        // ── view 3: imports ──

        /** @return how many imports resolved to a class (liveness). */
        int scanImports(MainSourceSet.SourceFile file) {
            String code = MainSourceSet.codeOnly(file.text());
            String user = file.relativePath() + " (import)";
            int resolved = 0;
            Matcher m = IMPORT.matcher(code);
            while (m.find()) {
                boolean isStatic = m.group(1) != null;
                boolean wildcard = m.group(3) != null;
                String[] parts = m.group(2).replaceAll("\\s+", "").split("\\.");
                if (!isStatic) {
                    if (wildcard) {
                        continue; // a package — nothing to check
                    }
                    Optional<Class<?>> type = resolveDotted(parts, parts.length);
                    if (type.isPresent()) {
                        resolved++;
                        checkType(type.get().getName(), user);
                    }
                } else {
                    int typeParts = wildcard ? parts.length : parts.length - 1;
                    Optional<Class<?>> type = resolveDotted(parts, typeParts);
                    if (type.isEmpty()) {
                        continue;
                    }
                    resolved++;
                    checkType(type.get().getName(), user);
                    if (!wildcard) {
                        String member = parts[parts.length - 1];
                        checkStaticImport(type.get(), member, user);
                    }
                }
            }
            return resolved;
        }

        private void checkStaticImport(Class<?> type, String member, String user) {
            // Superclasses AND interfaces: a static import can name an interface constant or static method.
            for (Class<?> c : withSupertypes(type)) {
                try {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getName().equals(member) && isForRemoval(f)) {
                            record(c.getName() + "#" + member, user);
                        }
                    }
                    for (Method mt : c.getDeclaredMethods()) {
                        if (mt.getName().equals(member) && isForRemoval(mt)) {
                            record(c.getName() + "#" + member, user);
                        }
                    }
                } catch (LinkageError ignored) {
                    // a missing optional dependency in one supertype's signatures — keep walking
                }
            }
        }

        /** {@code a.b.Outer.Inner} as written in source -> the class, trying each package/nesting split. */
        private Optional<Class<?>> resolveDotted(String[] parts, int length) {
            for (int pkg = length - 1; pkg >= 0; pkg--) {
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        name.append(i <= pkg ? '.' : '$');
                    }
                    name.append(parts[i]);
                }
                Optional<Class<?>> c = load(name.toString());
                if (c.isPresent()) {
                    return c;
                }
            }
            return Optional.empty();
        }

        // ── resolution helpers ──

        private void record(String declaration, String user) {
            findings.computeIfAbsent(declaration, k -> new TreeSet<>()).add(user);
        }

        private Optional<Class<?>> load(String binaryName) {
            return classCache.computeIfAbsent(binaryName, n -> {
                try {
                    Class<?> c = Class.forName(n, false, LOADER);
                    resolvedTypes.add(c.getName());
                    return Optional.of(c);
                } catch (ClassNotFoundException | LinkageError e) {
                    return Optional.empty();
                }
            });
        }

        /** The type itself, or the innermost enclosing type, that is deprecated for removal — else null. */
        private static Class<?> doomedTypeOf(Class<?> type) {
            try {
                for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
                    if (isForRemoval(c)) {
                        return c;
                    }
                }
            } catch (LinkageError ignored) {
                // enclosing-class metadata naming a missing class
            }
            return null;
        }

        private static boolean isForRemoval(AnnotatedElement element) {
            Deprecated d = element.getAnnotation(Deprecated.class);
            return d != null && d.forRemoval();
        }

        /** The field javac bound {@code owner.name} to: the owner's own, else the nearest supertype's. */
        private static Field findField(Class<?> owner, String name) {
            for (Class<?> c : withSupertypes(owner)) {
                try {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getName().equals(name)) {
                            return f;
                        }
                    }
                } catch (LinkageError ignored) {
                    // keep walking
                }
            }
            return null;
        }

        /** The method/constructor javac bound {@code owner.name desc} to — the most specific declaration. */
        private Executable findExecutable(Class<?> owner, String name, String desc) {
            return executableCache.computeIfAbsent(owner, k -> new HashMap<>())
                .computeIfAbsent(name + desc, k -> {
                    if (name.equals("<init>")) {
                        return Optional.ofNullable(matching(owner, name, desc, true));
                    }
                    for (Class<?> c : withSupertypes(owner)) {
                        Executable e = matching(c, name, desc, false);
                        if (e != null) {
                            return Optional.of(e);
                        }
                    }
                    return Optional.empty();
                }).orElse(null);
        }

        private static Executable matching(Class<?> c, String name, String desc, boolean constructor) {
            try {
                Executable[] candidates = constructor ? c.getDeclaredConstructors() : c.getDeclaredMethods();
                for (Executable e : candidates) {
                    if (!constructor && !e.getName().equals(name)) {
                        continue;
                    }
                    Class<?> ret = e instanceof Method mt ? mt.getReturnType() : void.class;
                    if (MethodType.methodType(ret, e.getParameterTypes()).toMethodDescriptorString().equals(desc)) {
                        return e;
                    }
                }
            } catch (LinkageError ignored) {
                // a missing optional dependency in some signature of c
            }
            return null;
        }

        /** Whether {@code m} can be overridden from {@code by}: not private or static, and package-private only
         *  within its own package. */
        private static boolean overridable(Method m, Class<?> by) {
            int mods = m.getModifiers();
            if (Modifier.isPrivate(mods) || Modifier.isStatic(mods)) {
                return false;
            }
            return Modifier.isPublic(mods) || Modifier.isProtected(mods)
                || m.getDeclaringClass().getPackageName().equals(by.getPackageName());
        }

        private static Method declaredMethod(Class<?> c, String name, Class<?>[] params) {
            try {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params)) {
                        return m;
                    }
                }
            } catch (LinkageError ignored) {
                // skip this supertype
            }
            return null;
        }

        /** {@code type} then its superclasses, then all of their interfaces breadth-first; Object last. */
        private static List<Class<?>> withSupertypes(Class<?> type) {
            List<Class<?>> out = new ArrayList<>();
            Set<Class<?>> seen = new HashSet<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                if (seen.add(c)) {
                    out.add(c);
                }
            }
            Deque<Class<?>> queue = new ArrayDeque<>(out);
            while (!queue.isEmpty()) {
                for (Class<?> i : queue.poll().getInterfaces()) {
                    if (seen.add(i)) {
                        out.add(i);
                        queue.add(i);
                    }
                }
            }
            if (seen.add(Object.class)) {
                out.add(Object.class);
            }
            return out;
        }

        private static List<Class<?>> supertypes(Class<?> type) {
            List<Class<?>> all = withSupertypes(type);
            return all.subList(1, all.size());
        }
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────────

    /** Declarations the scanner must flag (and one it must not). */
    static class Fixture {
        @Deprecated(forRemoval = true)
        static int doomedField = 1; // not final: a compile-time constant would be inlined and leave no reference

        @Deprecated(forRemoval = true)
        static void doomedMethod() {}

        @Deprecated
        static void fineMethod() {}

        @Deprecated(forRemoval = true)
        static final class DoomedType {}

        static class Base {
            @Deprecated(forRemoval = true)
            void doomedHook() {}
        }

        interface DoomedConstants {
            @Deprecated(forRemoval = true)
            int DOOMED_CONSTANT = 1;
        }

        static final class ConstantHolder implements DoomedConstants {}
    }

    /** Uses every fixture declaration with the warnings suppressed — the scanner must see through that. */
    @SuppressWarnings({"removal", "deprecation"})
    static final class FixtureUser extends Fixture.Base {
        @Override
        void doomedHook() {}

        int use() {
            Fixture.doomedMethod();
            Fixture.fineMethod();
            Object o = new Fixture.DoomedType();
            return Fixture.doomedField + o.hashCode();
        }
    }
}
