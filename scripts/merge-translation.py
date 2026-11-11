#!/usr/bin/env python3
"""
merge-translation.py
    Validates a returned translation file and merges it into a locale's lang file.

Translations usually arrive as a flat {key: value} JSON from a translator or an LLM. Nothing about
that file is trustworthy on arrival: a lang file is data, so a broken value compiles, loads, and
renders. This script is the gate between "a file someone sent us" and "a file in the repo".

It refuses to merge if anything below fails, and prints every problem at once rather than stopping
at the first:

  * invalid JSON, or a top-level value that is not an object of strings
  * duplicate keys in the incoming file (JSON parsers silently keep the last one)
  * keys that do not exist in en_us.json (a typo'd key is dead weight that never renders)
  * a placeholder count that disagrees with the English (%s/%d are substituted at runtime, so a
    dropped one hardcodes whatever it would have supplied and an extra one throws at render time)
  * a literal backslash-n where a real newline escape was meant, which prints as the two characters
  * a double-quote in a key that gets spliced into the guide book's XML

Usage:
    python scripts/merge-translation.py <incoming.json> [locale]      # default locale: zh_cn
    python scripts/merge-translation.py <incoming.json> zh_cn --dry-run
    python scripts/merge-translation.py --report-only [locale]        # just show coverage

On merge the locale file is rewritten in en_us.json's key ORDER. That is a one-time large diff on a
file that was not already ordered, and permanently smaller diffs afterwards: the two files can then
be read side by side, and a missing translation is visible as a gap rather than found by tooling.
"""

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

# Translated values are non-ASCII by definition, and this script prints them (the overwrite diff).
# Windows consoles default to a legacy code page, where printing CJK raises UnicodeEncodeError and
# would crash the tool on exactly the input it exists to handle. Force UTF-8 and degrade instead.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="backslashreplace")
    except (AttributeError, ValueError):  # not a reconfigurable stream (piped, redirected, embedded)
        pass

REPO = Path(__file__).resolve().parent.parent
LANG_DIR = REPO / "src" / "main" / "resources" / "assets" / "buildcraftunofficial" / "lang"
EN = LANG_DIR / "en_us.json"

# %s / %d including positional (%1$s) forms, matching how the game's formatter reads them.
FORMAT_SPEC = re.compile(r"%(?:\d+\$)?[sd]")
# The argument index of a positional placeholder, e.g. the "2" in %2$s.
POSITIONAL_INDEX = re.compile(r"%(\d+)\$[sd]")
# One "key": declaration at the start of a line — used to spot duplicates, which a parsed dict hides.
KEY_LINE = re.compile(r'(?m)^\s*"((?:[^"\\]|\\.)+)"\s*:')
# Values spliced into the guide book's XML; XmlPageLoader's attribute reader stops at the first quote.
XML_SPLICED_KEYS = {"buildcraft.guide.page.wip.title"}
# The language's own counting rule for fluid unit names (#183). An incoming file made by copying en_us
# carries English's value for it, which must not replace the one the locale already declares.
PLURAL_RULE_KEY = "buildcraft.unit.plural_rule"
# Number forms of a unit name that only a language with more than two forms carries (Russian: one / few /
# many). English never has them, so they are legitimate keys that en_us.json lacks; LocaleUtil reads them
# before the .full.singular / .full.plural pair. LangFileIntegrityTester has the matching exemption.
PER_LANGUAGE_KEY = re.compile(r"buildcraft\.unit\.(?:milli)?bucket\.full\.(?:one|few|many)")
PER_LANGUAGE_FORMS = ("one", "few", "many")


def is_per_language(key):
    return PER_LANGUAGE_KEY.fullmatch(key) is not None


def load(path):
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as e:
        sys.exit(f"error: cannot read {path}: {e}")
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        sys.exit(f"error: {path.name} is not valid JSON: {e}")
    if not isinstance(data, dict):
        sys.exit(f"error: {path.name} must be a JSON object mapping key -> string")
    return text, data


def duplicate_keys(text):
    return [k for k, n in Counter(KEY_LINE.findall(text)).items() if n > 1]


def validate(incoming, incoming_text, english):
    problems = []

    for key in duplicate_keys(incoming_text):
        problems.append(f"{key}: declared more than once — JSON keeps only the last value, silently")

    for key, value in incoming.items():
        if not isinstance(value, str):
            problems.append(f"{key}: value is {type(value).__name__}, expected a string")
            continue
        if is_per_language(key):
            if FORMAT_SPEC.search(value) or not value.strip():
                problems.append(f"{key}: a unit name form must be a plain, non-empty word")
            continue
        if key not in english:
            problems.append(f"{key}: not present in en_us.json — typo, or a key that has been removed")
            continue

        expected = len(FORMAT_SPEC.findall(english[key]))
        actual = len(FORMAT_SPEC.findall(value))
        if expected != actual:
            problems.append(
                f"{key}: has {actual} format placeholder(s), English has {expected} "
                f"(en={english[key]!r})"
            )

        # Reordering requires positional syntax (%2$s), since plain %s is filled left to right.
        # An index past the argument count throws MissingFormatArgumentException at render time,
        # which the count check above cannot see: "%2$s %2$s" has the right count and still breaks.
        for index in (int(m) for m in POSITIONAL_INDEX.findall(value)):
            if index < 1 or index > expected:
                problems.append(
                    f"{key}: uses %{index}$ but the string only takes {expected} argument(s) — "
                    f"positional indices are 1-based and numbered by the ENGLISH order"
                )

        if "\\n" in value:
            problems.append(
                f"{key}: contains a literal backslash-n; write a real newline escape, "
                f"otherwise the player sees the two characters"
            )

        if key in XML_SPLICED_KEYS and '"' in value:
            problems.append(f'{key}: is spliced into XML and must not contain a double-quote (use 「」)')

    return problems


