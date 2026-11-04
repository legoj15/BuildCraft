#!/usr/bin/env python3
"""Platform-layer census: how far common code still is from the loader-neutral finish line.

Standing design: docs/platform-layer.md (issue #98, "Progress metric"). Read-only on the tree, stdlib only.

Run it isolated, from anywhere:

    python -I scripts/platform-census.py              # full report for the active node's view (26.3 today)
    python -I scripts/platform-census.py --summary    # the one-line summary only
    python -I scripts/platform-census.py --json       # everything as one JSON object
    python -I scripts/platform-census.py --node 1.21.1
        # read versions/<node>/build/generated/stonecutter/main/java instead of src/ (that node's own view);
        # needs a prior `:<node>:stonecutterGenerate` (or any compile of that node), else exit code 2
    python -I scripts/platform-census.py --show-live buildcraft/lib/misc/NBTUtilBC.java
        # debugging aid: print the live (comment-stripped, string-blanked) view of one file, with line numbers

Other options: --top N (rows in the top-files lists, default 15), --neoforge-ref DIR (the .neoforge-ref directory
holding the decompiled sources; found automatically in the checkout or, from a worktree, in the main checkout),
--root DIR (census another checkout, e.g. a scratch tree).

Exit codes: 0 clean, 1 at least one implementation leak (section 5), 2 unusable input (unknown node, missing
generated sources, unreadable file). A missing .neoforge-ref only skips section 4, with a warning.

What it measures, all on the live view (the code the node actually compiles):

 1. Live `net.neoforged` import lines and fully qualified references outside src/main/java/buildcraft/platform/neoforge/**
    (the implementation package). This is the headline. Grouped by API group, by subsystem and by file.
 2. `net.neoforged` text inside commented-out directive arms (informational, never part of the finish line). Arms that
    only the 1.21.1 node compiles are listed apart: that node is NeoForge-only forever (docs/platform-layer.md,
    "Which arms count").
 3. Allowlisted references (the ALLOWLIST dict below, each with a reason). The allowlist is supposed to end empty.
 4. Patched-vanilla overrides: the methods declared by NeoForge's I*Extension interfaces (read from the pinned
    decompiled sources), matched by name and parameter types against `@Override` methods in BC classes, split into
    tagged (carry a @LoaderHook annotation) and untagged. A match counts only when the declaring BC type descends
    from the vanilla type that carries the extension (IBlockExtension -> Block, found through class headers in the
    same sources), so a BC method that merely shares a name with an extension method is not mistaken for a patch.
 5. Implementation leaks (any one makes the exit code 1): a common file naming a loader impl package (buildcraft.platform.neoforge or .fabric) other than
    the binders, a loader impl package naming the OTHER loader's package, or a buildcraft/platform contract file naming net.neoforged, net.fabricmc or a BC gameplay package (any BC
    package but buildcraft.platform, buildcraft.api.fluids and buildcraft.api.capability).
 6. Stonecutter directive lines remaining in common code.
 7. Test-tree figures (src/test/java), informational.

How "live" is decided: a Java-aware lexer (strings, text blocks, char literals, // and block comments) blanks every
comment and string body while keeping line numbers. Stonecutter writes an inactive arm as a block comment closed by the
`*///?}` forms and the directive lines are line comments, so the same lexer reads the active node's view and the
generated views alike. Which node a commented arm belongs to is worked out by evaluating each chain's condition
(`if`/`elif`/`else`, `&&`, `||`, loader constants) against the nodes found under versions/.

No test pins any number printed here: the census is a progress gauge, not a gate. The one exception is the exit code,
which is nonzero when an implementation leak exists. Keep it in step with directive syntax (`elif`, `*///?}`) when
Stonecutter's grammar grows. After each slice the orchestrator posts the summary line with `issues.sh note 98`.
"""

from __future__ import annotations

import argparse
import bisect
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# ----------------------------------------------------------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------------------------------------------------------

