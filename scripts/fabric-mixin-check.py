# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
"""Static check of a Fabric node's mixins against that node's Minecraft jar, before any game run.

A Fabric mixin aimed at a method, call, member or type a given MC line does not have fails only at run time, one mixin
per crash. This lists every such miss at once. For each mixin in the node's Stonecutter view (main and gametest) it
checks, against the bytecode (javap) of the Mojang-named Minecraft jar:

  names      the target class exists; each injector's `method` exists there (overloads by descriptor when given); each
             `target = "L...;name(...)"` call, field access or `new` occurs inside that method; each @Shadow, @Accessor
             and @Invoker member exists on the class or a supertype.
  types      a @Shadow/@Accessor field's type equals the target field's; a @Shadow/@Invoker method's parameters, return
             type and static-ness equal the target method's; an @Inject handler's leading parameters (when it takes the
             method's arguments at all) and its CallbackInfo/CallbackInfoReturnable kind match the target method; a
             @WrapOperation/@WrapWithCondition/@Redirect/@ModifyExpressionValue/@ModifyReturnValue/@ModifyArg/@WrapMethod
             handler's parameters and return type match the invoked method, field access, `new` or wrapped method.
  ordinals   `@At(..., ordinal = N)` has an N-th matching instruction (INVOKE/INVOKE_ASSIGN/FIELD/NEW/RETURN) in the
             target method; skipped when the injector has a @Slice.

Problems (exit 1) are only what is certain: a name that is not there, a primitive or class that differs, a class pair the
jar proves unrelated, an ordinal past the last match. What it cannot resolve confidently (a type outside the jar and the
JDK, a type variable, an interface pair, a handler parameter type that is only a super- or subtype of the expected one
where the injector may allow it, a dynamic annotation value) is a warning and does not change the exit status. It is
still a lead finder, not Mixin: @Local captures, local-variable ordinals, constants and slices are not checked, and a
target outside net.minecraft / com.mojang (another mod's class, such as Fabric API's) is skipped.

Readiness: it reads the Stonecutter view (versions/<node>/build/generated/stonecutter/{main,test}/java), which
./gradlew :<node>:compileJava :<node>:compileGametestJava (or :<node>:stonecutterGenerate
:<node>:stonecutterGenerateTest) writes IN PLACE, a file at
a time, over several seconds; read mid-write it silently undercounts. So it refuses (exit 2, nothing checked) when
  - a Gradle wrapper client of THIS checkout is running (its gradle-wrapper.jar is on a live java command line), or
  - the view is missing a file that src/{main,test}/java has (never generated, or generation not finished), or
  - a view file was written in the last 3 s (it waits up to --wait seconds, default 15, for that to clear), or
  - a mixin file it reads is cut off mid-write (its class body does not close).
It cannot see a build that starts after it. A mixin file in src/ newer than its generated copy (edited, not compiled
since) is only a warning, as a save that changed nothing looks the same: compile first. --no-ready-check skips the
refusal (a long run/test task of this checkout would keep the first rule firing).

Usage: python -I scripts/fabric-mixin-check.py <node> [--jar <minecraft jar>] [--wait SECONDS] [--no-ready-check] [-v]
  <node>  a Fabric node, e.g. 26.2-fabric; its view must exist (./gradlew :<node>:compileJava :<node>:compileGametestJava, run first; the gametest compile writes the test view its test mixins live in).
  --jar   the Mojang-named Minecraft jar; found under the Gradle user home's fabric-loom cache (for the 1.x lines,
          the named merged jar in its minecraftMaven cache).
  -v      also list every check that passed (the audit trail), not just problems and warnings.
Exit status: 0 when no problem was found, 1 when one was, 2 when it refused to run (or could not start).
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from collections import namedtuple
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QUIET_SECONDS = 3.0


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


# ---------------------------------------------------------------------------------------------------------------------
# Types. A Ty is a JVM type: primitives by descriptor letter, classes by internal name (net/minecraft/Foo$Bar); a source
# type the checker cannot resolve is a class whose base starts with "?" and compares as unknown.

Ty = namedtuple("Ty", "dims base ref")

PRIM_LETTER = {"boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J", "float": "F",
               "double": "D", "void": "V"}
LETTER_NAME = {v: k for k, v in PRIM_LETTER.items()}
OBJECT = "java/lang/Object"
VOID = Ty(0, "V", False)
BOOLEAN = Ty(0, "Z", False)


def ty_name(t):
    if t.ref:
        n = t.base[1:] if t.base.startswith("?") else t.base.rsplit("/", 1)[-1].replace("$", ".")
    else:
        n = LETTER_NAME[t.base]
    return n + "[]" * t.dims


def unresolved(t):
    return t.ref and t.base.startswith("?")


def parse_desc(desc):
    """'(Lfoo/Bar;Z[I)V' -> ([Ty, ...], Ty); a field descriptor 'F' -> ([], Ty)."""
    def one(i):
        dims = 0
        while desc[i] == "[":
            dims += 1
            i += 1
        if desc[i] == "L":
            j = desc.index(";", i)
            return Ty(dims, desc[i + 1:j], True), j + 1
        return Ty(dims, desc[i], False), i + 1

    if not desc.startswith("("):
        return [], one(0)[0]
    params, i = [], 1
    while desc[i] != ")":
        t, i = one(i)
        params.append(t)
    return params, one(i + 1)[0]


def show_sig(params, ret):
    return f"({', '.join(ty_name(p) for p in params)}){ty_name(ret)}"


# ---------------------------------------------------------------------------------------------------------------------
# The jar, through javap.

Insn = namedtuple("Insn", "idx op owner name desc")
JField = namedtuple("JField", "name desc static")
INSN_RE = re.compile(r"^\s+(\d+): ([a-z]\w*)\s*(.*)$")
REF_RE = re.compile(r'^(?:(?P<owner>"[^"]+"|[^.:"\s]+)\.)?(?P<name>"[^"]+"|[^:"\s]+):(?P<desc>\S+)$')
REF_OPS = {"invokevirtual", "invokespecial", "invokestatic", "invokeinterface", "getfield", "putfield", "getstatic",
           "putstatic"}
INVOKE_OPS = {"invokevirtual", "invokespecial", "invokestatic", "invokeinterface"}
RETURN_OPS = {"ireturn", "lreturn", "freturn", "dreturn", "areturn", "return"}


class JMethod:
    def __init__(self, cls, name, desc, static, abstract, block):
        self.cls, self.name, self.desc, self.static, self.abstract = cls, name, desc, static, abstract
        self.block = block
        self._insns = None

    @property
    def insns(self):
        if self._insns is None:
            out = []
            for line in self.block.split("\n"):
                m = INSN_RE.match(line)
                if not m:
                    continue
                op, rest = m.group(2), m.group(3)
                owner = name = desc = None
                if op in REF_OPS or op == "new":
                    comment = rest.split("// ", 1)[1] if "// " in rest else ""
                    if op == "new":
                        cm = re.match(r'^class\s+"?([^"\s]+)"?$', comment)
                        owner = cm.group(1) if cm else None
                    else:
                        rm = REF_RE.match(re.sub(r"^(?:Method|InterfaceMethod|Field)\s+", "", comment))
                        if rm:
                            owner = (rm.group("owner") or self.cls).strip('"')
                            name, desc = rm.group("name").strip('"'), rm.group("desc")
                out.append(Insn(int(m.group(1)), op, owner, name, desc))
            self._insns = out
        return self._insns


class JClass:
    def __init__(self, name, interface, supers, fields, methods):
        self.name, self.interface, self.supers, self.fields, self.methods = name, interface, supers, fields, methods


def strip_generics(s):
    prev = None
    while prev != s:
        prev, s = s, re.sub(r"<[^<>]*>", "", s)
    return s


def parse_javap(out, key):
    lines = out.split("\n")
    header = next((ln for ln in lines if re.search(r"\b(class|interface)\s+\S+", ln) and ln.rstrip().endswith("{")), None)
    if header is None:
        return None
    h = strip_generics(header)
    interface = bool(re.search(r"\binterface\s+\S+", h))
    supers = []
    em = re.search(r"\bextends\s+(\S+)", h)
    if em:
        supers += em.group(1).split(",")
    im = re.search(r"\bimplements\s+(\S+)", h)
    if im:
        supers += im.group(1).split(",")
    supers = [s.replace(".", "/") for s in supers if s]
    dotted = key.replace("/", ".")
    fields, methods = [], []
    blocks, cur = [], None
    for ln in lines:
        if re.match(r"^  \S", ln):
            cur = [ln]
            blocks.append(cur)
        elif cur is not None and ln.startswith("    "):
            cur.append(ln)
    for b in blocks:
        head = b[0].strip()
        dm = next((re.match(r"^\s+descriptor: (\S+)\s*$", ln) for ln in b[1:] if ln.lstrip().startswith("descriptor:")), None)
        if not dm:
            continue
        desc = dm.group(1)
        if not desc.startswith("("):
            nm = re.search(r"([\w$]+);\s*$", head)
            if nm:
                fields.append(JField(nm.group(1), desc, bool(re.search(r"\bstatic\b", head))))
            continue
        if head.startswith("static {}"):
            name, static = "<clinit>", True
        else:
            before = head[:head.index("(")].strip()
            token = before.split()[-1]
            name = "<init>" if token == dotted else token
            static = bool(re.search(r"\bstatic\b", strip_generics(before)))
        methods.append(JMethod(key, name, desc, static, bool(re.search(r"\babstract\b", head)), "\n".join(b)))
    return JClass(key, interface, supers, fields, methods)


def find_javap():
    exe = shutil.which("javap")
    if exe:
        return exe
    home = os.environ.get("JAVA_HOME")
    for cand in ([Path(home) / "bin" / ("javap.exe" if os.name == "nt" else "javap")] if home else []):
        if cand.is_file():
            return str(cand)
    return None


class Jar:
    def __init__(self, jar, javap):
        self.jar, self.javap = str(jar), javap
        self._cache = {}

    def known(self):
        return self._cache.keys()

    def _load(self, key):
        r = subprocess.run([self.javap, "-p", "-c", "-s", "-cp", self.jar, key.replace("/", ".")],
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
        return parse_javap(r.stdout, key) if r.returncode == 0 else None

    def get(self, name):
        key = name.replace(".", "/")
        if key not in self._cache:
            self._cache[key] = self._load(key)
        return self._cache[key]

    def prefetch(self, names):
        todo = sorted({n.replace(".", "/") for n in names} - self._cache.keys())
        with ThreadPoolExecutor(max_workers=6) as ex:
            for key, res in zip(todo, ex.map(self._load, todo)):
                self._cache[key] = res

    def ancestors(self, name):
        """(names from `name` up through every supertype javap can read, True when none was unreadable)."""
        seen, queue, complete = [name], [name], True
        while queue:
            c = self.get(queue.pop(0))
            if c is None:
                complete = False
                continue
            for s in c.supers:
                if s not in seen:
                    seen.append(s)
                    queue.append(s)
        if OBJECT not in seen:
            seen.append(OBJECT)
        return seen, complete

    def is_subtype(self, sub, sup):
        """True / False when the hierarchy of `sub` is fully readable / None when it is not."""
        names, complete = self.ancestors(sub)
        if sup in names:
            return True
        return False if complete else None

    def find_field(self, cls, name):
        names, complete = self.ancestors(cls)
        hits = []
        for n in names:
            c = self.get(n)
            if c:
                hits += [(n, f) for f in c.fields if f.name == name]
        return hits, complete

    def find_methods(self, cls, name):
        names, complete = self.ancestors(cls)
        hits = []
        for n in names:
            c = self.get(n)
            if c:
                hits += [(n, m) for m in c.methods if m.name == name]
        return hits, complete

    def is_interface(self, name):
        c = self.get(name)
        return bool(c and c.interface)


EXACT, WIDEN, NARROW, UNRELATED, UNKNOWN = "exact", "widen", "narrow", "unrelated", "unknown"


def relate(jar, h, a):
    """How a handler-declared type `h` stands to the type `a` the target has."""
    if unresolved(h) or unresolved(a):
        return UNKNOWN
    if h == a:
        return EXACT
    if h.dims != a.dims:
        if not h.dims and h.base == OBJECT and h.ref:
            return WIDEN
        return UNRELATED
    if not h.ref or not a.ref:
        return UNRELATED
    if h.base == OBJECT:
        return WIDEN
    up = jar.is_subtype(a.base, h.base)
    if up:
        return WIDEN
    down = jar.is_subtype(h.base, a.base)
    if down:
        return NARROW
    if up is None or down is None or jar.is_interface(h.base) or jar.is_interface(a.base):
        return UNKNOWN
    return UNRELATED


# ---------------------------------------------------------------------------------------------------------------------
# Java source, just enough of it: comments out, annotations with their arguments, class members.

def skip_literal(text, i):
    q = text[i]
    j = i + 1
    while j < len(text) and text[j] != q:
        j += 2 if text[j] == "\\" else 1
    return j + 1


def strip_comments(text):
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(text[i:j])
            i = j
        elif c in "\"'":
            j = skip_literal(text, i)
            out.append(text[i:j])
            i = j
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


CLOSER = {"(": ")", "{": "}", "[": "]"}


def match_close(text, i):
    """Index of the bracket closing the one at text[i]; -1 when it does not."""
    stack, n = [], len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            i = skip_literal(text, i)
            continue
        if c in CLOSER:
            stack.append(CLOSER[c])
        elif c in ")}]":
            if not stack or stack.pop() != c:
                return -1
            if not stack:
                return i
        i += 1
    return -1


def split_top(text, angle=False):
    """Split on commas outside brackets, strings and (with `angle`) generics."""
    parts, depth, start, i, n = [], 0, 0, 0, len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            i = skip_literal(text, i)
            continue
        if c in "({[" or (angle and c == "<"):
            depth += 1
        elif c in ")}]" or (angle and c == ">"):
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(text[start:i])
            start = i + 1
        i += 1
    parts.append(text[start:])
    return [p.strip() for p in parts if p.strip()]


Anno = namedtuple("Anno", "name args")
Param = namedtuple("Param", "annos type name")
Member = namedtuple("Member", "kind annos mods type name params line")
MODIFIERS = {"public", "private", "protected", "static", "final", "abstract", "synchronized", "native", "default",
             "strictfp", "transient", "volatile", "sealed", "non-sealed"}


def read_annos(h):
    """Leading annotations and modifiers of a declaration header -> (annos, mods, rest)."""
    annos, mods, i = [], set(), 0
    while True:
        while i < len(h) and h[i].isspace():
            i += 1
        if i < len(h) and h[i] == "@" and not h.startswith("@interface", i):
            m = re.match(r"@([\w.]+)", h[i:])
            i += m.end()
            j = i
            while j < len(h) and h[j].isspace():
                j += 1
            args = ""
            if j < len(h) and h[j] == "(":
                k = match_close(h, j)
                args = h[j + 1:k]
                i = k + 1
            annos.append(Anno(m.group(1).rsplit(".", 1)[-1], args))
            continue
        m = re.match(r"(non-sealed|[a-z]+)\b", h[i:])
        if m and m.group(1) in MODIFIERS:
            mods.add(m.group(1))
            i += m.end()
            continue
        return annos, mods, h[i:].strip()


def parse_member(header, line):
    annos, mods, rest = read_annos(header)
    if re.match(r"(class|interface|enum|record|@interface)\b", rest) or not rest:
        return None
    paren = rest.find("(")
    if paren < 0:  # a field
        m = re.match(r"^(.*?)\s+([\w$]+)$", rest, re.S)
        return Member("field", annos, mods, m.group(1).strip(), m.group(2), [], line) if m else None
    k = match_close(rest, paren)
    head, plist = rest[:paren].strip(), rest[paren + 1:k]
    m = re.match(r"^(.*?)\s*([\w$]+)$", head, re.S)
    if not m:
        return None
    ret = re.sub(r"^<[^>]*>\s*", "", m.group(1)).strip()
    params = []
    for p in split_top(plist, angle=True):
        pa, _, pr = read_annos(p)
        pm = re.match(r"^(.*?)\s*([\w$]+)$", pr, re.S)
        if pm:
            params.append(Param(pa, pm.group(1).strip(), pm.group(2)))
    return Member("method", annos, mods, ret, m.group(2), params, line)


def class_members(text, body_start, body_end):
    """The declarations at depth 1 of the class body text[body_start:body_end]."""
    members, i = [], body_start
    while i < body_end:
        while i < body_end and (text[i].isspace() or text[i] == ";"):
            i += 1
        if i >= body_end:
            break
        start, j = i, i
        while j < body_end and text[j] not in ";{=":
            if text[j] in "\"'":
                j = skip_literal(text, j)
            elif text[j] in "([":
                k = match_close(text, j)
                j = body_end if k < 0 else k + 1
            else:
                j += 1
        term = text[j] if j < body_end else ";"
        line = text.count("\n", 0, start + len(text[start:j]) - len(text[start:j].lstrip())) + 1
        mem = parse_member(text[start:j], line)
        if term == "{":
            k = match_close(text, j)
            i = body_end if k < 0 else k + 1
        elif term == "=":
            k = j
            while k < body_end and text[k] != ";":
                k = (match_close(text, k) + 1 or body_end) if text[k] in "({[" else (skip_literal(text, k) if text[k] in "\"'" else k + 1)
            i = k + 1
        else:
            i = j + 1
        if mem is not None:
            members.append(mem)
    return members


def anno_args(argtext):
    """The `key = value` pairs of an annotation's arguments (a lone value is `value`), values as source text."""
    out = {}
    for part in split_top(argtext):
        m = re.match(r"^(\w+)\s*=(?!=)\s*(.*)$", part, re.S)
        if m:
            out[m.group(1)] = m.group(2).strip()
        else:
            out["value"] = part
    return out


