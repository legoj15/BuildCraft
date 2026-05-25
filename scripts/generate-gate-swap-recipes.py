#!/usr/bin/env python3
"""Generate the AND<->OR gate swap shapeless recipes.

1.12.2's BuildCraft let a player flip a gate's logic by placing it alone in the
crafting grid (shapeless 1-ingredient with NBT-matched input). We restore that
here as a fan-out of static JSONs — one per (material, modifier, direction).

The CLAY_BRICK material is intentionally excluded; 1.12.2 skipped it and so do
we (only the AND/NO_MODIFIER variant of that gate is registered anywhere).

Run:
    python3 scripts/generate-gate-swap-recipes.py

Idempotent; safe to re-run. Output goes under
src/main/resources/data/buildcraftunofficial/recipe/gate_swap_*.json.
"""

from pathlib import Path

# Keep enum ordinals in lock-step with EnumGateMaterial / EnumGateLogic /
# EnumGateModifier in src/main/java/buildcraft/silicon/gate/. If those reorder
# (which would be a save-breaking change anyway), update here too.
MATERIALS = [("iron", 1), ("nether_brick", 2), ("gold", 3)]
MODIFIERS = [("no_modifier", 0), ("lapis", 1), ("quartz", 2), ("diamond", 3)]

OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "buildcraftunofficial" / "recipe"


def variant_name(material: str, logic: str, modifier: str) -> str:
    # Mirrors GateVariant.getVariantName() for modifiable materials.
    return f"{material}_{logic}_{modifier}"


def gate_components(material_ord: int, logic_ord: int, modifier_ord: int, variant_name_str: str) -> dict:
    # The custom_data CompoundTag stores logic/material/modifier as bytes (see
    # GateVariant.writeToNBT()). SNBT byte literals (`0b`) are the only way to
    # preserve the byte type through JSON — using JSON ints serialises as IntTag,
    # which `nbt.getByte()` returns 0 for in strict-type mode.
    snbt = f"{{gate:{{logic:{logic_ord}b,material:{material_ord}b,modifier:{modifier_ord}b}}}}"
    return {
        "minecraft:custom_data": snbt,
        "minecraft:custom_model_data": {"strings": [variant_name_str]},
    }


def write_recipe(material: str, material_ord: int, modifier: str, modifier_ord: int, src_logic: str, src_logic_ord: int, dst_logic: str, dst_logic_ord: int) -> None:
    src_variant = variant_name(material, src_logic, modifier)
    dst_variant = variant_name(material, dst_logic, modifier)

    recipe = {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "group": "buildcraft_gate_swap",
        "ingredients": [
            {
                # NeoForge's custom-ingredient codec dispatches on `neoforge:ingredient_type`,
                # not `type` — `type` is the *recipe*-level dispatch key.
                "neoforge:ingredient_type": "neoforge:components",
                "items": "buildcraftunofficial:plug_gate",
                "components": gate_components(material_ord, src_logic_ord, modifier_ord, src_variant),
            }
        ],
        "result": {
            "id": "buildcraftunofficial:plug_gate",
            "count": 1,
            "components": gate_components(material_ord, dst_logic_ord, modifier_ord, dst_variant),
        },
    }

    path = OUT_DIR / f"gate_swap_{src_variant}_to_{dst_logic}.json"
    import json
    path.write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for material, material_ord in MATERIALS:
        for modifier, modifier_ord in MODIFIERS:
            # AND -> OR
            write_recipe(material, material_ord, modifier, modifier_ord, "and", 0, "or", 1)
            # OR -> AND
            write_recipe(material, material_ord, modifier, modifier_ord, "or", 1, "and", 0)
            count += 2
    print(f"Wrote {count} gate-swap recipes to {OUT_DIR}")


if __name__ == "__main__":
    main()