#: The NeoForge implementation package: references here are the point of the package, so they are not counted.
IMPL_PREFIX = "buildcraft/platform/neoforge/"
#: Loader implementation packages. Contract rules apply to every other file under buildcraft/platform/.
LOADER_PREFIXES = (IMPL_PREFIX, "buildcraft/platform/fabric/")
#: The only common files allowed to name a loader implementation package (the compile-time binders).
BINDERS = ("buildcraft/platform/Platform.java", "buildcraft/platform/client/ClientPlatform.java")
#: The oldest node. Its commented arms do not count toward the finish line (NeoForge-only forever).
LEGACY_NODE = "1.21.1"
FALLBACK_NODES = ("1.21.1", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3", "26.3-fabric")

#: References that are tolerated for now. Matched in order: `paths` are source-root-relative prefixes, `types` are
#: regexes over the fully qualified name. A reference matching an entry counts as allowlisted, not remaining.
#: The list should shrink to nothing; delete an entry the moment its reason expires.
ALLOWLIST = {
    "config-spec": {
        "reason": "ModConfigSpec/ModConfig stay NeoForge-typed in the config classes, pending the user's decision on "
                  "config for Fabric (docs/platform-layer.md, open question 1)",
        "types": [
            r"net\.neoforged\.neoforge\.common\.ModConfigSpec(?:\.|$)",
            r"net\.neoforged\.fml\.config\.ModConfig(?:\.|$)",
        ],
        "paths": [],
    },
}

# ----------------------------------------------------------------------------------------------------------------------
# Java lexer
# ----------------------------------------------------------------------------------------------------------------------

_TOKEN = re.compile(r"//[^\n]*|/\*|\"\"\"|\"|'")
_STRING = re.compile(r'"(?:\\.|[^"\\\n])*"')
_CHAR = re.compile(r"'(?:\\.|[^'\\\n])+'")
_NON_NEWLINE = re.compile(r"[^\n]")
# A directive line inside a block comment (a chain nested in an inactive arm). A nested arm that closes on the same
# line is written `^///?}` (one level deeper, in generated views, a superscript digit precedes the `^/`): the nested
# comment closer, then `//?`.
_NESTED_DIRECTIVE = re.compile(r"\s*(?:\S*?\^/)?//\?(.*)$")


def _blank(text):
    """Replace every character but the newlines with a space, so offsets and line numbers survive."""
    return _NON_NEWLINE.sub(" ", text)


class Lexed:
    """masked: the source with comments and string bodies blanked (same length as the input).
    comments: [(offset, text)]. strings: [(line, body)]. directives: [(line, text after '//?')]."""

    __slots__ = ("masked", "comments", "strings", "directives", "newlines")

    def line_of(self, offset):
        return bisect.bisect_left(self.newlines, offset) + 1


def lex(text):
    newlines = [m.start() for m in re.finditer("\n", text)]
    out, comments, strings, code_directives = [], [], [], []
    block_lines = set()
    lx = Lexed()
    lx.newlines = newlines
    n = len(text)
    pos = last = 0
    while True:
        m = _TOKEN.search(text, pos)
        if m is None:
            break
        start, tok = m.start(), m.group()
        if tok.startswith("//"):
            end = m.end()
            out.append(text[last:start])
            out.append(_blank(text[start:end]))
            comments.append((start, text[start:end]))
            if text.startswith("//?", start):
                code_directives.append((lx.line_of(start), text[start + 3:end].strip()))
        elif tok == "/*":
            end = text.find("*/", start + 2)
            end = n if end < 0 else end + 2
            out.append(text[last:start])
            out.append(_blank(text[start:end]))
            comments.append((start, text[start:end]))
            # Lines after the opening one start inside the comment, up to and including the line where it closes.
            block_lines.update(range(lx.line_of(start) + 1, lx.line_of(end - 1) + 1))
        elif tok == '"""':
            k = start + 3
            while True:
                k = text.find('"""', k)
                if k < 0:
                    k = n - 3
                    break
                backslashes = 0
                while text[k - 1 - backslashes] == "\\":
                    backslashes += 1
                if backslashes % 2 == 0:
                    break
                k += 1
            end = min(k + 3, n)
            body = text[start + 3:max(k, start + 3)]
            out.append(text[last:start])
            out.append('"""' + _blank(body) + '"""')
            strings.append((lx.line_of(start), body))
        elif tok == '"':
            sm = _STRING.match(text, start)
            if sm is None:
                pos = start + 1
                continue
            end = sm.end()
            out.append(text[last:start])
            out.append('"' + _blank(text[start + 1:end - 1]) + '"')
            strings.append((lx.line_of(start), text[start + 1:end - 1]))
        else:
            cm = _CHAR.match(text, start)
            if cm is None:
                pos = start + 1
                continue
            end = cm.end()
            out.append(text[last:start])
            out.append("'" + _blank(text[start + 1:end - 1]) + "'")
        last = pos = end
    out.append(text[last:])
    lx.masked = "".join(out)
    lx.comments = comments
    lx.strings = strings
    directives = dict(code_directives)
    if block_lines:
        raw_lines = text.split("\n")
        for line in sorted(block_lines):
            dm = _NESTED_DIRECTIVE.match(raw_lines[line - 1])
            if dm is not None:
                directives.setdefault(line, dm.group(1).strip())
    lx.directives = sorted(directives.items())
    return lx


# ----------------------------------------------------------------------------------------------------------------------
# Stonecutter directives: which nodes compile a given line
# ----------------------------------------------------------------------------------------------------------------------

_D_IF = re.compile(r"^if\s+(.+?)\s*\{$")
_D_ELIF = re.compile(r"^\}\s*(?:elif|else\s+if)\s+(.+?)\s*\{$")
_D_ELSE = re.compile(r"^\}\s*else\s*\{$")
_D_END = re.compile(r"^\}$")
_TERM_VERSION = re.compile(r"^(>=|<=|!=|>|<|=)?\s*(\d+(?:\.\d+)*)$")
_TERM_CONSTANT = re.compile(r"^(neoforge|fabric)$")


class CensusError(Exception):
    """A usage or input problem (not a finding): reported on stderr, exit code 2."""


def _loader_of(node_name):
    for props in (REPO / "versions" / node_name / "gradle.properties", REPO / "gradle.properties"):
        try:
            text = props.read_text(encoding="utf-8")
        except OSError:
            continue
        m = re.search(r"^loader\s*=\s*(\S+)", text, re.M)
        if m:
            return m.group(1)
    return "neoforge"


class Node:
    def __init__(self, name):
        self.name = name
        base, _, _ = name.partition("-")
        self.version = _vkey(base)
        # The loader comes from gradle.properties (node file, then root), never the node name.
        self.loader = _loader_of(name)


def _vkey(text):
    parts = tuple(int(p) for p in text.split("."))
    return parts + (0,) * (3 - len(parts))


def _term_true(term, node):
    term = term.strip()
    m = _TERM_CONSTANT.match(term)
    if m:
        return node.loader == m.group(1)
    m = _TERM_VERSION.match(term)
    if not m:
        raise ValueError(term)
    op, ver = m.group(1) or "=", _vkey(m.group(2))
    have = node.version
    return {
        ">=": have >= ver, "<=": have <= ver, ">": have > ver, "<": have < ver, "=": have == ver, "!=": have != ver,
    }[op]


def condition_nodes(cond, nodes):
    """The names of the nodes for which a directive condition holds, or None when the grammar is not understood."""
    try:
        groups = [[t for t in part.split("&&")] for part in cond.split("||")]
        return frozenset(
            node.name for node in nodes if any(all(_term_true(t, node) for t in ands) for ands in groups))
    except ValueError:
        return None


def parse_directive(text):
    """(kind, condition) with kind in if/elif/else/end, or ("other", text) for a shape this census does not model."""
    for kind, rx in (("if", _D_IF), ("elif", _D_ELIF)):
        m = rx.match(text)
        if m:
            return kind, m.group(1)
    if _D_ELSE.match(text):
        return "else", None
    if _D_END.match(text):
        return "end", None
    return "other", text


class ChainStates:
    """Walks one file's directives in order and answers: which nodes compile line L (None = unknown)."""

    def __init__(self, directives, nodes, warnings, where):
        everyone = frozenset(n.name for n in nodes)
        self.everyone = everyone
        self.lines, self.sets = [], []
        self.chains = 0
        self.loader_lines = 0
        stack = []  # frames: [remaining nodes not yet matched, nodes of the current arm]; None = unknown

        def outer():
            return stack[-1][1] if stack else everyone

        for line, text in directives:
            kind, cond = parse_directive(text)
            if cond is not None and re.search(r"\b(?:neoforge|fabric)\b", cond):
                self.loader_lines += 1
            if kind == "if":
                self.chains += 1
                have, cs = outer(), condition_nodes(cond, nodes)
                if cs is None:
                    warnings.append(f"{where}:{line}: unparsed condition {cond!r}")
                stack.append([None, None] if have is None or cs is None else [have - cs, have & cs])
            elif kind == "elif" and stack:
                frame, cs = stack[-1], condition_nodes(cond, nodes)
                if cs is None:
                    warnings.append(f"{where}:{line}: unparsed condition {cond!r}")
                if frame[0] is None or cs is None:
                    frame[0] = frame[1] = None
                else:
                    frame[1], frame[0] = frame[0] & cs, frame[0] - cs
            elif kind == "else" and stack:
                frame = stack[-1]
                frame[1], frame[0] = frame[0], (None if frame[0] is None else frozenset())
            elif kind == "end" and stack:
                stack.pop()
            else:
                warnings.append(f"{where}:{line}: directive not modelled: //?{text}")
            self.lines.append(line)
            self.sets.append(outer())
        if stack:
            warnings.append(f"{where}: {len(stack)} directive chain(s) left open at end of file")

    def nodes_at(self, line):
        i = bisect.bisect_right(self.lines, line) - 1
        return self.everyone if i < 0 else self.sets[i]


# ----------------------------------------------------------------------------------------------------------------------
# Override scanning (patched-vanilla methods)
# ----------------------------------------------------------------------------------------------------------------------

_MODIFIERS = {"public", "protected", "private", "static", "final", "default", "abstract", "synchronized", "native",
              "strictfp"}
_ANNOTATION = re.compile(r"@\s*([\w.]+)")
_IDENT = re.compile(r"\w+")


def _match_paren(s, i):
    """Index of the ')' closing the '(' at i, or -1."""
    depth = 0
    for j in range(i, len(s)):
        c = s[j]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return j
    return -1


def _skip_ws(s, i):
    while i < len(s) and s[i].isspace():
        i += 1
    return i


def _annotations_before(s, i):
    """Simple names of the annotations immediately preceding index i."""
    names = []
    while True:
        k = i - 1
        while k >= 0 and s[k].isspace():
            k -= 1
        if k < 0:
            return names
        if s[k] == ")":
            depth = 0
            while k >= 0:
                if s[k] == ")":
                    depth += 1
                elif s[k] == "(":
                    depth -= 1
                    if depth == 0:
                        break
                k -= 1
            if k < 0:
                return names
            k -= 1
            while k >= 0 and s[k].isspace():
                k -= 1
        end = k + 1
        while k >= 0 and (s[k].isalnum() or s[k] in "_.$"):
            k -= 1
        start = k + 1
        while k >= 0 and s[k].isspace():
            k -= 1
        if end > start and k >= 0 and s[k] == "@":
            names.append(s[start:end].split(".")[-1])
            i = k
        else:
            return names


def _split_params(text):
    parts, depth, cur = [], 0, []
    for c in text:
        if c in "<([{":
            depth += 1
        elif c in ">)]}":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return parts


def _param_type(param):
    param = re.sub(r"@[\w.]+(?:\s*\([^()]*\))?", " ", param)
    while True:
        stripped = re.sub(r"<[^<>]*>", "", param)
        if stripped == param:
            break
        param = stripped
    tokens = [t for t in param.replace("...", "[] ").split() if t != "final"]
    if not tokens:
        return "?"
    kind = tokens[0] if len(tokens) == 1 else "".join(tokens[:-1])
    return kind.split(".")[-1]


def parse_signature(header_after_annotations, paren):
    """(method name, [simple parameter types]) for the declaration whose '(' is at `paren` in the given string."""
    head = header_after_annotations[:paren]
    ident = list(_IDENT.finditer(head))
    if not ident:
        return None
    name = ident[-1].group()
    close = _match_paren(header_after_annotations, paren)
    if close < 0:
        return None
    params = [_param_type(p) for p in _split_params(header_after_annotations[paren + 1:close])]
    return name, params


def find_overrides(lx):
    """Every `@Override` method in the masked source: [(offset, line, name, [param types], {annotation names})]."""
    s = lx.masked
    found = []
    for m in re.finditer(r"@Override\b", s):
        tags = set(_annotations_before(s, m.start()))
        i = m.end()
        while True:
            i = _skip_ws(s, i)
            am = _ANNOTATION.match(s, i)
            if am and s[i] == "@":
                tags.add(am.group(1).split(".")[-1])
                i = _skip_ws(s, am.end())
                if i < len(s) and s[i] == "(":
                    close = _match_paren(s, i)
                    i = close + 1 if close > 0 else len(s)
                continue
            wm = _IDENT.match(s, i)
            if wm and wm.group() in _MODIFIERS:
                i = wm.end()
                continue
            break
        paren = s.find("(", i)
        if paren < 0 or re.search(r"[;{}]", s[i:paren]):
            continue
        sig = parse_signature(s[i:], paren - i)
        if sig is None:
            continue
        found.append((m.start(), lx.line_of(m.start()), sig[0], sig[1], tags))
    return found


_DECL_ATOM = re.compile(
    r"\s*(?:@[\w.]+\s*(?:\([^()]*\))?|(?:public|protected|private|static|final|abstract|synchronized|default|native"
    r"|strictfp)\b)")
_NOT_A_TYPE = {"return", "new", "throw", "else", "case", "assert", "yield", "super", "this"}


def find_unannotated(lx, names_re):
    """Method declarations whose name is in `names_re` but which carry no `@Override`:
    [(offset, line, name, [param types], {annotation names})]. A patched override written without the annotation does
    not fail loudly on a loader that lacks the hook, so the census reports it."""
    s = lx.masked
    found = []
    for m in names_re.finditer(s):
        boundary = max(s.rfind(c, 0, m.start()) for c in ";{}")
        header = s[boundary + 1:m.start()]
        tags, pos = set(), 0
        while True:
            am = _DECL_ATOM.match(header, pos)
            if am is None:
                break
            text = am.group().strip()
            if text.startswith("@"):
                tags.add(re.match(r"@([\w.]+)", text).group(1).split(".")[-1])
            pos = am.end()
        rest = _strip_nested(header[pos:], "<", ">").strip()
        if "Override" in tags or rest in _NOT_A_TYPE or not re.fullmatch(r"[\w.]+(?:\[\])*", rest):
            continue
        sig = parse_signature(s[m.start():], m.group().rindex("("))
        if sig is None:
            continue
        close = _match_paren(s, m.end() - 1)
        tail = s[close + 1:close + 80].lstrip() if close > 0 else ""
        if not (tail.startswith("{") or tail.startswith("throws")):
            continue
        found.append((m.start(), lx.line_of(m.start()), sig[0], sig[1], tags))
    return found


def _strip_nested(s, open_c, close_c):
    pattern = re.compile(re.escape(open_c) + "[^" + re.escape(open_c + close_c) + "]*" + re.escape(close_c))
    while True:
        t = pattern.sub("", s)
        if t == s:
            return s
        s = t


def parse_parents(tail):
    """Simple names after `extends`/`implements` in the rest of a type header (the text after the type's name)."""
    tail = _strip_nested(_strip_nested(tail, "<", ">"), "(", ")")
    tail = re.sub(r"@[\w.]+", " ", tail)
    raw = []
    m = re.search(r"\bextends\b(.*?)(?=\bimplements\b|\bpermits\b|$)", tail, re.S)
    if m:
        raw += m.group(1).split(",")
    m = re.search(r"\bimplements\b(.*?)(?=\bpermits\b|$)", tail, re.S)
    if m:
        raw += m.group(1).split(",")
    return [p.strip().split(".")[-1].strip() for p in raw if p.strip()]


_TYPE_DECL = re.compile(r"\b(?:class|interface|enum|record)\s+(\w+)([^{;]*)\{")
_NEW_BEFORE_PAREN = re.compile(r"\bnew\s+([\w.]+)\s*(?:<.*>)?\s*$", re.S)


def type_declarations(masked):
    """[(type name, [direct parent names])] for every class/interface/enum/record declared in the source."""
    return [(m.group(1), parse_parents(m.group(2))) for m in _TYPE_DECL.finditer(masked)]


def _frame_parents(masked, boundary, brace):
    """What a '{' opens: the direct parents of a type body (named or anonymous), or None for any other block."""
    header = masked[boundary + 1:brace]
    m = re.search(r"\b(?:class|interface|enum|record)\s+\w+(.*)$", header, re.S)
    if m:
        return parse_parents(m.group(1))
    head = header.rstrip()
    if head.endswith(")"):
        depth, k = 0, len(head) - 1
        while k >= 0:
            if head[k] == ")":
                depth += 1
            elif head[k] == "(":
                depth -= 1
                if depth == 0:
                    break
            k -= 1
        nm = _NEW_BEFORE_PAREN.search(head[:k]) if k > 0 else None
        if nm:
            return [nm.group(1).split(".")[-1]]
    return None


def enclosing_parents(masked, offsets):
    """{offset: direct parents of the innermost enclosing type body} for each offset (None if not inside one)."""
    result, stack, idx, boundary = {}, [], 0, -1
    offsets = sorted(offsets)
    for m in re.finditer(r"[{};]", masked):
        p, c = m.start(), m.group()
        while idx < len(offsets) and offsets[idx] < p:
            result[offsets[idx]] = next((f for f in reversed(stack) if f is not None), None)
            idx += 1
        if c == "{":
            stack.append(_frame_parents(masked, boundary, p))
        elif c == "}" and stack:
            stack.pop()
        boundary = p
    for off in offsets[idx:]:
        result[off] = None
    return result


def _strip_annotations(s):
    out, i, n = [], 0, len(s)
    while i < n:
        if s[i] == "@" and not s.startswith("@interface", i):
            am = _ANNOTATION.match(s, i)
            if am:
                j = _skip_ws(s, am.end())
                if j < n and s[j] == "(":
                    close = _match_paren(s, j)
                    j = close + 1 if close > 0 else n
                else:
                    j = am.end()
                out.append(" " * (j - i))
                i = j
                continue
        out.append(s[i])
        i += 1
    return "".join(out)


def harvest_extension_methods(path):
    """[(interface, method, [param types])] declared (instance methods only) by one I*Extension interface file."""
    s = _strip_annotations(lex(path.read_text(encoding="utf-8", errors="replace")).masked)
    iface = path.stem
    m = re.search(r"\binterface\s+" + re.escape(iface) + r"\b[^{]*\{", s)
    if not m:
        return []
    members, depth, start = [], 1, m.end()
    for i in range(m.end(), len(s)):
        c = s[i]
        if c == "{":
            if depth == 1:
                members.append(s[start:i])
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                break
            if depth == 1:
                start = i + 1
        elif c == ";" and depth == 1:
            members.append(s[start:i])
            start = i + 1
    methods = []
    for member in members:
        member = member.strip()
        paren = member.find("(")
        if paren < 0 or "=" in member[:paren]:
            continue
        words = set(re.findall(r"\w+", member[:paren]))
        if {"static", "private"} & words:
            continue
        sig = parse_signature(member, paren)
        if sig:
            methods.append((iface, sig[0], sig[1]))
    return methods


# ----------------------------------------------------------------------------------------------------------------------
# Per-file analysis
# ----------------------------------------------------------------------------------------------------------------------

_NEOFORGED = re.compile(r"(?<![\w.$])net\.neoforged(?:\.(?:\w+|\*))*")
_IMPORT_PREFIX = re.compile(r"\s*import\s+(?:static\s+)?$")
_IMPL_NAME = re.compile(r"(?<![\w.$])buildcraft\.platform\.(?:neoforge|fabric)\b")
_CONTRACT_BANS = (
    (re.compile(r"(?<![\w.$])net\.neoforged\b"), "net.neoforged"),
    (re.compile(r"(?<![\w.$])net\.fabricmc\b"), "net.fabricmc"),
    # buildcraft.api.fluids and buildcraft.api.capability are the only other BC packages a contract may name:
    # BCFluidStack and the BCCapability token are vanilla-only and the Fabric node compiles them (bc.fabric.gradle.kts),
    # so the fluid storage contracts (S14d) and the capability registrar and lookups (S14e) carry them.
    (re.compile(r"(?<![\w.$])buildcraft\.(?!platform\b|api\.fluids\b|api\.capability\b)[A-Za-z_]\w*"),
     "BC gameplay package"),
)


def api_group(fqn):
    """`net.neoforged.neoforge.fluids.FluidStack` -> `neoforge.fluids`; `net.neoforged.fml.ModList` -> `fml`."""
    pkgs = []
    for seg in fqn.split(".")[2:]:
        if not seg or not seg[0].islower():
            break
        pkgs.append(seg)
    if not pkgs:
        return "(root)"
    depth = 3 if pkgs[0] == "neoforge" and len(pkgs) > 1 and pkgs[1] in ("client", "event") else 2
    return ".".join(pkgs[:depth])


def subsystem(rel):
    parts = rel.split("/")
    return parts[1] if len(parts) > 2 else "(root)"


def allowlist_key(rel, fqn):
    for key, entry in ALLOWLIST.items():
        if any(rel.startswith(p) for p in entry["paths"]) or any(re.match(t, fqn) for t in entry["types"]):
            return key
    return None


class ExtensionIndex(dict):
    """(method name, arity) -> [(extension interface, parameter types)], plus a regex finding any of the names."""

    def __init__(self, methods):
        super().__init__()
        for iface, name, params in methods:
            self.setdefault((name, len(params)), []).append((iface, params))
        names = sorted({name for name, _arity in self})
        self.names_re = re.compile(r"\b(?:" + "|".join(re.escape(n) for n in names) + r")\s*\(")


class FileResult:
    __slots__ = ("rel", "imports", "fqns", "string_refs", "comment_refs", "directives", "chains", "loader_lines",
                 "overrides", "decls", "impl_named", "contract_leaks")

    def __init__(self, rel):
        self.rel = rel
        self.imports, self.fqns, self.string_refs = [], [], []
        self.comment_refs = []  # (line, fqn, node set or None)
        self.directives = []
        self.chains = self.loader_lines = 0
        self.overrides = []  # signature matches against the extension interfaces (see analyze)
        self.decls = []  # (type name, [direct parents]) for every type declared in the file
        self.impl_named = []
        self.contract_leaks = []


def analyze(text, rel, nodes, warnings, ext_index):
    lx = lex(text)
    masked = lx.masked
    res = FileResult(rel)
    res.directives = lx.directives
    states = ChainStates(lx.directives, nodes, warnings, rel)
    res.chains, res.loader_lines = states.chains, states.loader_lines

    for m in _NEOFORGED.finditer(masked):
        fqn = re.sub(r"\.class$", "", m.group())
        line = lx.line_of(m.start())
        # An import is a statement of its own; two can share a line (`a.B;import net.neoforged.X;`).
        stmt_start = max(masked.rfind(c, 0, m.start()) for c in "\n;{}") + 1
        target = res.imports if _IMPORT_PREFIX.match(masked[stmt_start:m.start()]) else res.fqns
        target.append((line, fqn))
    for line, body in lx.strings:
        for m in _NEOFORGED.finditer(body):
            res.string_refs.append((line, m.group()))
    for offset, ctext in lx.comments:
        for m in _NEOFORGED.finditer(ctext):
            line = lx.line_of(offset + m.start())
            res.comment_refs.append((line, m.group(), states.nodes_at(line)))

    # A loader impl package may name itself, never a sibling loader's package; common files name neither.
    own_impl = next((p.rstrip("/").replace("/", ".") for p in LOADER_PREFIXES if rel.startswith(p)), None)
    if rel not in BINDERS:
        res.impl_named = [lx.line_of(m.start()) for m in _IMPL_NAME.finditer(masked) if m.group() != own_impl]
    if own_impl is None:
        if rel.startswith("buildcraft/platform/"):
            for rx, what in _CONTRACT_BANS:
                for m in rx.finditer(masked):
                    res.contract_leaks.append((lx.line_of(m.start()), what, m.group()))
    if ext_index is not None:
        res.decls = type_declarations(masked)
        if not rel.startswith(IMPL_PREFIX):
            hits = []
            for annotated, found in ((True, find_overrides(lx)), (False, find_unannotated(lx, ext_index.names_re))):
                for offset, line, name, params, tags in found:
                    ifaces = [iface for iface, eparams in ext_index.get((name, len(params)), ())
                              if all(e == p or re.fullmatch(r"[A-Z]", e) for e, p in zip(eparams, params))]
                    if ifaces:
                        hits.append({"offset": offset, "line": line, "name": name, "ifaces": ifaces,
                                     "tagged": "LoaderHook" in tags, "annotated": annotated})
            if hits:
                parents = enclosing_parents(masked, [h["offset"] for h in hits])
                for h in hits:
                    h["parents"] = parents.get(h["offset"])
                res.overrides = hits
    return res


def read_tree(root, nodes, warnings, ext_index):
    results, disabled = [], 0
    if root is None or not root.is_dir():
        return results, disabled
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        if path.name.endswith(".disabled"):
            disabled += 1
            continue
        if path.suffix != ".java":
            continue
        rel = path.relative_to(root).as_posix()
        try:
            text = path.read_bytes().decode("utf-8", errors="replace")
        except OSError as e:
            raise CensusError(f"cannot read {path}: {e}")
        results.append(analyze(text, rel, nodes, warnings, ext_index))
    return results, disabled


# ----------------------------------------------------------------------------------------------------------------------
# Aggregation
# ----------------------------------------------------------------------------------------------------------------------

def _bucket():
    return {"imports": 0, "fqn": 0, "allowlisted": 0, "files": set()}


def headline(results, top):
    """Section 1 (and 3) figures for one tree."""
    out = {"files": 0, "imports": 0, "fqn": 0, "string_refs": 0, "allowlisted": 0, "remaining": 0,
           "remaining_files": 0, "impl": {"files": 0, "imports": 0, "fqn": 0}}
    groups, systems = defaultdict(_bucket), defaultdict(_bucket)
    per_file, allow_use = [], defaultdict(lambda: {"refs": 0, "files": set()})
    remaining_files = set()
    for r in results:
        if r.rel.startswith(IMPL_PREFIX):
            if r.imports or r.fqns:
                out["impl"]["files"] += 1
            out["impl"]["imports"] += len(r.imports)
            out["impl"]["fqn"] += len(r.fqns)
            continue
        out["string_refs"] += len(r.string_refs)
        if not (r.imports or r.fqns):
            continue
        out["files"] += 1
        out["imports"] += len(r.imports)
        out["fqn"] += len(r.fqns)
        file_allowed = 0
        for kind, refs in (("imports", r.imports), ("fqn", r.fqns)):
            for _line, fqn in refs:
                key = allowlist_key(r.rel, fqn)
                for bucket in (groups[api_group(fqn)], systems[subsystem(r.rel)]):
                    bucket[kind] += 1
                    bucket["files"].add(r.rel)
                    if key:
                        bucket["allowlisted"] += 1
                if key:
                    file_allowed += 1
                    allow_use[key]["refs"] += 1
                    allow_use[key]["files"].add(r.rel)
        total = len(r.imports) + len(r.fqns)
        out["allowlisted"] += file_allowed
        if total > file_allowed:
            remaining_files.add(r.rel)
        per_file.append((total - file_allowed, total, len(r.imports), len(r.fqns), file_allowed, r.rel))
    out["remaining"] = out["imports"] + out["fqn"] - out["allowlisted"]
    out["remaining_files"] = len(remaining_files)
    per_file.sort(key=lambda t: (-t[0], -t[1], t[5]))
    out["top_files"] = [
        {"file": t[5], "remaining": t[0], "refs": t[1], "imports": t[2], "fqn": t[3], "allowlisted": t[4]}
        for t in per_file[:top]]

    def render(buckets):
        rows = [{"name": k, "imports": b["imports"], "fqn": b["fqn"], "allowlisted": b["allowlisted"],
                 "files": len(b["files"])} for k, b in buckets.items()]
        rows.sort(key=lambda r: (-(r["imports"] + r["fqn"]), r["name"]))
        return rows

    out["by_group"] = render(groups)
    out["by_subsystem"] = render(systems)
    out["allowlist"] = [
        {"name": key, "reason": entry["reason"], "refs": allow_use[key]["refs"] if key in allow_use else 0,
         "files": len(allow_use[key]["files"]) if key in allow_use else 0}
        for key, entry in ALLOWLIST.items()]
    return out


def commented_arms(results, view):
    """Section 2: net.neoforged text in commented directive arms, split by the nodes that would compile it."""
    legacy_lines, other_lines, prose_lines, unknown_lines = set(), set(), set(), set()
    by_set = Counter()
    for r in results:
        for line, _fqn, nodes in r.comment_refs:
            key = (r.rel, line)
            if nodes is None:
                unknown_lines.add(key)
            elif view in nodes:
                prose_lines.add(key)
            elif nodes == frozenset({LEGACY_NODE}):
                legacy_lines.add(key)
            else:
                other_lines.add(key)
    for r in results:
        seen = set()
        for line, _fqn, nodes in r.comment_refs:
            if nodes is not None and view not in nodes and nodes != frozenset({LEGACY_NODE}) and (r.rel, line) not in seen:
                seen.add((r.rel, line))
                by_set[",".join(sorted(nodes)) or "(no node)"] += 1

    def summary(lines):
        return {"lines": len(lines), "files": len({f for f, _ in lines})}

    return {"legacy_only": summary(legacy_lines), "other_nodes": summary(other_lines),
            "unclassified": summary(unknown_lines), "prose_in_live_arms": summary(prose_lines),
            "other_nodes_by_node_set": dict(by_set.most_common())}


def directive_figures(results, top):
    """Section 6: Stonecutter directive lines in common code (everything outside the loader packages)."""
    lines = chains = loader = files = 0
    by_system, per_file = Counter(), []
    for r in results:
        if r.rel.startswith(LOADER_PREFIXES) or not r.directives:
            continue
        files += 1
        lines += len(r.directives)
        chains += r.chains
        loader += r.loader_lines
        by_system[subsystem(r.rel)] += len(r.directives)
        per_file.append((len(r.directives), r.rel))
    per_file.sort(key=lambda t: (-t[0], t[1]))
    return {"lines": lines, "files": files, "chains": chains, "loader_constant_lines": loader,
            "by_subsystem": dict(by_system.most_common()),
            "top_files": [{"file": f, "lines": n} for n, f in per_file[:top]]}


class TypeIndex:
    """Direct parents of a type by simple name: BC's own declarations plus the headers of the decompiled vanilla and
    NeoForge sources (top-level types only, read lazily). Used to tell a patched-vanilla override from a BC method that
    merely shares a name with an extension method."""

    _TOP_DECL = r"^(?:[\w-]+[ \t]+)*?(?:class|interface|enum|record)[ \t]+%s\b([^{;]*)\{"

    def __init__(self, results, source_roots):
        self.bc = defaultdict(set)
        for r in results:
            for name, parents in r.decls:
                self.bc[name].update(parents)
        self.files = defaultdict(list)
        for root in source_roots:
            for path in root.rglob("*.java"):
                self.files[path.stem].append(path)
        self.memo = {}

    def known(self, name):
        return name in self.bc or name in self.files

    def direct(self, name):
        if name not in self.memo:
            parents = set(self.bc.get(name, ()))
            for path in self.files.get(name, ()):
                head = path.read_bytes()[:60000].decode("utf-8", errors="replace")
                m = re.search(self._TOP_DECL % re.escape(name), head, re.M)
                if m:
                    parents.update(parse_parents(m.group(1)))
            self.memo[name] = parents
        return self.memo[name]

    def ancestors(self, seeds):
        seen, todo = set(), list(seeds)
        while todo:
            name = todo.pop()
            if name not in seen:
                seen.add(name)
                todo.extend(self.direct(name))
        return seen


def override_figures(results, types):
    """Section 4: BC @Override methods that override a method NeoForge injects through an I*Extension interface.

    A signature match (name plus parameter types) is only counted when the declaring BC type descends from the vanilla
    type that carries the extension (IBlockExtension -> Block, ...): `Slot.getMaxStackSize(ItemStack)` or BC's own
    `getDescription()` share a signature with an extension method without being patched."""
    matched, no_annotation, rejected = [], [], 0
    unresolved = Counter()
    per_method = defaultdict(lambda: {"tagged": 0, "untagged": 0})
    for r in results:
        for hit in r.overrides:
            ancestors = types.ancestors(hit["parents"] or ())
            carrier = None
            for iface in hit["ifaces"]:
                base = iface[1:-len("Extension")]
                if base in ancestors:
                    carrier = iface
                    break
            if carrier is None:
                if any(not types.known(i[1:-len("Extension")]) for i in hit["ifaces"]):
                    # The vanilla type is not where the naming convention puts it (a nested class, say): the match
                    # cannot be verified, so it stays out of the figures and is named in the report instead.
                    unresolved.update(hit["ifaces"])
                rejected += 1
                continue
            key = f"{carrier}#{hit['name']}"
            site = {"file": r.rel, "line": hit["line"], "method": key, "tagged": hit["tagged"]}
            if not hit["annotated"]:
                no_annotation.append(site)
                continue
            matched.append(site)
            per_method[key]["tagged" if hit["tagged"] else "untagged"] += 1
    tagged_n = sum(1 for m in matched if m["tagged"])
    methods = [{"method": k, **v}
               for k, v in sorted(per_method.items(), key=lambda kv: (-sum(kv[1].values()), kv[0]))]
    for sites in (matched, no_annotation):
        sites.sort(key=lambda m: (m["file"], m["line"]))
    return {"total": len(matched), "tagged": tagged_n, "untagged": len(matched) - tagged_n, "by_method": methods,
            "untagged_sites": [m for m in matched if not m["tagged"]],
            "without_override_annotation": no_annotation,
            "name_matches_rejected_by_hierarchy": rejected,
            "unresolved_carriers": dict(unresolved.most_common())}


def leak_figures(results):
    named = [{"file": r.rel, "line": ln} for r in results for ln in r.impl_named]
    contract = [{"file": r.rel, "line": ln, "what": what, "text": text}
                for r in results for ln, what, text in r.contract_leaks]
    return {"count": len(named) + len(contract), "impl_named_outside_binder": named, "contract_violations": contract}


def test_figures(results, top):
    h = headline(results, top)
    return {"files": h["files"], "imports": h["imports"], "fqn": h["fqn"], "string_refs": h["string_refs"],
            "inside_platform_neoforge": h["impl"], "by_group": h["by_group"][:8], "top_files": h["top_files"][:5],
            "directive_lines": directive_figures(results, 1)["lines"],
            "impl_named": sum(len(r.impl_named) for r in results if not r.rel.startswith(LOADER_PREFIXES))}


# ----------------------------------------------------------------------------------------------------------------------
# Locating inputs
# ----------------------------------------------------------------------------------------------------------------------

def active_node():
    try:
        text = (REPO / "stonecutter.gradle.kts").read_text(encoding="utf-8")
    except OSError:
        return None
    m = re.search(r'stonecutter\s+active\s+"([^"]+)"', text)
    return m.group(1) if m else None


def discover_nodes():
    versions = REPO / "versions"
    found = [p.name for p in versions.iterdir() if p.is_dir()] if versions.is_dir() else []
    names = [n for n in found if re.fullmatch(r"\d+(?:\.\d+)*(?:-\w+)?", n)]
    names = sorted(names, key=lambda n: (_vkey(n.split("-")[0]), n)) or list(FALLBACK_NODES)
    return [Node(n) for n in names]


def main_checkout():
    """The main checkout when REPO is a linked worktree (its .git is a file pointing at .git/worktrees/<name>)."""
    dot_git = REPO / ".git"
    if not dot_git.is_file():
        return None
    text = dot_git.read_text(encoding="utf-8", errors="replace").strip()
    if not text.startswith("gitdir:"):
        return None
    gitdir = Path(text[len("gitdir:"):].strip())
    if not gitdir.is_absolute():
        gitdir = (REPO / gitdir).resolve()
    commondir = gitdir / "commondir"
    if not commondir.is_file():
        return None
    common = (gitdir / commondir.read_text(encoding="utf-8").strip()).resolve()
    return common.parent if common.name == ".git" else None


def find_neoforge_ref(explicit):
    candidates = [Path(explicit)] if explicit else [REPO / ".neoforge-ref"]
    if not explicit:
        main = main_checkout()
        if main is not None:
            candidates.append(main / ".neoforge-ref")
    for c in candidates:
        if (c / "INDEX.txt").is_file():
            return c
    return None


def extension_methods_for(ref, node_name, warnings):
    """Harvest the I*Extension methods of the sources dir-set INDEX.txt lists for this node."""
    if ref is None:
        warnings.append("no .neoforge-ref found (checked the checkout and, from a worktree, the main checkout): "
                        "patched-override section skipped; run scripts/neoforge-sources-sync.sh or pass --neoforge-ref")
        return None, None, None
    sources = None
    for line in (ref / "INDEX.txt").read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[0] == "node" and parts[1] == node_name:
            sources = ref / parts[2].rstrip("/")
            break
    if sources is None or not sources.is_dir():
        warnings.append(f"no sources dir for node {node_name} in {ref / 'INDEX.txt'}: patched-override section skipped")
        return None, None, None
    files = []
    for sub in ("common/extensions", "client/extensions"):
        files += sorted((sources / "net/neoforged/neoforge" / sub).glob("I*Extension.java"))
    methods = []
    for f in files:
        methods += harvest_extension_methods(f)
    if not methods:
        warnings.append(f"no extension methods harvested under {sources}: patched-override section skipped")
        return None, None, None
    return methods, {"sources": str(sources), "interfaces": len(files), "methods": len(methods)}, sources


# ----------------------------------------------------------------------------------------------------------------------
# Report
# ----------------------------------------------------------------------------------------------------------------------

def build_report(args):
    warnings = []
    nodes = discover_nodes()
    node_names = [n.name for n in nodes]
    if args.node:
        if args.node not in node_names:
            raise CensusError(f"unknown node {args.node!r}; nodes under versions/: {', '.join(node_names)}")
        view, mode = args.node, f"{args.node} generated view"
        gen = REPO / "versions" / args.node / "build" / "generated" / "stonecutter"
        main_root, test_root = gen / "main" / "java", gen / "test" / "java"
        if not main_root.is_dir():
            raise CensusError(
                f"{main_root} does not exist; generate it first (./gradlew :{args.node}:stonecutterGenerate or any "
                f"compile of that node), then rerun")
    else:
        view = active_node() or "26.3"
        mode = f"{view} view"
        main_root, test_root = REPO / "src" / "main" / "java", REPO / "src" / "test" / "java"
        if not main_root.is_dir():
            raise CensusError(f"{main_root} does not exist; is --root a BuildCraft checkout?")

    ref = find_neoforge_ref(args.neoforge_ref)
    ext_methods, ext_info, sources = extension_methods_for(ref, view, warnings)
    ext_index = None
    if ext_methods is not None:
        ext_index = ExtensionIndex(ext_methods)

    main, disabled = read_tree(main_root, nodes, warnings, ext_index)
    if test_root.is_dir():
        tests, test_disabled = read_tree(test_root, nodes, warnings, None)
    else:
        tests, test_disabled = None, 0
        warnings.append(f"test tree not found at {test_root}: test figures skipped")

    head = headline(main, args.top)
    arms = commented_arms(main, view)
    overrides = None
    if ext_methods is not None:
        types = TypeIndex(main, [sources / "net" / "minecraft", sources / "net" / "neoforged"])
        overrides = override_figures(main, types)
    leaks = leak_figures(main)
    directives = directive_figures(main, args.top)

    report = {
        "view": view, "mode": mode, "source_root": str(main_root), "nodes": node_names,
        "disabled_files_skipped": disabled + test_disabled, "main_java_files": len(main),
        "headline": head, "commented_arms": arms, "allowlist": head["allowlist"],
        "overrides": overrides, "overrides_source": ext_info, "leaks": leaks, "directives": directives,
        "tests": test_figures(tests, args.top) if tests is not None else None,
        "warnings": sorted(set(warnings)),
    }
    report["summary"] = summary_line(report)
    return report


def summary_line(r):
    h, o = r["headline"], r["overrides"]
    tagged = f"{o['tagged']}/{o['total']}" if o is not None else "n/a"
    if o is not None and o["without_override_annotation"]:
        tagged += f" (+{len(o['without_override_annotation'])} without @Override)"
    return (f"platform census ({r['mode']}): {h['files']} files / {h['imports']} imports / {h['fqn']} FQN live outside "
            f"impl; allowlisted {h['allowlisted']}; remaining {h['remaining']} in {h['remaining_files']} files; "
            f"overrides tagged {tagged}; directives {r['directives']['lines']}; leaks {r['leaks']['count']}")


def _table(headers, rows, left=(0,)):
    cells = [[str(c) for c in row] for row in rows]
    widths = [max(len(headers[i]), *(len(row[i]) for row in cells)) if cells else len(headers[i])
              for i in range(len(headers))]

    def fmt(row):
        return "  " + "  ".join(c.ljust(widths[i]) if i in left else c.rjust(widths[i]) for i, c in enumerate(row))

    return [fmt(headers)] + [fmt(row) for row in cells]


def render_text(r):
    h, arms, d, lk = r["headline"], r["commented_arms"], r["directives"], r["leaks"]
    out = [r["summary"], ""]

    out.append("== 1. Live net.neoforged references outside platform/neoforge (headline) ==")
    out.append(f"  source             : {r['source_root']}  ({r['main_java_files']} .java files, "
               f"{r['disabled_files_skipped']} .disabled skipped)")
    out.append(f"  files with refs    : {h['files']}")
    out.append(f"  import lines       : {h['imports']}")
    out.append(f"  FQN references     : {h['fqn']}   (+{h['string_refs']} inside string literals, not counted)")
    out.append(f"  allowlisted        : {h['allowlisted']} refs")
    out.append(f"  remaining          : {h['remaining']} refs in {h['remaining_files']} files")
    out.append(f"  inside impl (not counted): {h['impl']['files']} files / {h['impl']['imports']} imports / "
               f"{h['impl']['fqn']} FQN under {IMPL_PREFIX}")
    out.append("")
    out.append("  by API group:")
    out += _table(["group", "imports", "fqn", "allowlisted", "files"],
                  [(g["name"], g["imports"], g["fqn"], g["allowlisted"], g["files"]) for g in h["by_group"]])
    out.append("")
    out.append("  by subsystem:")
    out += _table(["subsystem", "imports", "fqn", "allowlisted", "files"],
                  [(g["name"], g["imports"], g["fqn"], g["allowlisted"], g["files"]) for g in h["by_subsystem"]])
    out.append("")
    out.append(f"  top {len(h['top_files'])} files (by remaining refs):")
    out += _table(["file", "remaining", "imports", "fqn", "allowlisted"],
                  [(f["file"], f["remaining"], f["imports"], f["fqn"], f["allowlisted"]) for f in h["top_files"]])
    out.append("")

    out.append("== 2. net.neoforged lines in commented directive arms (informational) ==")
    out.append(f"  only the {LEGACY_NODE} node compiles them : {arms['legacy_only']['lines']} lines in "
               f"{arms['legacy_only']['files']} files   (do not count toward the finish line)")
    out.append(f"  other nodes compile them      : {arms['other_nodes']['lines']} lines in "
               f"{arms['other_nodes']['files']} files")
    for label, count in list(arms["other_nodes_by_node_set"].items())[:8]:
        out.append(f"      nodes {{{label}}}: {count}")
    if arms["unclassified"]["lines"]:
        out.append(f"  unclassified (condition not understood): {arms['unclassified']['lines']} lines")
    out.append(f"  prose mentions in comments of live arms: {arms['prose_in_live_arms']['lines']} lines in "
               f"{arms['prose_in_live_arms']['files']} files")
    out.append("  (arm membership is computed by evaluating each chain's condition against the nodes under versions/)")
    out.append("")

    out.append("== 3. Allowlisted references ==")
    for a in r["allowlist"]:
        out.append(f"  {a['name']}: {a['refs']} refs in {a['files']} files")
        out.append(f"      reason: {a['reason']}")
    out.append("")

    out.append("== 4. Patched-vanilla overrides ==")
    o = r["overrides"]
    if o is None:
        out.append("  skipped (see warnings)")
    else:
        info = r["overrides_source"]
        out.append(f"  extension methods harvested: {info['methods']} from {info['interfaces']} interfaces in "
                   f"{info['sources']}")
        out.append(f"  patched BC @Override methods: {o['total']}   tagged @LoaderHook: {o['tagged']}   "
                   f"untagged: {o['untagged']}")
        out.append(f"  ({o['name_matches_rejected_by_hierarchy']} same-signature methods ignored: their class does "
                   f"not descend from the vanilla type the extension patches)")
        if o["unresolved_carriers"]:
            out.append("  (unverifiable, vanilla carrier type not found by name, so not counted: "
                       + ", ".join(f"{k} x{v}" for k, v in o["unresolved_carriers"].items()) + ")")
        if o["by_method"]:
            out += _table(["method", "tagged", "untagged"],
                          [(m["method"], m["tagged"], m["untagged"]) for m in o["by_method"]])
        if o["without_override_annotation"]:
            out.append("  declared WITHOUT @Override (not in the figures above; they stop being called silently, not "
                       "loudly, on a loader without the hook):")
            for site in o["without_override_annotation"]:
                out.append(f"      {site['file']}:{site['line']}  {site['method']}")
        if o["untagged_sites"]:
            out.append("  untagged sites:")
            for site in o["untagged_sites"][:40]:
                out.append(f"      {site['file']}:{site['line']}  {site['method']}")
            if len(o["untagged_sites"]) > 40:
                out.append(f"      ... {len(o['untagged_sites']) - 40} more (see --json)")
    out.append("")

    out.append("== 5. Impl leaks ==")
    out.append(f"  leaks: {lk['count']}")
    for item in lk["impl_named_outside_binder"]:
        out.append(f"  LEAK names a loader impl package outside the binders: {item['file']}:{item['line']}")
    for item in lk["contract_violations"]:
        out.append(f"  LEAK platform contract names {item['what']} ({item['text']}): {item['file']}:{item['line']}")
    out.append("")

    out.append("== 6. Stonecutter directives remaining in common code ==")
    out.append(f"  {d['lines']} directive lines in {d['files']} files ({d['chains']} chains; "
               f"{d['loader_constant_lines']} lines name a loader constant)")
    out += _table(["subsystem", "lines"], [(k, v) for k, v in d["by_subsystem"].items()])
    out.append(f"  top {len(d['top_files'])} files:")
    out += _table(["file", "lines"], [(f["file"], f["lines"]) for f in d["top_files"]])
    out.append("")

    out.append("== 7. Test tree (informational) ==")
    t = r["tests"]
    if t is None:
        out.append("  not available")
    else:
        out.append(f"  {t['files']} files / {t['imports']} imports / {t['fqn']} FQN live outside "
                   f"src/test/.../platform/neoforge ({t['string_refs']} more inside string literals); "
                   f"inside it: {t['inside_platform_neoforge']['files']} files / "
                   f"{t['inside_platform_neoforge']['imports']} imports / {t['inside_platform_neoforge']['fqn']} FQN")
        out.append(f"  directive lines: {t['directive_lines']}; tests naming a loader impl package outside "
                   f"its test package: {t['impl_named']}")
        out += _table(["group", "imports", "fqn", "files"],
                      [(g["name"], g["imports"], g["fqn"], g["files"]) for g in t["by_group"]])
    if r["warnings"]:
        out.append("")
        out.append(f"== warnings ({len(r['warnings'])}) ==")
        out += [f"  {w}" for w in r["warnings"][:30]]
        if len(r["warnings"]) > 30:
            out.append(f"  ... {len(r['warnings']) - 30} more (see --json)")
    return "\n".join(out)


def show_live(rel):
    root = REPO / "src" / "main" / "java"
    path = root / rel
    if not path.is_file():
        raise CensusError(f"{path} does not exist")
    lx = lex(path.read_bytes().decode("utf-8", errors="replace"))
    for i, line in enumerate(lx.masked.split("\n"), 1):
        if line.strip():
            print(f"{i:5d}  {line.rstrip()}")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Platform-layer census (docs/platform-layer.md, Progress metric).")
    ap.add_argument("--node", help="read versions/<node>/build/generated/stonecutter/main/java instead of src/")
    ap.add_argument("--json", action="store_true", help="print one JSON object instead of the report")
    ap.add_argument("--summary", action="store_true", help="print only the one-line summary")
    ap.add_argument("--top", type=int, default=15, help="rows in the top-files lists (default 15)")
    ap.add_argument("--neoforge-ref", help="path of the .neoforge-ref directory (default: auto-detect)")
    ap.add_argument("--show-live", metavar="REL", help="print the live view of one src/main/java file and exit")
    ap.add_argument("--root", help="repository root to census (default: the parent of scripts/); for experiments")
    args = ap.parse_args(argv)
    if args.root:
        global REPO
        REPO = Path(args.root).resolve()

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    try:
        if args.show_live:
            show_live(args.show_live)
            return 0
        report = build_report(args)
    except CensusError as err:
        print(f"error: {err}", file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=False))
    elif args.summary:
        print(report["summary"])
    else:
        print(render_text(report))
    return 1 if report["leaks"]["count"] else 0


if __name__ == "__main__":
    sys.exit(main())
