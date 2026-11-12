#!/usr/bin/env python3
"""
guide-translation-validate.py
    Structural gate for translated guide-book pages (issue #182).

A translated page is free prose wrapped around a fixed skeleton: the loader (GuideManager
"loadLangInternal") overlays `compat/buildcraft/guide/<locale>/<page>.md` on the en_us page,
and a page whose markup drifted — a dropped <recipe>, a retyped link target, a translated
json_insn example — compiles, loads, and then quietly breaks in the book (a bad link target
NPEs at page open). This script is the gate between "a file an agent wrote" and "a file in
the repo", in the spirit of merge-translation.py. It checks, per page, against en_us:

  * the tag skeleton is identical: same tags, same order, same attributes; only the
    translatable attributes (chapter@name, link@text) may differ, and never be empty
  * <guide_md> and <json_insn> blocks are literal examples and stay byte-identical
  * every prose run of English has exactly one non-empty translated run (no dropped and no
    invented sentences), and every in-code `$[...]` token survives inside its run
  * the file is clean UTF-8 without a BOM, is not a byte-identical copy of English, and
    actually carries the locale's script (CJK for zh_cn, Cyrillic for ru_ru)
  * a page with nothing translatable (recipe tags only) must NOT be overlayed at all —
    the overlay would be dead weight the loader reads for nothing

scripts/guide-translation.stamp records, for every translated page, the sha256 of its en_us
source AND of the validated translation. GuideTranslationStalenessTester recomputes both:
edit the English page, or hand-edit a translation after validation, and the test fails until
`--stamp` is re-run (which itself refuses while any page is invalid).

Usage:
    python scripts/guide-translation-validate.py zh_cn [page ...]      # validate all, or listed pages (action/robot.md)
    python scripts/guide-translation-validate.py --stamp [--force]     # validate all locales, rewrite the stamp
    python scripts/guide-translation-validate.py --list-translatable   # en_us pages that carry translatable text
Stdlib only, plus guide_model for the loader's own `//` comment rule.
"""

import argparse
import hashlib
import re
import sys
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="backslashreplace")
    except (AttributeError, ValueError):
        pass

REPO = Path(__file__).resolve().parent.parent
GUIDE_DIR = REPO / "src" / "main" / "resources" / "assets" / "buildcraftunofficial" / "compat" / "buildcraft" / "guide"
EN_DIR = GUIDE_DIR / "en_us"
STAMP = REPO / "scripts" / "guide-translation.stamp"
LOCALES = ("zh_cn", "ru_ru")

# The loader ignores `//` lines (XmlPageLoader#loadParts); guide_model owns the rule.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from guide_model import strip_comment_lines  # noqa: E402

TAG = re.compile(r"</?[a-z_]+\b[^>]*?/?>")
ATTR = re.compile(r'([a-z_]+)\s*=\s*("((?:[^"\\]|\\.)*)"|\'((?:[^\'\\]|\\.)*)\')')
SPECIAL_TOKEN = re.compile(r"\$\[[^\]]*\](?:\([^)]*\))?")
# Attributes whose value is player-facing text and may (must) be translated; every other
# attribute — link targets, stack ids, recipe matches, url, type, level, count — is identity.
TRANSLATABLE_ATTRS = {"chapter": {"name"}, "link": {"text"}}
# Blocks holding literal example source; their inner text is code, not prose.
CODE_BLOCKS = ("guide_md", "json_insn")
# Enough of the locale's own script to prove this is not a copy of English.
SCRIPT_RANGE = {"zh_cn": (0x4E00, 0x9FFF), "ru_ru": (0x0400, 0x04FF)}


def normalize(text):
    """The bytes-level form every comparison and hash uses: UTF-8, no BOM, LF only."""
    return text.lstrip("﻿").replace("\r\n", "\n")


