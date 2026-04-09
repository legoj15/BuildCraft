import os
import re
import subprocess

TARGET_DIR = r"src\main"
EXTENSIONS = {'.json', '.java', '.mcmeta', '.txt', '.cfg'}

def lowercase_file_contents(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return False
        
    def replacer(match):
        return match.group(0).lower()
        
    # Replace anything starting with buildcraftunofficial: followed by letters/numbers/_/.
    new_content = re.sub(r'(buildcraft[a-z]*:)([A-Za-z0-9_/.-]+)', replacer, content)
    
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False

# 1. Lowercase file contents
changed_contents = 0
for root, dirs, files in os.walk(TARGET_DIR):
    for f in files:
        if os.path.splitext(f)[1].lower() in EXTENSIONS:
            if lowercase_file_contents(os.path.join(root, f)):
                changed_contents += 1

# 2. Lowercase file and folder names using git mv
renamed_files = 0
for root, dirs, files in os.walk(TARGET_DIR, topdown=False): # Bottom-up to not break paths
    for name in files + dirs:
        if re.search(r'[A-Z]', name):
            old_path = os.path.join(root, name)
            new_name = name.lower()
            new_path = os.path.join(root, new_name)
            
            # Git mv requires a temp name if only case changes in case-insensitive OS
            temp_path = old_path + "_temp_rename"
            os.rename(old_path, temp_path)
            subprocess.run(["git", "mv", temp_path, new_path], check=False, capture_output=True)
            if not os.path.exists(new_path):
                # Fallback if git mv failed
                os.rename(temp_path, new_path)
            renamed_files += 1

print(f"Updated {changed_contents} files' contents.")
print(f"Renamed {renamed_files} files/directories to lowercase.")
