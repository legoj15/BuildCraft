"""
Fix gate textures:
1. Copy original 1.12.2 textures from buildcraft_resources to the correct locations
2. Convert all palette-indexed PNGs to RGBA (MC requires RGBA)
3. Generate missing material textures (clay_brick, nether_brick) from the iron base
"""
import os
import shutil
from PIL import Image

src_dir = r"e:\GitHub\BuildCraft\buildcraft_resources\assets\buildcraftsilicon\textures\items\gates"
dst_dir = r"e:\GitHub\BuildCraft\buildcraft-silicon\src\main\resources\assets\buildcraftsilicon\textures\item\gates"

os.makedirs(dst_dir, exist_ok=True)

# Map original 1.12.2 names -> our names
# Materials (layer 0 - base shape, these are unique gate shapes)
material_map = {
    'gate_material_iron.png': 'material_iron.png',
    'gate_material_gold.png': 'material_gold.png',
}

# Logic overlays (layer 1)
logic_map = {
    'gate_logic_and.png': 'logic_and.png',
    'gate_logic_or.png': 'logic_or.png',
}

# Modifier overlays (layer 2) - In 1.12.2 these were named gate_material_*
# but they're actually modifier overlays (diamond, quartz, emerald -> lapis)
modifier_map = {
    'gate_material_diamond.png': 'modifier_diamond.png',
    'gate_material_quartz.png': 'modifier_quartz.png',
    'gate_material_emerald.png': 'modifier_lapis.png',  # emerald -> lapis (closest match)
}

def convert_to_rgba(src_path, dst_path):
    """Convert a palette/indexed PNG to RGBA."""
    img = Image.open(src_path)
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    img.save(dst_path)
    return img

# Copy and convert materials
for src_name, dst_name in material_map.items():
    src_path = os.path.join(src_dir, src_name)
    dst_path = os.path.join(dst_dir, dst_name)
    convert_to_rgba(src_path, dst_path)
    print(f"Copied {src_name} -> {dst_name}")

# Copy and convert logic
for src_name, dst_name in logic_map.items():
    src_path = os.path.join(src_dir, src_name)
    dst_path = os.path.join(dst_dir, dst_name)
    convert_to_rgba(src_path, dst_path)
    print(f"Copied {src_name} -> {dst_name}")

# Copy and convert modifiers
for src_name, dst_name in modifier_map.items():
    src_path = os.path.join(src_dir, src_name)
    dst_path = os.path.join(dst_dir, dst_name)
    convert_to_rgba(src_path, dst_path)
    print(f"Copied {src_name} -> {dst_name}")

# Generate clay_brick material from iron but tinted brick-like
iron_img = Image.open(os.path.join(dst_dir, 'material_iron.png')).convert('RGBA')

def tint_image(img, r_mult, g_mult, b_mult):
    """Multiply RGB channels by the given factors, preserving alpha."""
    pixels = img.load()
    result = img.copy()
    result_px = result.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = pixels[x, y]
            result_px[x, y] = (
                min(255, int(r * r_mult)),
                min(255, int(g * g_mult)),
                min(255, int(b * b_mult)),
                a
            )
    return result

# Clay brick: warm brownish-red tint
clay_img = tint_image(iron_img, 1.1, 0.65, 0.55)
clay_img.save(os.path.join(dst_dir, 'material_clay_brick.png'))
print("Generated material_clay_brick.png (tinted from iron)")

# Nether brick: dark purplish-red tint  
nether_img = tint_image(iron_img, 0.55, 0.25, 0.30)
nether_img.save(os.path.join(dst_dir, 'material_nether_brick.png'))
print("Generated material_nether_brick.png (tinted from iron)")

print("\nDone! All gate textures are now RGBA format.")
