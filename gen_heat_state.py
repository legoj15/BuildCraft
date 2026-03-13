import json
import os

state = {
    "variants": {}
}

facings = ["north", "east", "south", "west"]
parts = ["start", "middle", "end"]
booleans = ["false", "true"]

rotations = {
    "north": {"start": 180, "middle": 0, "end": 0},
    "east":  {"start": 270, "middle": 90, "end": 90},
    "south": {"start": 0, "middle": 0, "end": 180},
    "west":  {"start": 90, "middle": 90, "end": 270}
}

models = {
    "start": "buildcraftfactory:block/heat_exchange_joiner",
    "middle": "buildcraftfactory:block/heat_exchange_middle",
    "end": "buildcraftfactory:block/heat_exchange_end"
}

for conn_l in booleans:
    for conn_r in booleans:
        for f in facings:
            for p in parts:
                variant_name = f"connected_left={conn_l},connected_right={conn_r},facing={f},part={p}"
                
                # Check if it actually connects
                # If it's a start, it only has a graphic when connected appropriately,
                # but we will just output the base geometry anyway
                
                model_str = models[p]
                
                # Also, if it's purely a middle block with both connections false,
                # maybe we don't display it or it just looks identical.
                # Standard is it just uses the middle rotation.
                
                y_rot = rotations[f][p]
                
                entry = {"model": model_str}
                if y_rot != 0:
                    entry["y"] = y_rot
                    
                state["variants"][variant_name] = entry

output_path = r"e:\GitHub\BuildCraft\buildcraft-factory\src\main\resources\assets\buildcraftfactory\blockstates\heat_exchange.json"
with open(output_path, "w") as f:
    json.dump(state, f, indent=4)

print("Generated blockstate JSON!")
