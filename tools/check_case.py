import os
import json
import re

def check_dict(d, filepath):
    issues = []
    if isinstance(d, dict):
        for k, v in d.items():
            if isinstance(v, str):
                if re.search(r'[A-Z]', v) and (':' in v or '/' in v):
                    issues.append(f"Contains uppercase in string: {v}")
            elif isinstance(v, (dict, list)):
                issues.extend(check_dict(v, filepath))
    elif isinstance(d, list):
        for item in d:
            issues.extend(check_dict(item, filepath))
    return issues

assets_dir = r"src\main\resources"
for root, dirs, files in os.walk(assets_dir):
    for f in files:
        if f.endswith('.json'):
            path = os.path.join(root, f)
            try:
                with open(path, 'r', encoding='utf-8') as file:
                    data = json.load(file)
                    issues = check_dict(data, path)
                    if issues:
                        print(f"File: {path}")
                        for i in issues:
                            print(f"  {i}")
            except Exception as e:
                pass
