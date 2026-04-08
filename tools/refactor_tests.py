import os
import shutil

src_test_dir = r"src\test\java\buildcraft\test"
target_dir = r"src\test\java\buildcraft"

# Move all directories/files from buildcraft/test directly up to buildcraft/
for item in os.listdir(src_test_dir):
    s = os.path.join(src_test_dir, item)
    d = os.path.join(target_dir, item)
    shutil.move(s, d)

# Now, iterate over all files in src/test/java/buildcraft and replace package/imports
for root, dirs, files in os.walk(target_dir):
    for f in files:
        if f.endswith('.java') or f.endswith('.disabled'):
            filepath = os.path.join(root, f)
            with open(filepath, 'r', encoding='utf-8') as file:
                content = file.read()
            
            # Replace package strings
            new_content = content.replace("package buildcraft.test.", "package buildcraft.")
            new_content = new_content.replace("package buildcraft.test;", "package buildcraft;")
            
            # Replace import strings
            new_content = new_content.replace("import buildcraft.test.", "import buildcraft.")
            
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as file:
                    file.write(new_content)

# Delete the now empty 'test' directory inside buildcraft
try:
    os.rmdir(src_test_dir)
except OSError:
    pass

print("Test refactor complete!")