def string_list(value):
    """['a', 'b'] from "a" / {"a", "b"}; None when any element is not a plain string literal."""
    if value is None:
        return []
    v = value.strip()
    items = split_top(v[1:-1]) if v.startswith("{") and v.endswith("}") else [v]
    out = []
    for it in items:
        if not re.fullmatch(r'"(?:[^"\\]|\\.)*"', it):
            return None
        out.append(it[1:-1].replace('\\"', '"'))
    return out


def at_list(value):
    """The @At(...) annotations of an `at = ...` value as argument dicts."""
    if value is None:
        return []
    v = value.strip()
    items = split_top(v[1:-1]) if v.startswith("{") and v.endswith("}") else [v]
    out = []
    for it in items:
        m = re.match(r"^@(?:[\w.]*\.)?At\s*\((.*)\)$", it, re.S)
        if m:
            out.append(anno_args(m.group(1)))
    return out


def all_ats(argtext):
    """Every @At(...) argument dict anywhere inside an annotation's argument text (slices included)."""
    out = []
    for m in re.finditer(r"@(?:[\w.]*\.)?At\s*\(", argtext):
        k = match_close(argtext, m.end() - 1)
        if k > 0:
            out.append(anno_args(argtext[m.end():k]))
    return out


class Source:
    """One mixin class of a file: its package and imports (for resolving names), targets and members."""

    def __init__(self, path, text):
        self.path = path
        self.package = (re.search(r"^\s*package\s+([\w.]+)", text, re.M) or [None, ""])[1]
        self.imports = {}
        self.wildcards = []
        for imp in re.findall(r"^\s*import\s+([\w.]+(?:\.\*)?)\s*;", text, re.M):
            if imp.endswith(".*"):
                self.wildcards.append(imp[:-2])
            else:
                self.imports[imp.rsplit(".", 1)[-1]] = imp
        self.text = text


