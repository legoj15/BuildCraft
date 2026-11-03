"""
gen_io.py — write-if-changed file output shared by the generator scripts.

A Windows checkout (core.autocrlf=true) holds CRLF working copies of LF blobs. Rewriting an unchanged file
with other line endings shows it as modified in git status, so a regeneration that touched 4 pages used to
dirty 247. These helpers leave a file alone when only its line endings would change, keep the file's own
line endings when its content does change (LF for a new file), and prune only outputs no longer generated.
Needs Python 3.10+ (Path.write_text's newline argument).
"""

from pathlib import Path


def write_text(path, text):
    """Write text to path unless the file already holds it modulo line endings. Returns True if written."""
    path = Path(path)
    text = text.replace("\r\n", "\n")
    crlf = False
    if path.exists():
        old = path.read_bytes()
        if old.replace(b"\r\n", b"\n") == text.encode("utf-8"):
            return False
        crlf = b"\r\n" in old
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\r\n" if crlf else "\n")
    return True


def write_bytes(path, data):
    """Write data to path unless the file already holds exactly those bytes. Returns True if written."""
    path = Path(path)
    if path.exists() and path.read_bytes() == data:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return True


def prune(root, keep, pattern="**/*"):
    """Delete files under root matching pattern that are not in keep, then the directories those deletions left
    empty (never root itself, never a directory nothing was deleted from). Returns the number of files deleted."""
    root = Path(root).resolve()
    if not root.exists():
        return 0
    keep = {Path(p).resolve() for p in keep}
    emptied = set()
    removed = 0
    for p in root.glob(pattern):
        # generators never write symlinks, so one is not ours to prune (and unlinking a resolved path would
        # delete its target, possibly outside root)
        if p.is_symlink() or not p.is_file():
            continue
        if p.resolve() not in keep:
            p.unlink()
            removed += 1
            emptied.update(d for d in p.parents if d != root and root in d.parents)
    for d in sorted(emptied, key=lambda d: len(d.parts), reverse=True):
        if not any(d.iterdir()):
            d.rmdir()
    return removed
