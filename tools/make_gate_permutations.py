import os
import json

base_dir = r"e:\GitHub\BuildCraft\buildcraft-silicon\src\main\resources\assets\buildcraftsilicon\models\item"
gates_dir = os.path.join(base_dir, "gates", "variants")
os.makedirs(gates_dir, exist_ok=True)

# Clear old variants
for f in os.listdir(gates_dir):
    if f.endswith('.json'):
        os.remove(os.path.join(gates_dir, f))

# Material textures (base layer) - our own gate sprites
material_textures = {
    'clay_brick': 'buildcraftsilicon:item/gates/material_clay_brick',
    'iron': 'buildcraftsilicon:item/gates/material_iron',
    'nether_brick': 'buildcraftsilicon:item/gates/material_nether_brick',
    'gold': 'buildcraftsilicon:item/gates/material_gold',
}

# Logic textures (overlay layer)
logic_textures = {
    'and': 'buildcraftsilicon:item/gates/logic_and',
    'or': 'buildcraftsilicon:item/gates/logic_or',
}

# Modifier textures (overlay layer)
modifier_textures = {
    'lapis': 'buildcraftsilicon:item/gates/modifier_lapis',
    'quartz': 'buildcraftsilicon:item/gates/modifier_quartz',
    'diamond': 'buildcraftsilicon:item/gates/modifier_diamond',
}

can_be_modified = {'clay_brick': False, 'iron': True, 'nether_brick': True, 'gold': True}
logics = ['and', 'or']
modifiers = ['no_modifier', 'lapis', 'quartz', 'diamond']

select_cases = []

def write_variant(variant_name, mat_tag, log_tag, mod_tag):
    path = os.path.join(gates_dir, f"{variant_name}.json")
    
    layers = {"layer0": material_textures[mat_tag]}
    if log_tag:
        layers["layer1"] = logic_textures[log_tag]
    if mod_tag and mod_tag != 'no_modifier':
        layers["layer2"] = modifier_textures[mod_tag]

    with open(path, 'w') as f:
        json.dump({
            "parent": "minecraft:item/generated",
            "textures": layers
        }, f, indent=4)
    
    select_cases.append({
        "when": variant_name,
        "model": {
            "type": "minecraft:model",
            "model": f"buildcraftsilicon:item/gates/variants/{variant_name}"
        }
    })

for mat in material_textures:
    if not can_be_modified[mat]:
        write_variant(mat, mat, None, None)
    else:
        for logic in logics:
            for mod in modifiers:
                variant_name = f"{mat}_{logic}_{mod}"
                write_variant(variant_name, mat, logic, mod)

# Generate plug_gate.json in items/ directory (item model definition, not geometry)
items_dir = r"e:\GitHub\BuildCraft\buildcraft-silicon\src\main\resources\assets\buildcraftsilicon\items"
plug_gate_path = os.path.join(items_dir, "plug_gate.json")
with open(plug_gate_path, 'w') as f:
    json.dump({
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "fallback": {
                "type": "minecraft:model",
                "model": "buildcraftsilicon:item/gates/variants/iron_and_no_modifier"
            },
            "cases": select_cases
        }
    }, f, indent=4)

print(f"Generated {len(select_cases)} variants and plug_gate.json")
