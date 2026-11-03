"""
optimize-pngs.py — lossless recompression of the repo's PNGs, with a pixel-identity check.

Runs oxipng at its strongest setting (-o max --zopfli) over the given PNGs, or every tracked PNG when none are
given. oxipng only picks a palette / grayscale / lower bit depth when every pixel survives, and it leaves the
colour under fully transparent pixels alone (that colour bleeds through mipmapping and linear filtering, so never
add --alpha). Every NativeImage read path forces RGBA, and vanilla itself ships 1/2/4/8-bit palette and grayscale
textures, so any colour type oxipng chooses loads the same. Afterwards each PNG that git shows as modified is
decoded and compared, RGBA byte for byte, with its HEAD blob; a mismatch exits 1. Commit art edits first, or the
check reports them as mismatches too.

Usage (from the repo root):  python scripts/optimize-pngs.py [file.png ...]
Needs oxipng on PATH (cargo install oxipng) and Pillow.
"""

import io
import subprocess
import sys

from PIL import Image


def git(*args, text=True):
    return subprocess.run(["git", *args], capture_output=True, text=text, check=True).stdout


def main():
    files = sys.argv[1:] or git("ls-files", "*.png").splitlines()
    if files:
        subprocess.run(["oxipng", "-o", "max", "--zopfli", "--strip", "safe", "-q", *files], check=True)

    changed = git("diff", "--name-only", "--", "*.png").splitlines()
    tracked = set(git("ls-files", "*.png").splitlines())
    bad = 0
    for path in changed:
        if path not in tracked:
            continue
        before = Image.open(io.BytesIO(git("show", f"HEAD:{path}", text=False)))
        after = Image.open(path)
        if before.size != after.size or before.convert("RGBA").tobytes() != after.convert("RGBA").tobytes():
            bad += 1
            print(f"PIXELS DIFFER: {path} ({before.mode} -> {after.mode})")
    print(f"{len(changed)} PNG(s) differ from HEAD, {bad} with changed pixels")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
