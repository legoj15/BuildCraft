"""
BuildCraft Module Unification Migration Script
Migrates all resource namespaces from per-module IDs to 'buildcraftunofficial'
"""
import os
import shutil

ROOT = r"e:\GitHub\BuildCraft"
NEW_NS = "buildcraftunofficial"

OLD_NAMESPACES = [
    "buildcraftcore",
    "buildcraftlib",
    "buildcrafttransport",
    "buildcraftenergy",
    "buildcraftfactory",
    "buildcraftbuilders",
    "buildcraftsilicon",
    "buildcraftrobotics",
    "buildcraftapi",
]

SUBPROJECTS = [
    "buildcraft-api",
    "buildcraft-lib",
    "buildcraft-core",
    "buildcraft-builders",
    "buildcraft-energy",
    "buildcraft-factory",
    "buildcraft-transport",
    "buildcraft-silicon",
    "buildcraft-robotics",
]

TEXT_EXTENSIONS = {".json", ".toml", ".cfg", ".mcmeta", ".lang", ".txt"}

def replace_namespaces_in_text(text):
    for old_ns in OLD_NAMESPACES:
        text = text.replace(f"{old_ns}:", f"{NEW_NS}:")
        text = text.replace(f".{old_ns}.", f".{NEW_NS}.")
    return text

def process_text_file(filepath):
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            original = f.read()
    except (UnicodeDecodeError, PermissionError):
        return False
    updated = replace_namespaces_in_text(original)
    if updated != original:
        with open(filepath, "w", encoding="utf-8", newline="") as f:
            f.write(updated)
        return True
    return False

def merge_dirs(src, dst):
    for item in os.listdir(src):
        s = os.path.join(src, item)
        d = os.path.join(dst, item)
        if os.path.isdir(s):
            if os.path.exists(d):
                merge_dirs(s, d)
            else:
                shutil.move(s, d)
        else:
            if os.path.exists(d):
                print(f"  WARNING: collision: {d}")
            else:
                shutil.move(s, d)

def migrate_resource_dir(src_resources_root):
    renamed = []
    for subdir in ["assets", "data"]:
        parent = os.path.join(src_resources_root, subdir)
        if not os.path.isdir(parent):
            continue
        for old_ns in OLD_NAMESPACES:
            old_path = os.path.join(parent, old_ns)
            if not os.path.isdir(old_path):
                continue
            new_path = os.path.join(parent, NEW_NS)
            if os.path.isdir(new_path):
                for item in os.listdir(old_path):
                    src = os.path.join(old_path, item)
                    dst = os.path.join(new_path, item)
                    if os.path.isdir(src):
                        if os.path.exists(dst):
                            merge_dirs(src, dst)
                        else:
                            shutil.move(src, dst)
                    else:
                        if os.path.exists(dst):
                            print(f"  WARNING: collision: {dst}")
                        else:
                            shutil.move(src, dst)
                try:
                    shutil.rmtree(old_path)
                except OSError:
                    pass
            else:
                os.rename(old_path, new_path)
            renamed.append(f"  {os.path.relpath(old_path, ROOT)} -> {NEW_NS}")
    return renamed

def process_all_text_files(directory):
    count = 0
    for root, dirs, files in os.walk(directory):
        dirs[:] = [d for d in dirs if d not in {".git", ".gradle", "build", "bin", ".agents"}]
        for fname in files:
            ext = os.path.splitext(fname)[1].lower()
            if ext in TEXT_EXTENSIONS:
                filepath = os.path.join(root, fname)
                if process_text_file(filepath):
                    count += 1
    return count

def process_java_files(directory):
    count = 0
    for root, dirs, files in os.walk(directory):
        dirs[:] = [d for d in dirs if d not in {".git", ".gradle", "build", "bin"}]
        for fname in files:
            if not fname.endswith(".java") and not fname.endswith(".java.disabled"):
                continue
            filepath = os.path.join(root, fname)
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    original = f.read()
            except (UnicodeDecodeError, PermissionError):
                continue
            updated = original
            for old_ns in OLD_NAMESPACES:
                updated = updated.replace(f'"{old_ns}"', f'"{NEW_NS}"')
                updated = updated.replace(f'"{old_ns}:', f'"{NEW_NS}:')
                updated = updated.replace(f'.{old_ns}.', f'.{NEW_NS}.')
            if updated != original:
                with open(filepath, "w", encoding="utf-8", newline="") as f:
                    f.write(updated)
                count += 1
    return count

def main():
    print("=" * 60)
    print("BuildCraft Module Unification Migration")
    print(f"Target namespace: {NEW_NS}")
    print("=" * 60)

    print("\n--- Rename resource namespace directories ---")
    total_renames = 0
    for proj in SUBPROJECTS + ["buildcraft-all"]:
        res_root = os.path.join(ROOT, proj, "src", "main", "resources")
        if os.path.isdir(res_root):
            renames = migrate_resource_dir(res_root)
            for r in renames:
                print(r)
            total_renames += len(renames)
    print(f"  Total: {total_renames}")

    print("\n--- Update namespace refs in resource files ---")
    total_text = 0
    for proj in SUBPROJECTS + ["buildcraft-all"]:
        res_root = os.path.join(ROOT, proj, "src", "main", "resources")
        if os.path.isdir(res_root):
            total_text += process_all_text_files(res_root)
    print(f"  Total resource files updated: {total_text}")

    print("\n--- Update namespace refs in Java files ---")
    total_java = 0
    for proj in SUBPROJECTS:
        src_root = os.path.join(ROOT, proj, "src", "main", "java")
        if os.path.isdir(src_root):
            total_java += process_java_files(src_root)
        api_root = os.path.join(ROOT, proj, "api")
        if os.path.isdir(api_root):
            total_java += process_java_files(api_root)
    src_root = os.path.join(ROOT, "buildcraft-all", "src", "main", "java")
    if os.path.isdir(src_root):
        total_java += process_java_files(src_root)
    print(f"  Total Java files updated: {total_java}")

    print("\n" + "=" * 60)
    print(f"Done! Dirs:{total_renames} Resources:{total_text} Java:{total_java}")
    print("=" * 60)

if __name__ == "__main__":
    main()