def sha256(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def parse_attrs(tag):
    out = {}
    for m in ATTR.finditer(tag):
        out[m.group(1)] = m.group(3) if m.group(3) is not None else m.group(4)
    return out


def tokenize(md):
    """Split comment-stripped markdown into a flat list of ('tag'|'code', ...) and
    ('text', run) items, in document order. A code block collapses into one item."""
    s = strip_comment_lines(md)
    items = []
    pos = 0
    open_code = None  # (name, start index after the opening tag)
    for m in TAG.finditer(s):
        if open_code is not None:
            if m.group(0) == f"</{open_code[0]}>":
                items.append(("code", open_code[0], s[open_code[1]:m.start()]))
                open_code = None
            continue
        items.append(("text", s[pos:m.start()]))
        name = re.match(r"</?([a-z_]+)", m.group(0)).group(1)
        if name in CODE_BLOCKS and not m.group(0).startswith("</") and not m.group(0).endswith("/>"):
            open_code = (name, m.end())
        else:
            items.append(("tag", m.group(0)))
        pos = m.end()
    if open_code is not None:
        raise ValueError(f"unclosed <{open_code[0]}> block")
    items.append(("text", s[pos:]))
    return items


def item_signature(item):
    """A tag item reduced to name + kind + identity attributes, for skeleton comparison."""
    if item[0] == "tag":
        tag = item[1]
        name = re.match(r"</?([a-z_]+)", tag).group(1)
        kind = "close" if tag.startswith("</") else "self" if tag.endswith("/>") else "open"
        return ("tag", name, kind)
    return item[:2]


def validate_page(locale, rel, en_text, tr_text, raw_tr=None):
    """Every problem found for one page; empty list means the page is valid. raw_tr is the
    file's unparsed text, where the BOM is still visible (read() strips it before comparing)."""
    problems = []
    page = f"{locale}/{rel}"

    if raw_tr is not None and raw_tr.startswith("﻿"):
        problems.append(f"{page}: starts with a UTF-8 BOM")

    en_items, tr_items = None, None
    try:
        en_items = tokenize(en_text)
    except ValueError as e:
        problems.append(f"en_us/{rel}: {e} (fix the English page first)")
        return problems
    try:
        tr_items = tokenize(tr_text)
    except ValueError as e:
        problems.append(f"{page}: {e}")
        return problems

    # Skeleton: same tags in the same order with the same identity attributes.
    en_tags = [i for i in en_items if i[0] != "text"]
    tr_tags = [i for i in tr_items if i[0] != "text"]
    if len(en_tags) != len(tr_tags):
        problems.append(f"{page}: {len(tr_tags)} tag(s) vs en_us {len(en_tags)}")
    for i, (en, tr) in enumerate(zip(en_tags, tr_tags)):
        if item_signature(en) != item_signature(tr):
            problems.append(f"{page}: tag #{i + 1} is {tr[1]!r}, en_us has {en[1]!r} at that position")
            continue
        if en[0] == "code":
            if en[2] != tr[2]:
                problems.append(f"{page}: <{en[1]}> example block differs from en_us (literal examples are never translated)")
            continue
        en_a, tr_a = parse_attrs(en[1]), parse_attrs(tr[1])
        if set(en_a) != set(tr_a):
            problems.append(f"{page}: tag #{i + 1} <{item_signature(en)[1]}> attributes {sorted(tr_a)} vs en_us {sorted(en_a)}")
            continue
        translatable = TRANSLATABLE_ATTRS.get(item_signature(en)[1], set())
        for key in en_a:
            if key in translatable:
                if not tr_a[key].strip():
                    problems.append(f"{page}: tag #{i + 1} attribute {key}= is empty")
            elif en_a[key] != tr_a[key]:
                problems.append(f'{page}: tag #{i + 1} attribute {key}={tr_a[key]!r} != en_us {en_a[key]!r}')

    # Prose: every English run has exactly one non-empty translated run, tokens preserved.
    en_runs = [i[1] for i in en_items if i[0] == "text"]
    tr_runs = [i[1] for i in tr_items if i[0] == "text"]
    if len(en_runs) != len(tr_runs):
        problems.append(f"{page}: {len(tr_runs)} prose run(s) vs en_us {len(en_runs)}")
    has_prose = False
    for i, (en, tr) in enumerate(zip(en_runs, tr_runs)):
        en_ws, tr_ws = en.strip(), tr.strip()
        if en_ws and not tr_ws:
            problems.append(f"{page}: prose run #{i + 1} is empty (en_us: {en_ws[:60]!r}...)")
        if en_ws or tr_ws:
            if bool(en_ws) != bool(tr_ws):
                problems.append(f"{page}: prose run #{i + 1} present only on one side")
            has_prose = has_prose or bool(en_ws)
        en_tokens = set(SPECIAL_TOKEN.findall(en))
        if en_tokens - set(SPECIAL_TOKEN.findall(tr)):
            problems.append(f"{page}: prose run #{i + 1} lost $[...] token(s) {sorted(en_tokens - set(SPECIAL_TOKEN.findall(tr)))}")

    # A page with nothing to say must not be overlayed at all.
    translatable_attr = any(
        parse_attrs(i[1]).get(next(iter(TRANSLATABLE_ATTRS[item_signature(i)[1]])), "").strip()
        for i in en_tags if i[0] == "tag" and item_signature(i)[1] in TRANSLATABLE_ATTRS
    )
    if not has_prose and not translatable_attr:
        problems.append(f"{page}: en_us page has no translatable text — delete this overlay, it is dead weight")

    if normalize(en_text) == normalize(tr_text):
        problems.append(f"{page}: is a byte-identical copy of en_us (not translated)")
    lo, hi = SCRIPT_RANGE[locale]
    if not any(lo <= ord(c) <= hi for c in tr_text):
        problems.append(f"{page}: contains no {locale} script — suspicious for a translation")
    return problems


def locale_pages(locale):
    root = GUIDE_DIR / locale
    if not root.is_dir():
        return []
    return sorted(p.relative_to(root).as_posix() for p in root.rglob("*.md"))


def read(path):
    return normalize(path.read_text(encoding="utf-8"))


def validate_locale(locale, only=None):
    problems, pages = [], locale_pages(locale)
    listed = set(only) if only else None
    if listed is not None:
        # An explicitly requested page that does not exist must not pass vacuously: "validate
        # this page" on a typo'd or never-written file is a failure, not an empty success.
        for rel in sorted(listed - set(pages)):
            problems.append(f"{locale}/{rel}: no such translated page (typo, or never written)")
    if not pages:
        return problems
    en_available = {p.relative_to(EN_DIR).as_posix() for p in EN_DIR.rglob("*.md")}
    for rel in pages:
        if listed is not None and rel not in listed:
            continue
        if rel not in en_available:
            problems.append(f"{locale}/{rel}: no en_us/{rel} to translate (orphan)")
            continue
        problems += validate_page(locale, rel, read(EN_DIR / rel), read(GUIDE_DIR / locale / rel),
                                  raw_tr=(GUIDE_DIR / locale / rel).read_text(encoding="utf-8"))
    return problems


def list_translatable():
    """en_us pages carrying prose or a translatable attribute — the pages worth overlaying."""
    out = []
    for p in sorted(EN_DIR.rglob("*.md")):
        rel = p.relative_to(EN_DIR).as_posix()
        items = tokenize(read(p))
        has_prose = any(i[0] == "text" and i[1].strip() for i in items)
        has_attr = any(
            i[0] == "tag" and item_signature(i)[1] in TRANSLATABLE_ATTRS
            and any(v.strip() for k, v in parse_attrs(i[1]).items() if k in TRANSLATABLE_ATTRS[item_signature(i)[1]])
            for i in items)
        if has_prose or has_attr:
            out.append(rel)
    return out


def previous_stamp():
    """{page -> (en_us sha, translation sha)} as the last --stamp run recorded it."""
    out = {}
    if not STAMP.exists():
        return out
    for ln in STAMP.read_text(encoding="utf-8").splitlines():
        if ln.strip() and not ln.startswith("#"):
            en_sha, tr_sha, page = ln.split(" ", 2)
            out[page] = (en_sha, tr_sha)
    return out


def write_stamp(force=False):
    """Rewrite the stamp, refusing (without --force) pages whose en_us source changed since the
    last stamp while their translation stayed byte-identical: that is the signature of a page
    re-stamped over a now-stale translation, which GuideTranslationStalenessTest cannot see —
    the new stamp simply blesses the old text. Retranslate those pages first, or pass --force
    when the English change provably needs no translation update (whitespace, comments)."""
    previous = previous_stamp()
    suspects, lines, count = [], [
        "# Written by scripts/guide-translation-validate.py; GuideTranslationStalenessTest checks it.",
        "# <en_us sha256> <translation sha256> <locale>/<page>", ""], 0
    for locale in LOCALES:
        for rel in locale_pages(locale):
            en_sha, tr_sha = sha256(read(EN_DIR / rel)), sha256(read(GUIDE_DIR / locale / rel))
            prev = previous.get(f"{locale}/{rel}")
            if prev and prev[0] != en_sha and prev[1] == tr_sha and not force:
                suspects.append(f"{locale}/{rel}")
            lines.append(f"{en_sha} {tr_sha} {locale}/{rel}")
            count += 1
    if suspects:
        print(f"REFUSING TO STAMP — {len(suspects)} page(s) whose English source changed but whose "
              f"translation did not (probably stale — retranslate them, or --force if the English "
              f"change needs no translation update):", file=sys.stderr)
        for s in suspects:
            print(f"  {s}", file=sys.stderr)
        return False
    STAMP.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {count} entr{'y' if count == 1 else 'ies'} to {STAMP.relative_to(REPO)}")
    return True


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("locale", nargs="?", choices=LOCALES, help="locale to validate")
    ap.add_argument("pages", nargs="*", help="specific pages, e.g. action/redstone.md (default: all)")
    ap.add_argument("--stamp", action="store_true", help="validate every locale, then rewrite the stamp (no locale needed)")
    ap.add_argument("--force", action="store_true", help="with --stamp: re-stamp pages whose English changed without a translation change")
    ap.add_argument("--list-translatable", action="store_true", help="print the en_us pages worth translating")
    args = ap.parse_args()

    if args.list_translatable:
        print("\n".join(list_translatable()))
        return 0
    if args.stamp:
        problems = []
        for locale in LOCALES:
            problems += validate_locale(locale)
        if problems:
            print(f"REFUSING TO STAMP — {len(problems)} problem(s):", file=sys.stderr)
            for p in problems:
                print(f"  {p}", file=sys.stderr)
            return 1
        return 0 if write_stamp(args.force) else 1
    if not args.locale:
        ap.error("a locale is required (or --stamp / --list-translatable)")

    problems = validate_locale(args.locale, args.pages)
    if problems:
        print(f"{len(problems)} problem(s):", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        return 1
    print(f"{args.locale}: all pages structurally valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
