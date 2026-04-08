import os

ROOT = r"e:\GitHub\BuildCraft"
NEW_NS = "buildcraftunofficial"
OLD_NAMESPACES = ["buildcraftcore","buildcraftlib","buildcrafttransport","buildcraftenergy","buildcraftfactory","buildcraftbuilders","buildcraftsilicon","buildcraftrobotics","buildcraftapi"]

SCAN_DIRS = []
for proj in ["buildcraft-api","buildcraft-lib","buildcraft-core","buildcraft-builders","buildcraft-energy","buildcraft-factory","buildcraft-transport","buildcraft-silicon","buildcraft-robotics","buildcraft-all"]:
    d = os.path.join(ROOT, proj, "src", "main", "java")
    if os.path.isdir(d): SCAN_DIRS.append(d)
    d = os.path.join(ROOT, proj, "api")
    if os.path.isdir(d): SCAN_DIRS.append(d)

count = 0
for scan_dir in SCAN_DIRS:
    for root, dirs, files in os.walk(scan_dir):
        dirs[:] = [d for d in dirs if d not in {".git", ".gradle", "build", "bin"}]
        for fname in files:
            if not fname.endswith(".java") and not fname.endswith(".java.disabled"):
                continue
            fp = os.path.join(root, fname)
            try:
                with open(fp, "r", encoding="utf-8") as f:
                    orig = f.read()
            except:
                continue
            upd = orig
            for old_ns in OLD_NAMESPACES:
                upd = upd.replace(f'"{old_ns}"', f'"{NEW_NS}"')
                upd = upd.replace(f'"{old_ns}:', f'"{NEW_NS}:')
                upd = upd.replace(f".{old_ns}.", f".{NEW_NS}.")
            if upd != orig:
                with open(fp, "w", encoding="utf-8", newline="") as f:
                    f.write(upd)
                count += 1
                print(os.path.relpath(fp, ROOT))

print(f"\nUpdated {count} Java files")
