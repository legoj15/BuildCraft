# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
"""Static check of a Fabric node's mixins against that node's Minecraft jar, before any game run.

A Fabric mixin aimed at a method, call or member a given MC line does not have fails only at run time, one mixin per
crash. This lists every such miss at once: for each mixin in the node's Stonecutter view (main and gametest), it checks
that the target class exists, that each injector's `method` exists there, that each `target = "L...;name(...)"` call or
field access occurs inside that method, and that each @Shadow, @Accessor and @Invoker member exists on the class or a
superclass. It reads the bytecode with javap. It is a lead finder, not Mixin: slices, ordinals and @Local captures are
not checked, and a miss in a class the node never loads (another loader's mixin) is still reported.

Usage: python -I scripts/fabric-mixin-check.py <node> [--jar <minecraft jar>]
  <node>  a Fabric node, e.g. 26.2-fabric; its view must exist (./gradlew :<node>:stonecutterGenerate
          :<node>:stonecutterGenerateTest, or any compile of it).
  --jar   the Mojang-named Minecraft jar; found under the Gradle user home's fabric-loom cache (for the 1.x lines,
          the named merged jar in its minecraftMaven cache).
Exit status: 0 when nothing is missing, 1 otherwise.
"""
import argparse
import functools
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def find_jar(mc):
    home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    # Remap mode (the obfuscated 1.x lines): the minecraft-merged.jar beside the mappings is the obfuscated one, so
    # take Loom's Mojang-named merged jar from its maven cache (minecraft-merged-<mc>-loom.mappings..., not the
    # intermediary twin; the global cache's has no class tweaker applied, which a member lookup does not need).
    if mc.startswith("1."):
        for base in (home / "caches" / "fabric-loom" / "minecraftMaven", ROOT / ".gradle" / "loom-cache" / "minecraftMaven"):
            if base.is_dir():
                hits = sorted(p for p in base.rglob(f"minecraft-merged*-{mc}-loom.mappings*.jar")
                              if "intermediary" not in p.name and "sources" not in p.name)
                if hits:
                    return hits[-1]
        return None
    merged = home / "caches" / "fabric-loom" / mc / "minecraft-merged.jar"
    return merged if merged.is_file() else None


class Jar:
    def __init__(self, jar):
        self.jar = str(jar)

    @functools.lru_cache(maxsize=None)
    def javap(self, cls):
        r = subprocess.run(["javap", "-p", "-c", "-s", "-cp", self.jar, cls], capture_output=True, text=True)
        return r.stdout if r.returncode == 0 else None

    @functools.lru_cache(maxsize=None)
    def members(self, cls):
        """{name: [descriptor-ish signature lines]}, plus the superclass chain."""
        out = self.javap(cls)
        if out is None:
            return None
        return out

    def super_chain(self, cls):
        chain = []
        while cls:
            out = self.javap(cls)
            if out is None:
                break
            chain.append(cls)
            m = re.search(r"^(?:public |protected |private |abstract |final |sealed |non-sealed |static )*"
                          r"(?:class|interface|enum|record) \S+?(?:<.*?>)? extends ([\w.$]+)", out, re.M)
            cls = m.group(1) if m else None
        return chain

    def method_bodies(self, cls, name):
        """The javap -c bodies of every overload of `name` in `cls` (empty list when it has none)."""
        out = self.javap(cls)
        if out is None:
            return []
        bodies = []
        blocks = re.split(r"\n(?=  \S)", out)
        for b in blocks:
            head = b.strip().split("\n", 1)[0]
            simple = cls.rsplit(".", 1)[-1]
            if name == "<init>":
                if re.search(rf"[ .]{re.escape(simple)}\(", head):
                    bodies.append(b)
            elif name == "<clinit>":
                if head.startswith("static {}"):
                    bodies.append(b)
            elif re.search(rf"[ ]{re.escape(name)}\(", head):
                bodies.append(b)
        return bodies

    def has_member(self, cls, name):
        for c in self.super_chain(cls):
            out = self.javap(c)
            if re.search(rf"[ .]{re.escape(name)}[;(]", out) or re.search(rf" {re.escape(name)} =", out):
                return True
        return False