def fq_to_internal(dotted):
    """a.b.Outer.Inner -> a/b/Outer$Inner (the first capitalised segment starts the class names)."""
    parts = dotted.split(".")
    k = next((i for i, p in enumerate(parts) if p[:1].isupper()), len(parts) - 1)
    return "/".join(parts[:k]) + ("/" if k else "") + "$".join(parts[k:])


class Resolver:
    def __init__(self, src, jar):
        self.src, self.jar = src, jar

    def class_of(self, token):
        token = token.strip()
        parts = token.split(".")
        if not parts[0]:
            return None
        if parts[0][0].islower():
            return fq_to_internal(token)
        head, rest = parts[0], parts[1:]
        if head in self.src.imports:
            return fq_to_internal(self.src.imports[head]) + "".join("$" + p for p in rest)
        for pkg in [self.src.package, "java.lang", *self.src.wildcards]:
            cand = pkg.replace(".", "/") + "/" + head
            if self.jar.get(cand) is not None:
                return cand + "".join("$" + p for p in rest)
        return None

    def ty(self, text, tparams=()):
        t = strip_generics(re.sub(r"@\w+(?:\([^)]*\))?\s*", "", text)).strip()
        t = re.sub(r"\bfinal\b\s*", "", t).strip()
        dims = 0
        if t.endswith("..."):
            dims, t = 1, t[:-3].strip()
        while t.endswith("[]"):
            dims, t = dims + 1, t[:-2].strip()
        if t in PRIM_LETTER:
            return Ty(dims, PRIM_LETTER[t], False)
        if t in tparams or not re.fullmatch(r"[\w.$]+", t) or (re.fullmatch(r"[A-Z][A-Z0-9]?", t) and t not in self.src.imports):
            return Ty(dims, "?" + t, True)  # not a name (or a type variable such as T): its erasure is unknown
        c = self.class_of(t)
        return Ty(dims, c if c else "?" + t, True)