def write_ordered(path, english, merged):
    """Rewrite `path` following en_us.json's key order, keeping only keys English still has plus the
    per-language unit forms, each group placed right after its unit's .full.plural key."""
    lines = ["{"]
    ordered = []
    for k in english:
        if k in merged:
            ordered.append(k)
        if k.endswith(".full.plural"):
            base = k[: -len("plural")]
            ordered.extend(base + f for f in PER_LANGUAGE_FORMS if is_per_language(base + f) and base + f in merged)
    for i, key in enumerate(ordered):
        comma = "," if i < len(ordered) - 1 else ""
        lines.append(f"    {json.dumps(key, ensure_ascii=False)}: "
                     f"{json.dumps(merged[key], ensure_ascii=False)}{comma}")
    lines.append("}")
    # newline="" writes the bytes as given. Text mode on Windows would translate the "\n" of each "\r\n"
    # again and leave "\r\r\n" on every line, which the first ru_ru.json merge shipped to a reviewer.
    with path.open("w", encoding="utf-8", newline="") as out:
        out.write("\r\n".join(lines) + "\r\n")
    return len(ordered)


def report(english, target_data, locale):
    # The plural rule is optional: a language that omits it counts the English way.
    missing = [k for k in english if k not in target_data and k != PLURAL_RULE_KEY]
    stale = [k for k in target_data if k not in english and not is_per_language(k)]
    own = sum(1 for k in target_data if is_per_language(k))
    pct = 100.0 * (len(english) - len(missing)) / len(english) if english else 0.0
    print(f"{locale}: {len(target_data) - own} / {len(english)} keys ({pct:.1f}% translated)"
          + (f", plus {own} unit number form(s) of its own" if own else ""))
    if missing:
        print(f"  untranslated : {len(missing)}")
    if stale:
        print(f"  STALE (not in en_us, will be dropped on merge): {len(stale)}")
        for k in sorted(stale)[:20]:
            print(f"    {k}")
        if len(stale) > 20:
            print(f"    ... and {len(stale) - 20} more")
    return missing


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("incoming", nargs="?", help="JSON file of {key: translated value}")
    ap.add_argument("locale", nargs="?", default="zh_cn", help="target locale (default: zh_cn)")
    ap.add_argument("--dry-run", action="store_true", help="validate and report, write nothing")
    ap.add_argument("--report-only", action="store_true", help="show coverage for the locale and exit")
    args = ap.parse_args()

    _, english = load(EN)
    target = LANG_DIR / f"{args.locale}.json"

    if args.report_only and args.incoming and not Path(args.incoming).exists():
        # "--report-only ru_ru": the lone positional is the locale, not an incoming file.
        args.locale = args.incoming
        target = LANG_DIR / f"{args.locale}.json"
    if args.report_only or not args.incoming:
        if not target.exists():
            sys.exit(f"error: {target} does not exist")
        _, existing = load(target)
        report(english, existing, args.locale)
        return 0

    incoming_path = Path(args.incoming)
    incoming_text, incoming = load(incoming_path)

    problems = validate(incoming, incoming_text, english)
    if problems:
        print(f"REFUSING TO MERGE — {len(problems)} problem(s) in {incoming_path.name}:\n", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        print("\nNothing was written. Fix the incoming file and re-run.", file=sys.stderr)
        return 1

    existing = {}
    if target.exists():
        _, existing = load(target)

    overwritten = sorted(k for k in incoming if k in existing and existing[k] != incoming[k])
    merged = {**existing, **incoming}
    # A copy of en_us sends English's rule; the locale's own declaration wins over that, never over a real one.
    if (PLURAL_RULE_KEY in existing and incoming.get(PLURAL_RULE_KEY) == english.get(PLURAL_RULE_KEY)
            and existing[PLURAL_RULE_KEY] != incoming[PLURAL_RULE_KEY]):
        merged[PLURAL_RULE_KEY] = existing[PLURAL_RULE_KEY]
        overwritten.remove(PLURAL_RULE_KEY)
        print(f"  kept {PLURAL_RULE_KEY} = {existing[PLURAL_RULE_KEY]!r} (the incoming file carries English's)")

    print(f"{incoming_path.name}: {len(incoming)} entries, all valid.")
    if overwritten:
        print(f"  {len(overwritten)} existing translation(s) will CHANGE:")
        for k in overwritten[:15]:
            print(f"    {k}")
            print(f"      old: {existing[k]!r}")
            print(f"      new: {incoming[k]!r}")
        if len(overwritten) > 15:
            print(f"    ... and {len(overwritten) - 15} more")

    if args.dry_run:
        print("\n--dry-run: nothing written.")
        after = {k: v for k, v in merged.items() if k in english or is_per_language(k)}
        report(english, after, args.locale)
        return 0

    written = write_ordered(target, english, merged)
    print(f"\nwrote {written} keys to {target.relative_to(REPO)}")
    _, after = load(target)
    report(english, after, args.locale)
    print("\nNow run:  ./gradlew :26.3:test --tests buildcraft.lib.misc.LangFileIntegrityTester   (LangFileIntegrityTester re-checks the merged file)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