PRIMS = {"Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long", "F": "float", "D": "double",
         "V": "void"}


def desc_params(desc):
    """'(Lfoo/Bar;Z[I)V' -> ['foo.Bar', 'boolean', 'int[]'] (javap's source-type spelling, nested classes with $)."""
    inner = desc[desc.index("(") + 1:desc.index(")")]
    out, i = [], 0
    while i < len(inner):
        dims = 0
        while inner[i] == "[":
            dims += 1
            i += 1
        if inner[i] == "L":
            j = inner.index(";", i)
            t = inner[i + 1:j].replace("/", ".")
            i = j + 1
        else:
            t = PRIMS[inner[i]]
            i += 1
        out.append(t + "[]" * dims)
    return out


def head_params(head):
    m = re.search(r"\((.*)\)", head)
    if not m or not m.group(1).strip():
        return []
    return [re.sub(r"<.*?>", "", p).strip().replace("...", "[]") for p in m.group(1).split(",")]


def body_params(body):
    """A javap -c -s method block's parameters, from its descriptor line (the head's spelling when there is none)."""
    m = re.search(r"^\s+descriptor: (\(.*?\)\S*)\s*$", body, re.M)
    if m:
        return desc_params(m.group(1))
    return head_params(body.strip().split(chr(10), 1)[0])


IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)\s*;", re.M)
MIXIN_RE = re.compile(r"@Mixin\s*\((.*?)\)\s*\n\s*(?:public\s+|abstract\s+|final\s+)*(?:class|interface)", re.S)
INJ_RE = re.compile(r"@(Inject|WrapOperation|WrapWithCondition|ModifyReturnValue|ModifyVariable|ModifyArg|ModifyArgs|"
                    r"Redirect|ModifyConstant|ModifyExpressionValue|WrapMethod|ModifyReceiver)\s*\((.*?)\)\s*\n\s*"
                    r"(?:@[\w.]+(?:\(.*?\))?\s*)*(?:public |private |protected |static )*", re.S)
SHADOW_RE = re.compile(r"@Shadow(?:\s*\(.*?\))?\s*(?:@[\w.]+(?:\(.*?\))?\s*)*"
                       r"(?:public |private |protected |static |final |abstract )*[\w.<>,\[\] ?]+\s+(\w+)\s*[;(=]", re.S)
ACCESSOR_RE = re.compile(r"@(Accessor|Invoker)\s*\(\s*(?:value\s*=\s*)?\"([^\"]+)\"")


def resolve_class(token, imports, package):
    token = token.strip()
    if token.endswith(".class"):
        token = token[:-6]
    if "." in token and token[0].islower():
        return token
    head = token.split(".")[0]
    for imp in imports:
        if imp.endswith("." + head):
            return imp + token[len(head):].replace(".", "$")
    return f"{package}.{token}"