# ---------------------------------------------------------------------------------------------------------------------
# Mixin annotation semantics.

INJECTORS = {"Inject", "WrapOperation", "WrapWithCondition", "ModifyReturnValue", "ModifyVariable", "ModifyArg",
             "ModifyArgs", "Redirect", "ModifyConstant", "ModifyExpressionValue", "WrapMethod", "ModifyReceiver"}
CI_CLASSES = {"org/spongepowered/asm/mixin/injection/callback/CallbackInfo",
              "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"}
OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation"
FIELD_OPS = {"GETSTATIC": "getstatic", "PUTSTATIC": "putstatic", "GETFIELD": "getfield", "PUTFIELD": "putfield",
             "178": "getstatic", "179": "putstatic", "180": "getfield", "181": "putfield"}
# Mixin's own injectors (@Inject, @Redirect, @ModifyArg) and every @Shadow/@Accessor/@Invoker take exactly the target's
# types unless the parameter is @Coerce'd. MixinExtras' wrappers are looser about reference types, and I have not
# confirmed how much, so for them a handler type that is only a SUPERTYPE of the expected one is a warning, not a
# problem; a subtype or an unrelated class still cannot work and stays a problem.
LENIENT = {"WrapOperation", "WrapWithCondition", "ModifyExpressionValue", "ModifyReturnValue", "WrapMethod"}

MemberInfo = namedtuple("MemberInfo", "owner name desc field")


def parse_member_info(s):
    """Mixin's `Lowner;name(desc)ret` / `Lowner;name:desc` / `Lowner;` / `owner/Name` reference."""
    m = re.fullmatch(r"(?:L([\w/$]+);)?([\w$<>]*)(?:(\([^)]*\)\S*)|:(\S+))?", s)
    if m:
        return MemberInfo(m.group(1), m.group(2) or None, m.group(3) or m.group(4), m.group(4) is not None)
    if re.fullmatch(r"[\w/$]+", s) and "/" in s:
        return MemberInfo(s, None, None, False)
    return None


def desc_matches(want, got):
    """A written descriptor (maybe without its return type) against a real one."""
    return want is None or got == want or (want.endswith(")") and got.startswith(want)) or \
        (want.startswith("(") and ")" in want and got == want)


def find_matches(mi, kind, methods, opcode):
    """The instructions of the target methods an `At` of this kind with this reference selects."""
    out = []
    for m in methods:
        for ins in m.insns:
            if kind in ("INVOKE", "INVOKE_ASSIGN"):
                if ins.op not in INVOKE_OPS:
                    continue
            elif kind == "FIELD":
                if ins.op not in FIELD_OPS.values() or (opcode and ins.op != opcode):
                    continue
            elif kind == "NEW":
                if mi.name and mi.name == "<init>":
                    if not (ins.op == "invokespecial" and ins.name == "<init>"):
                        continue
                elif ins.op != "new":
                    continue
            else:
                continue
            if mi.owner and ins.owner != mi.owner:
                continue
            if kind != "NEW" and mi.name and ins.name != mi.name:
                continue
            if kind == "NEW" and mi.name == "<init>" and ins.name != "<init>":
                continue
            if mi.desc is not None and ins.desc is not None and not desc_matches(mi.desc, ins.desc):
                continue
            out.append((m, ins))
    return out


class Report:
    def __init__(self):
        self.problems, self.warnings, self.notes, self.checks = [], [], [], 0

    def fail(self, ctx, msg):
        self.problems.append(f"{ctx}: {msg}")

    def warn(self, ctx, msg):
        self.warnings.append(f"{ctx}: {msg}")

    def note(self, ctx, msg):
        self.notes.append(f"{ctx}: {msg}")


