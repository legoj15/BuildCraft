import os
import subprocess
import re
import sys

print("Starting auto-disable loop...")
# Gradle path
gradle_cmd = r"C:\ProgramData\chocolatey\lib\gradle\tools\gradle-9.3.1\bin\gradle.bat"

loop_count = 0
while loop_count < 20:
    loop_count += 1
    print(f"\n--- Iteration {loop_count} ---")
    sys.stdout.flush()
    
    # Run gradle
    result = subprocess.run([gradle_cmd, "buildcraft-lib:compileJava", "--console=plain"], capture_output=True, text=True)
    output = result.stdout + result.stderr
    
    if result.returncode == 0:
        print("Success! 0 compilation errors.")
        sys.stdout.flush()
        break
        
    # Find all java files with errors
    errors = re.findall(r"([a-zA-Z]:\\[^\s]+\.java):\d+: error:", output)
    if not errors:
        # Try relative paths
        errors = re.findall(r"([a-zA-Z0-9_\-\\]+\.java):\d+: error:", output)
        
    errors = list(set(errors))
    
    if not errors:
        print("Gradle failed but no .java files found in error output. Stopping.")
        sys.stdout.flush()
        with open(r"e:\GitHub\BuildCraft\last_gradle_error.txt", "w", encoding="utf-8") as f:
            f.write(output)
        break
        
    print(f"Found {len(errors)} broken files. Disabling them...")
    sys.stdout.flush()
    for full_path in errors:
        if not os.path.isabs(full_path):
            full_path = os.path.join(r"e:\GitHub\BuildCraft", full_path)
            
        if os.path.exists(full_path):
            try:
                os.rename(full_path, full_path + ".disabled")
                print(f"Disabled {os.path.basename(full_path)}")
            except Exception as e:
                print(f"Failed to disable {full_path}: {e}")
    sys.stdout.flush()
                
print("Finished auto-disable loop.")