def targets_of(mixin_args, imports, package):
    out = []
    for m in re.finditer(r"targets\s*=\s*\{?([^}]*)\}?", mixin_args):
        out += [t.replace("/", ".") for t in re.findall(r"\"([^\"]+)\"", m.group(1))]
    stripped = re.sub(r"targets\s*=\s*\{?[^}]*\}?", "", mixin_args)
    stripped = re.sub(r"(priority|remap)\s*=\s*[^,)]+", "", stripped)
    out += [resolve_class(t, imports, package) for t in re.findall(r"([\w.]+)\.class", stripped)]
    return out


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def check_file(path, jar, problems):
    raw = path.read_text(encoding="utf-8")
    text = strip_comments(raw)
    if "@Mixin" not in text:
        return 0
    package = re.search(r"^\s*package\s+([\w.]+)", text, re.M).group(1)
    imports = IMPORT_RE.findall(text)
    m = MIXIN_RE.search(text)
    if not m:
        return 0
    classes = targets_of(m.group(1), imports, package)
    rel = path.name
    checked = 0
    for cls in classes:
        if not cls.startswith(("net.minecraft.", "com.mojang.")):
            continue  # another mod's class (Fabric API's own): not in the Minecraft jar
        if jar.javap(cls) is None:
            problems.append(f"{rel}: target class {cls} not in the jar")
            continue
        for im in INJ_RE.finditer(text):
            args = im.group(2)
            methods = []
            mm = re.search(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", args)
            if mm:
                methods = re.findall(r"\"([^\"]+)\"", mm.group(1))
            tgt = re.findall(r"target\s*=\s*\"(L[^\"]+)\"", args)
            for meth in methods:
                checked += 1
                name = re.split(r"[(]", meth)[0]
                desc = meth[len(name):]
                bodies = jar.method_bodies(cls, name)
                if not bodies:
                    problems.append(f"{rel}: {cls} has no method {name} (@{im.group(1)})")
                    continue
                if desc:
                    # By the JVM descriptor javap -s prints under each head: a generic parameter's source spelling
                    # (T) is not its erasure.
                    want = desc_params(desc)
                    bodies = [b for b in bodies if body_params(b) == want]
                    if not bodies:
                        problems.append(f"{rel}: {cls} has no {name}{desc} (@{im.group(1)})")
                        continue
                for t in tgt:
                    tm = re.match(r"L([\w/$]+);([\w<>$]+)(\(.*|:.*)?", t)
                    if not tm:
                        continue
                    owner, member, sig = tm.group(1).replace("/", "."), tm.group(2), tm.group(3) or ""
                    owner_slash = tm.group(1)
                    needle_sig = sig[1:] if sig.startswith(":") else sig
                    found = False
                    for b in bodies:
                        for line in b.split("\n"):
                            if "// " not in line:
                                continue
                            ref = line.split("// ", 1)[1]
                            ref = re.sub(r"^(Method|InterfaceMethod|Field)\s+", "", ref)
                            if (ref.startswith(f"{owner_slash}.{member}:") or ref.startswith(f"{member}:")
                                    or ref.startswith(f"\"{member}\":")):
                                rsig = ref.split(":", 1)[1].strip()
                                if not needle_sig or rsig == needle_sig:
                                    found = True
                                    break
                        if found:
                            break
                    if not found:
                        problems.append(f"{rel}: no {owner.rsplit('.', 1)[-1]}.{member}{sig} inside {cls.rsplit('.', 1)[-1]}.{name}"
                                        f" (@{im.group(1)})")
        for sm in SHADOW_RE.finditer(text):
            checked += 1
            if not jar.has_member(cls, sm.group(1)):
                problems.append(f"{rel}: @Shadow {sm.group(1)} not on {cls} or its superclasses")
        for am in ACCESSOR_RE.finditer(text):
            checked += 1
            if not jar.has_member(cls, am.group(2)):
                problems.append(f"{rel}: @{am.group(1)} {am.group(2)} not on {cls} or its superclasses")
    return checked


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("node")
    ap.add_argument("--jar")
    a = ap.parse_args()
    props = (ROOT / "versions" / a.node / "gradle.properties").read_text(encoding="utf-8")
    mc = re.search(r"^minecraft_version=(\S+)", props, re.M).group(1)
    jar = Path(a.jar) if a.jar else find_jar(mc)
    if not jar or not jar.is_file():
        sys.exit(f"error: no Minecraft jar for {mc}; pass --jar")
    views = [ROOT / "versions" / a.node / "build" / "generated" / "stonecutter" / s / "java" for s in ("main", "test")]
    missing = [v for v in views if not v.is_dir()]
    if missing:
        sys.exit(f"error: {missing[0]} does not exist; run ./gradlew :{a.node}:stonecutterGenerate "
                 f":{a.node}:stonecutterGenerateTest first")
    j = Jar(jar)
    problems, files, checks = [], 0, 0
    for v in views:
        for p in sorted(v.rglob("*.java")):
            if "/fabric/" not in p.as_posix() or "mixin" not in p.as_posix():
                continue
            n = check_file(p, j, problems)
            if n or "@Mixin" in p.read_text(encoding="utf-8"):
                files += 1
                checks += n
    print(f"fabric mixin check ({a.node}, {jar.name}): {files} mixins, {checks} references, {len(problems)} missing")
    for pr in problems:
        print("  " + pr)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