class MixinChecker:
    def __init__(self, jar, src, rep):
        self.jar, self.src, self.rep = jar, src, rep
        self.R = Resolver(src, jar)

    # -- helpers ----------------------------------------------------------------------------------------------------
    def verdict(self, rel, strict=True):
        if rel == EXACT:
            return "ok"
        if rel == UNKNOWN:
            return "warn"
        if rel == WIDEN and not strict:
            return "warn"
        return "fail"

    def compare(self, what, handler_ty, actual_ty, strict, ctx, has="the target has"):
        """Compare one type; report it. Returns True when it is fine (no failure)."""
        rel = relate(self.jar, handler_ty, actual_ty)
        v = self.verdict(rel, strict)
        if v == "ok":
            self.rep.note(ctx, f"ok {what}: {ty_name(handler_ty)}")
            return True
        msg = f"{what} is {ty_name(handler_ty)} but {has} {ty_name(actual_ty)}"
        if v == "warn":
            why = {UNKNOWN: "cannot tell whether they match", WIDEN: "handler type is a supertype", NARROW: "handler type is a subtype"}[rel]
            self.rep.warn(ctx, f"{msg} ({why})")
            return True
        self.rep.fail(ctx, msg)
        return False

    def tys(self, member_params, tparams=()):
        return [None if any(a.name == "Coerce" for a in p.annos) else self.R.ty(p.type, tparams) for p in member_params]

    def compare_list(self, what, handler, expected, strict, ctx, owner_note=""):
        """Handler parameter types against expected ones, position by position. True when none failed."""
        ok = True
        if len(handler) != len(expected):
            self.rep.fail(ctx, f"{what} takes {len(handler)} parameter(s) ({', '.join(ty_name(t) if t else 'coerced' for t in handler)}) "
                               f"but the target has {len(expected)} ({', '.join(ty_name(t) for t in expected)}){owner_note}")
            return False
        for i, (h, a) in enumerate(zip(handler, expected)):
            if h is None:
                continue  # @Coerce: any supertype is allowed
            ok &= self.compare(f"{what} parameter {i + 1}", h, a, strict, ctx)
        return ok

    # -- mixin class ------------------------------------------------------------------------------------------------
    def targets(self, mixin_args):
        """The classes (internal names) a @Mixin's `value` and `targets` name."""
        out = []
        for m in re.finditer(r"targets\s*=\s*(\{[^}]*\}|\"[^\"]*\")", mixin_args):
            for t in re.findall(r"\"([^\"]+)\"", m.group(1)):
                if t.startswith("L") and t.endswith(";"):  # descriptor form, Lnet/minecraft/Foo;
                    t = t[1:-1]
                out.append(t.replace(".", "/"))
        stripped = re.sub(r"targets\s*=\s*(\{[^}]*\}|\"[^\"]*\")", "", mixin_args)
        for t in re.findall(r"([\w.$]+)\.class", stripped):
            c = self.R.class_of(t)
            if c:
                out.append(c)
            else:
                self.rep.warn(self.src.path.name, f"cannot resolve mixin target {t}.class")
        # another mod's class (Fabric API's, say) is not in the Minecraft jar: nothing to check it against
        return [t for t in out if t.startswith(("net/minecraft/", "com/mojang/"))]

    def check(self, mixin_args, members, class_name):
        src, jar, rep = self.src, self.jar, self.rep
        for cls in self.targets(mixin_args):
            jc = jar.get(cls)
            if jc is None:
                rep.fail(src.path.name, f"target class {cls.replace('/', '.')} not in the jar")
                continue
            for mem in members:
                ctx = f"{src.path.name}:{mem.line} {class_name}.{mem.name}"
                for an in mem.annos:
                    try:
                        if an.name in INJECTORS:
                            self.check_injector(cls, jc, mem, an, ctx)
                        elif an.name == "Shadow":
                            self.check_shadow(cls, mem, an, ctx)
                        elif an.name in ("Accessor", "Invoker"):
                            self.check_accessor(cls, mem, an, ctx)
                    except Exception as e:  # a checker bug must not hide the other findings
                        if os.environ.get("BC_MIXIN_CHECK_DEBUG"):
                            raise
                        rep.warn(ctx, f"@{an.name} not analysed ({type(e).__name__}: {e})")

    # -- @Shadow, @Accessor, @Invoker ---------------------------------------------------------------------------------
    def check_shadow(self, cls, mem, an, ctx):
        rep, jar = self.rep, self.jar
        rep.checks += 1
        dotted = cls.replace("/", ".")
        if mem.kind == "field":
            hits, complete = jar.find_field(cls, mem.name)
            if not hits:
                (rep.fail if complete else rep.warn)(ctx, f"@Shadow {mem.name} not on {dotted} or its superclasses")
                return
            want = self.R.ty(mem.type)
            good = [(n, f) for n, f in hits if parse_desc(f.desc)[1] == want]
            if not good:
                n, f = hits[0]
                actual = parse_desc(f.desc)[1]
                if unresolved(want):
                    rep.warn(ctx, f"@Shadow field {mem.name}: cannot resolve its type {mem.type}")
                else:
                    rep.fail(ctx, f"@Shadow field {mem.name} is {ty_name(want)} but {n.rsplit('/', 1)[-1]}.{mem.name} is {ty_name(actual)}")
                return
            if good[0][1].static != ("static" in mem.mods):
                rep.fail(ctx, f"@Shadow field {mem.name} is {'static' if 'static' in mem.mods else 'an instance field'} "
                              f"but {dotted.rsplit('.', 1)[-1]}.{mem.name} is {'static' if good[0][1].static else 'an instance field'}")
            else:
                rep.note(ctx, f"ok @Shadow field {mem.name}: {ty_name(want)}")
            return
        hits, complete = jar.find_methods(cls, mem.name)
        if not hits:
            (rep.fail if complete else rep.warn)(ctx, f"@Shadow {mem.name} not on {dotted} or its superclasses")
            return
        self.match_method(hits, mem, "@Shadow", ctx)

    def match_method(self, hits, mem, label, ctx):
        """The mixin method's whole signature against the target overloads `hits` [(declaring class, JMethod)]."""
        rep = self.rep
        params = self.tys(mem.params)
        ret = self.R.ty(mem.type)
        problems = []
        for n, m in hits:
            tp, tr = parse_desc(m.desc)
            if len(tp) != len(params):
                problems.append((n, m, tp, tr, "parameter count"))
                continue
            if any(h is not None and (unresolved(h) or unresolved(a)) for h, a in zip(params, tp)) or unresolved(ret) or unresolved(tr):
                rep.warn(ctx, f"{label} {mem.name}: cannot resolve every type of the signature")
                return
            if all(h is None or h == a for h, a in zip(params, tp)) and ret == tr:
                if m.static != ("static" in mem.mods):
                    rep.fail(ctx, f"{label} {mem.name} is {'static' if 'static' in mem.mods else 'an instance method'} "
                                  f"but {n.rsplit('/', 1)[-1]}.{mem.name} is {'static' if m.static else 'an instance method'}")
                else:
                    rep.note(ctx, f"ok {label} {mem.name}{show_sig([p for p in params if p], ret)}")
                return
            problems.append((n, m, tp, tr, "types"))
        have = "; ".join(f"{n.rsplit('/', 1)[-1]}.{mem.name}{show_sig(tp, tr)}" for n, m, tp, tr, _ in problems)
        rep.fail(ctx, f"{label} {mem.name}{show_sig([p for p in params if p], ret)} matches no target method; target has {have}")

    def check_accessor(self, cls, mem, an, ctx):
        rep, jar = self.rep, self.jar
        rep.checks += 1
        a = anno_args(an.args)
        names = string_list(a.get("value"))
        if names is None:
            rep.warn(ctx, f"@{an.name} name is not a plain string")
            return
        name = names[0] if names else None
        dotted = cls.replace("/", ".")
        if an.name == "Invoker":
            name = name or re.sub(r"^(?:invoke|call)([A-Z])", lambda m: m.group(1).lower(), mem.name, count=1)
            hits, complete = jar.find_methods(cls, name)
            if name == "<init>":
                return
            if not hits:
                (rep.fail if complete else rep.warn)(ctx, f"@Invoker {name} not on {dotted} or its superclasses")
                return
            self.match_method(hits, mem._replace(name=name), "@Invoker", ctx)
            return
        if name is None:
            m = re.match(r"^(?:get|is|set)([A-Z]\w*)$", mem.name)
            if not m:
                rep.warn(ctx, f"@Accessor {mem.name}: cannot infer the field name")
                return
            name = m.group(1)[0].lower() + m.group(1)[1:]
        hits, complete = jar.find_field(cls, name)
        if not hits:
            (rep.fail if complete else rep.warn)(ctx, f"@Accessor {name} not on {dotted} or its superclasses")
            return
        if mem.params:  # setter: the field type is the parameter's
            t = self.tys(mem.params[:1])[0]
            what = "setter parameter"
        else:
            t, what = self.R.ty(mem.type), "getter return type"
        if t is None:
            return
        actual = parse_desc(hits[0][1].desc)[1]
        if unresolved(t):
            rep.warn(ctx, f"@Accessor {name}: cannot resolve its type")
        elif t != actual:
            rep.fail(ctx, f"@Accessor {name} {what} is {ty_name(t)} but {hits[0][0].rsplit('/', 1)[-1]}.{name} is {ty_name(actual)}")
        else:
            rep.note(ctx, f"ok @Accessor {name} {what}: {ty_name(t)}")

    # -- injectors ----------------------------------------------------------------------------------------------------
    def check_injector(self, cls, jc, mem, an, ctx):
        rep = self.rep
        args = anno_args(an.args)
        methods = string_list(args.get("method"))
        if methods is None or (not methods and an.name != "ModifyConstant"):
            rep.warn(ctx, f"@{an.name} has a method that is not a plain string; not analysed")
            return
        ats = at_list(args.get("at"))
        sliced = "slice" in args
        kind = an.name
        if kind == "Inject" and not ats:
            rep.warn(ctx, "@Inject without a readable at; not analysed")
        for meth in methods:
            rep.checks += 1
            mm = re.fullmatch(r"(?:L[\w/$]+;)?([\w$<>]+)(\(.*)?", meth)
            if not mm:
                rep.warn(ctx, f"method {meth!r} is a pattern; not analysed")
                continue
            name, desc = mm.group(1), mm.group(2)
            cands = [m for m in jc.methods if m.name == name]
            simple = cls.rsplit("/", 1)[-1]
            if not cands:
                rep.fail(ctx, f"{cls.replace('/', '.')} has no method {name} (@{kind})")
                continue
            if desc:
                cands = [m for m in cands if desc_matches(desc, m.desc)]
                if not cands:
                    rep.fail(ctx, f"{cls.replace('/', '.')} has no {name}{desc} (@{kind})")
                    continue
            sites = []
            for at in ats:
                sites.append(self.resolve_site(cls, simple, name, cands, at, sliced, kind, ctx))
            # every @At nested elsewhere (a @Slice's from/to): its target has to exist in the method as well
            for at in all_ats(an.args):
                if not any(at == a for a in ats) and at.get("target"):
                    self.resolve_site(cls, simple, name, cands, at, True, kind, ctx)
            self.check_handler(kind, mem, an.args, cands, sites, ctx)

    def resolve_site(self, cls, simple, name, cands, at, sliced, inj_kind, ctx):
        """Find what one @At selects in the candidate methods; reports a missing target and an out-of-range ordinal."""
        rep = self.rep
        akind = (string_list(at.get("value")) or [""])[0]
        if ":" in akind or not akind:
            return None
        target = (string_list(at.get("target")) or [None])[0]
        ordinal = int(at["ordinal"]) if re.fullmatch(r"\d+", at.get("ordinal", "")) else None
        opcode = None
        if "opcode" in at:
            opcode = FIELD_OPS.get(at["opcode"].rsplit(".", 1)[-1])
        if akind == "RETURN":
            if ordinal is not None and not sliced:
                rep.checks += 1
                count = max(sum(1 for i in m.insns if i.op in RETURN_OPS) for m in cands)
                if ordinal >= count:
                    rep.fail(ctx, f"@At(RETURN) ordinal {ordinal} but {simple}.{name} has {count} return(s)")
            return None
        if akind not in ("INVOKE", "INVOKE_ASSIGN", "FIELD", "NEW"):
            return None
        if target is None:
            return None
        mi = parse_member_info(target)
        if mi is None:
            rep.warn(ctx, f"@At target {target!r} not understood; not analysed")
            return None
        rep.checks += 1
        found = find_matches(mi, akind, cands, opcode)
        if not found:
            what = f"{(mi.owner or '').rsplit('/', 1)[-1]}.{mi.name or ''}{mi.desc or ''}".strip(".")
            rep.fail(ctx, f"no {what} inside {simple}.{name} (@{inj_kind} @At({akind}))")
            return None
        if ordinal is not None and not sliced:
            rep.checks += 1
            best = max(sum(1 for mm, _ in found if mm is m) for m in cands)
            if ordinal >= best:
                what = f"{(mi.owner or '').rsplit('/', 1)[-1]}.{mi.name or ''}{mi.desc or ''}".strip(".")
                rep.fail(ctx, f"@At({akind}) ordinal {ordinal} but {simple}.{name} has {best} {what}")
        return akind, mi, found, opcode

    def site_signature(self, site):
        """(parameter types, return type) of the call a Wrap/Redirect handler stands in for; None when unknown."""
        if not site:
            return None
        akind, mi, found = site[:3]
        ins0 = found[0][1]
        owner = ins0.owner
        if akind in ("INVOKE", "INVOKE_ASSIGN"):
            descs = {i.desc for _, i in found}
            if len(descs) != 1:
                return None
            params, ret = parse_desc(next(iter(descs)))
            ops = {i.op for _, i in found}
            if len(ops) != 1:
                return None
            if ops != {"invokestatic"}:
                params = [Ty(0, owner, True)] + params
            return params, ret
        if akind == "FIELD":
            ops = {i.op for _, i in found}
            if len(ops) != 1 or len({i.desc for _, i in found}) != 1:
                return None
            ft = parse_desc(ins0.desc)[1]
            own = Ty(0, owner, True)
            return {"getstatic": ([], ft), "putstatic": ([ft], VOID), "getfield": ([own], ft),
                    "putfield": ([own, ft], VOID)}[next(iter(ops))]
        if akind == "NEW" and mi.desc and mi.name == "<init>":
            params, _ = parse_desc(mi.desc)
            return params, Ty(0, mi.owner, True)
        return None

    def check_handler(self, kind, mem, an_args, cands, sites, ctx):
        rep, R = self.rep, self.R
        strict = kind not in LENIENT
        self.rep.checks += 1
        if kind == "Inject":
            self.check_inject(mem, cands, ctx)
            return
        lead = []  # the handler parameters that stand for the expected ones (before @Local/@Share extras)
        for p in mem.params:
            if any(a.name in ("Local", "Share") for a in p.annos):
                break
            lead.append(p)
        if kind == "WrapMethod":
            for m in cands[:1]:
                tp, tr = parse_desc(m.desc)
                k = next((i for i, p in enumerate(lead) if self.is_operation(p)), None)
                if k is None:
                    rep.fail(ctx, "@WrapMethod handler has no Operation parameter")
                    return
                self.compare_list("@WrapMethod handler", self.tys(lead[:k]), tp, strict, ctx)
                self.compare("@WrapMethod handler return type", R.ty(mem.type), tr, strict, ctx)
            return
        if kind == "ModifyReturnValue":
            tr = parse_desc(cands[0].desc)[1]
            if lead:
                self.compare("@ModifyReturnValue handler parameter 1", R.ty(lead[0].type), tr, strict, ctx)
            self.compare("@ModifyReturnValue handler return type", R.ty(mem.type), tr, strict, ctx)
            return
        if kind not in ("WrapOperation", "WrapWithCondition", "Redirect", "ModifyExpressionValue", "ModifyArg"):
            rep.note(ctx, f"@{kind}: handler signature not checked")
            return
        for site in sites:
            sig = self.site_signature(site)
            if sig is None:
                rep.note(ctx, f"@{kind}: the @At does not pin one call or field access down; handler not compared")
                continue
            exp_params, exp_ret = sig
            if kind == "ModifyArg":
                args = anno_args(an_args)
                index = int(args["index"]) if re.fullmatch(r"\d+", args.get("index", "")) else None
                invoked = exp_params[1:] if site[2][0][1].op != "invokestatic" else exp_params
                if site[0] not in ("INVOKE", "INVOKE_ASSIGN") or index is None or index >= len(invoked):
                    rep.note(ctx, "@ModifyArg: argument index not pinned down; handler not compared")
                    continue
                if lead:
                    self.compare("@ModifyArg handler parameter 1", R.ty(lead[0].type), invoked[index], strict, ctx)
                self.compare("@ModifyArg handler return type", R.ty(mem.type), invoked[index], strict, ctx)
            elif kind == "WrapOperation":
                k = next((i for i, p in enumerate(lead) if self.is_operation(p)), None)
                if k is None:
                    rep.fail(ctx, "@WrapOperation handler has no Operation parameter")
                    continue
                self.compare_list("@WrapOperation handler", self.tys(lead[:k]), exp_params, strict, ctx)
                self.compare("@WrapOperation handler return type", R.ty(mem.type), exp_ret, strict, ctx)
            elif kind == "WrapWithCondition":
                self.compare_list("@WrapWithCondition handler", self.tys(lead), exp_params, strict, ctx)
                self.compare("@WrapWithCondition handler return type", R.ty(mem.type), BOOLEAN, strict, ctx, has="a condition is")
            elif kind == "Redirect":
                handler = self.tys(lead)
                if len(handler) < len(exp_params):
                    rep.fail(ctx, f"@Redirect handler takes {len(handler)} parameter(s) but the redirected call needs {len(exp_params)}")
                    continue
                self.compare_list("@Redirect handler", handler[:len(exp_params)], exp_params, strict, ctx)
                self.compare("@Redirect handler return type", R.ty(mem.type), exp_ret, strict, ctx)
            elif kind == "ModifyExpressionValue":
                if exp_ret == VOID:
                    continue
                if lead:
                    self.compare("@ModifyExpressionValue handler parameter 1", R.ty(lead[0].type), exp_ret, strict, ctx)
                self.compare("@ModifyExpressionValue handler return type", R.ty(mem.type), exp_ret, strict, ctx)

    def is_operation(self, p):
        return self.R.ty(p.type).base == OPERATION or p.type.split("<")[0].strip() == "Operation"

    def is_ci(self, p):
        b = self.R.ty(p.type).base
        return b in CI_CLASSES or p.type.split("<")[0].strip() in ("CallbackInfo", "CallbackInfoReturnable")

    def check_inject(self, mem, cands, ctx):
        rep, R = self.rep, self.R
        if R.ty(mem.type) != VOID:
            rep.fail(ctx, f"@Inject handler returns {mem.type}, not void")
        k = next((i for i, p in enumerate(mem.params) if self.is_ci(p)), None)
        if k is None:
            rep.fail(ctx, "@Inject handler has no CallbackInfo parameter")
            return
        ci_name = mem.params[k].type.split("<")[0].strip().rsplit(".", 1)[-1]
        # MixinExtras captures (@Local, @Share) are not target arguments wherever they sit
        handler = self.tys([p for p in mem.params[:k] if not any(a.name in ("Local", "Share") for a in p.annos)])
        results = []  # per overload, the failures it gave ([] = it fits)
        for m in cands:
            tp, tr = parse_desc(m.desc)
            before = len(rep.problems)
            want_ci = "CallbackInfoReturnable" if tr != VOID else "CallbackInfo"
            if ci_name != want_ci:
                rep.fail(ctx, f"@Inject handler takes {ci_name} but {m.name}{show_sig(tp, tr)} needs {want_ci}")
            if handler:  # an empty list is a handler that takes no target arguments, which Mixin allows
                self.compare_list("@Inject handler", handler, tp, True, ctx, f" in {m.name}{show_sig(tp, tr)}")
            results.append(rep.problems[before:])
            del rep.problems[before:]
            if not results[-1]:
                rep.note(ctx, f"ok @Inject handler on {m.name}{show_sig(tp, tr)}: {ci_name}, "
                              f"{len(handler)} leading parameter(s)")
        if all(results):
            # no overload fits: report the first one's findings (the others only add noise unless it is the lone one)
            if len(cands) == 1 or all(r == results[0] for r in results):
                rep.problems += results[0]
            else:
                rep.problems.append(f"{ctx}: @Inject handler fits none of the {len(cands)} overloads: " + " | ".join("; ".join(r).replace(ctx + ": ", "") for r in results))
        elif any(results):
            rep.warn(ctx, f"@Inject handler fits {sum(1 for r in results if not r)} of the {len(cands)} overloads its method name selects; "
                          f"name the descriptor")


# ---------------------------------------------------------------------------------------------------------------------
# Files.

class Truncated(Exception):
    pass


MixinDecl = namedtuple("MixinDecl", "src args name members")


def load_file(path):
    """The mixin classes of a source file (none for a file without @Mixin)."""
    raw = path.read_text(encoding="utf-8")
    if "@Mixin" not in raw:
        return []
    text = strip_comments(raw)
    src = Source(path, text)
    out = []
    for m in re.finditer(r"@Mixin\b", text):
        j = m.end()
        while text[j:j + 1].isspace():
            j += 1
        args = ""
        if text[j:j + 1] == "(":
            k = match_close(text, j)
            if k < 0:
                raise Truncated(f"{path.name}: the @Mixin arguments never close")
            args, j = text[j + 1:k], k + 1
        decl = re.compile(r"\b(?:class|interface)\s+(\w+)[^{;]*\{").search(text, j)
        if not decl:
            continue
        end = match_close(text, decl.end() - 1)
        if end < 0:
            raise Truncated(f"{path.name}: class {decl.group(1)} never closes")
        out.append(MixinDecl(src, args, decl.group(1), class_members(text, decl.end(), end)))
    return out


def mixin_files(views):
    for v in views:
        for p in sorted(v.rglob("*.java")):
            rel = p.relative_to(v).as_posix()
            if "/fabric/" in rel and "mixin" in rel:
                yield p


def prefetch_targets(decls, jar, rep):
    """Read every target class and its supertypes up front, in parallel, instead of one javap at a time."""
    want = set()
    for d in decls:
        want.update(MixinChecker(jar, d.src, rep).targets(d.args))
    while want:
        jar.prefetch(want)
        want = {s for n in want if jar.get(n) for s in jar.get(n).supers} - jar.known()


# ---------------------------------------------------------------------------------------------------------------------
# Readiness: refuse to read a view Gradle is writing.

def norm_path(text):
    """Lower-case, backslashes, no doubled separators: the same path as gradlew.bat and gradlew print it."""
    return re.sub(r"\\+", r"\\", text.replace("/", "\\").lower())


def gradle_clients():
    """(pid, command line) of live Gradle wrapper clients of this checkout; [] when none or processes can't be listed.

    gradlew runs `java -jar <checkout>/gradle/wrapper/gradle-wrapper.jar <tasks>` for as long as its build runs, and
    that jar path names the checkout, so it tells this checkout's build from another worktree's.
    """
    jar = norm_path(str(ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"))
    try:
        if os.name == "nt":
            r = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command",
                                "Get-CimInstance Win32_Process -Filter \"Name like 'java%'\" | "
                                "ForEach-Object { \"$($_.ProcessId)`t$($_.CommandLine)\" }"],
                               capture_output=True, text=True, timeout=30)
        else:
            r = subprocess.run(["ps", "-eo", "pid=,args="], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return []
    out = []
    for line in r.stdout.splitlines():
        if jar in norm_path(line):
            out.append((line.split(None, 1)[0], line))
    return out


def view_state(views):
    """(seconds since the newest view file was written or None, [what is missing from the views])."""
    newest, problems = 0.0, []
    for view, src in views:
        have = {}
        if view.is_dir():
            for p in view.rglob("*.java"):
                try:
                    st = p.stat()
                except OSError:
                    continue
                have[p.relative_to(view).as_posix()] = st
                newest = max(newest, st.st_mtime)
        want = {p.relative_to(src).as_posix() for p in src.rglob("*.java")}
        gone = sorted(want - have.keys())
        if gone:
            problems.append(f"{view.relative_to(ROOT).as_posix()} lacks {len(gone)} of the {len(want)} files in "
                            f"{src.relative_to(ROOT).as_posix()} (first: {gone[0]})")
    return (time.time() - newest if newest else None), problems


def refuse(node, reasons):
    print(f"fabric mixin check ({node}): refusing to read the generated sources: not ready", file=sys.stderr)
    for r in reasons:
        print("  " + r, file=sys.stderr)
    print(f"  wait for Gradle to finish, then run ./gradlew :{node}:compileJava :{node}:compileGametestJava and this again (--no-ready-check skips this)",
          file=sys.stderr)
    sys.exit(2)


def wait_ready(view_pairs, wait):
    """Reasons the view cannot be read yet ([] when it can), after waiting up to `wait` seconds for them to clear."""
    deadline = time.time() + wait
    while True:
        reasons = []
        clients = gradle_clients()
        if clients:
            reasons.append(f"a Gradle build of this checkout is running (pid {clients[0][0]})")
        age, problems = view_state(view_pairs)
        reasons += problems
        if age is not None and age < QUIET_SECONDS:
            reasons.append(f"the generated sources were written {age:.1f} s ago")
        if not reasons or time.time() >= deadline:
            return reasons
        time.sleep(0.5)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("node")
    ap.add_argument("--jar")
    ap.add_argument("--wait", type=float, default=15.0, help="seconds to wait for the generated sources to settle (default 15)")
    ap.add_argument("--no-ready-check", action="store_true", help="read the generated sources without the readiness refusal")
    ap.add_argument("-v", "--verbose", action="store_true", help="also list everything that was checked and found fine")
    a = ap.parse_args()
    props_file = ROOT / "versions" / a.node / "gradle.properties"
    if not props_file.is_file():
        print(f"error: no node {a.node} ({props_file} does not exist)", file=sys.stderr)
        sys.exit(2)
    props = props_file.read_text(encoding="utf-8")
    mc = re.search(r"^minecraft_version=(\S+)", props, re.M).group(1)
    jar = Path(a.jar) if a.jar else find_jar(mc)
    if not jar or not jar.is_file():
        print(f"error: no Minecraft jar for {mc}; pass --jar", file=sys.stderr)
        sys.exit(2)
    base = ROOT / "versions" / a.node / "build" / "generated" / "stonecutter"
    views = [base / s / "java" for s in ("main", "test")]
    pairs = [(base / "main" / "java", ROOT / "src" / "main" / "java"), (base / "test" / "java", ROOT / "src" / "test" / "java")]
    if not a.no_ready_check:
        reasons = wait_ready(pairs, a.wait)
        if reasons:
            refuse(a.node, reasons)
    elif any(not v.is_dir() for v in views):
        print(f"error: {next(v for v in views if not v.is_dir())} does not exist; run ./gradlew :{a.node}:compileJava :{a.node}:compileGametestJava first", file=sys.stderr)
        sys.exit(2)
    javap = find_javap()
    if not javap:
        print("error: javap not found (it ships with the JDK); put a JDK's bin on PATH or set JAVA_HOME", file=sys.stderr)
        sys.exit(2)
    j = Jar(jar, javap)
    rep = Report()
    decls, mixins, cut, stale = [], 0, [], []
    for view, src in pairs:
        for p in mixin_files([view]):
            try:
                found = load_file(p)
                decls += found
                mixins += 1 if found else 0
            except Truncated as e:
                cut.append(str(e))
            raw = src / p.relative_to(view)
            if raw.is_file() and raw.stat().st_mtime > p.stat().st_mtime + 2:
                stale.append(p.name)
    if stale:
        rep.warn("generated sources", f"{len(stale)} mixin file(s) in src/ are newer than their generated copy "
                                      f"({', '.join(stale[:4])}{', ...' if len(stale) > 4 else ''}); if they were edited, "
                                      f"this checked the old text: run ./gradlew :{a.node}:compileJava :{a.node}:compileGametestJava first")
    if cut:
        if not a.no_ready_check:
            refuse(a.node, [f"a mixin file is cut off, so it is being written: {c}" for c in cut])
        for c in cut:
            rep.fail("source", c)
    prefetch_targets(decls, j, rep)
    for d in decls:
        MixinChecker(j, d.src, rep).check(d.args, d.members, d.name)
    print(f"fabric mixin check ({a.node}, {jar.name}): {mixins} mixins, {rep.checks} references, "
          f"{len(rep.problems)} problems, {len(rep.warnings)} warnings")
    for pr in rep.problems:
        print("  " + pr)
    for w in rep.warnings:
        print("  warn: " + w)
    if a.verbose:
        for n in rep.notes:
            print("  " + n)
    sys.exit(1 if rep.problems else 0)


if __name__ == "__main__":
    main()
