"""Generate 16 dye-colour variants of each fluid pipe texture.

For each source texture, replaces waterproofing pixels with the BuildCraft
dye colour. Uses mask_shared_fluid.png as a positional guide — only pixels
at positions where the mask is opaque are eligible for replacement. This
prevents recolouring filter direction indicators on diamond pipes.

Output: buildcraft-transport/src/main/resources/assets/buildcrafttransport/
        textures/pipes/<basename>_dyed_<colour>.png
"""

from PIL import Image
import os

# BuildCraft LIGHT_HEX colours (from 1.12.2 ColourUtil.java), keyed by MC DyeColor name
DYE_COLOURS = {
    "white":      (0xe4, 0xe4, 0xe4),
    "orange":     (0xEA, 0x78, 0x35),
    "magenta":    (0xD9, 0x43, 0xC6),
    "light_blue": (0x66, 0xAA, 0xFF),
    "yellow":     (0xFF, 0xD9, 0x1C),
    "lime":       (0x39, 0xD5, 0x2E),
    "pink":       (0xD9, 0x71, 0x99),
    "gray":       (0x7A, 0x7A, 0x7A),
    "light_gray": (0xa0, 0xa7, 0xa7),
    "cyan":       (0x29, 0x97, 0x99),
    "purple":     (0x7e, 0x34, 0xbf),
    "blue":       (0x25, 0x31, 0x93),
    "brown":      (0x89, 0x50, 0x2D),
    "green":      (0x00, 0x7F, 0x0E),
    "red":        (0xBE, 0x2B, 0x27),
    "black":      (0x18, 0x14, 0x14),
}

# Fluid pipe texture basenames to process
FLUID_TEXTURES = [
    "andesite_fluid", "clay_fluid", "cobblestone_fluid",
    "diamond_fluid", "diamond_fluid_down", "diamond_fluid_east",
    "diamond_fluid_itemstack",
    "diamond_fluid_north", "diamond_fluid_south", "diamond_fluid_up",
    "diamond_fluid_west", "diamond_fluid_west_cb",
    "diamond_wood_fluid_clear", "diamond_wood_fluid_filled",
    "diorite_fluid", "gold_fluid", "granite_fluid",
    "iron_fluid_clear", "iron_fluid_filled",
    "quartz_fluid", "sandstone_fluid", "stone_fluid",
    "void_fluid", "wood_fluid_clear", "wood_fluid_filled",
]

# Tolerance for matching the waterproofing green (#24451b / #24431b)
TOLERANCE = 3


def is_waterproofing_green(r, g, b):
    """Check if a pixel matches the waterproofing green within tolerance."""
    return (abs(r - 0x24) <= TOLERANCE and
            abs(g - 0x45) <= TOLERANCE and
            abs(b - 0x1b) <= TOLERANCE)


def load_mask(tex_dir):
    """Load mask_shared_fluid.png and return a set of (x,y) positions where alpha > 0."""
    mask_path = os.path.join(tex_dir, "mask_shared_fluid.png")
    mask_img = Image.open(mask_path).convert("RGBA")
    mask_pixels = mask_img.load()
    w, h = mask_img.size
    paintable = set()
    for y in range(h):
        for x in range(w):
            if mask_pixels[x, y][3] > 0:
                paintable.add((x, y))
    return paintable


def generate_dyed_variant(src_path, dst_path, dye_rgb, paintable_positions):
    """Replace waterproofing green pixels with the dye colour,
    but ONLY at positions marked as paintable by the mask."""
    img = Image.open(src_path).convert("RGBA")
    pixels = img.load()
    w, h = img.size
    replaced = 0
    for y in range(h):
        for x in range(w):
            # Only replace at mask-allowed positions
            if (x, y) not in paintable_positions:
                continue
            r, g, b, a = pixels[x, y]
            if a > 0 and is_waterproofing_green(r, g, b):
                pixels[x, y] = (dye_rgb[0], dye_rgb[1], dye_rgb[2], a)
                replaced += 1
    img.save(dst_path)
    return replaced


def main():
    tex_dir = os.path.join(
        "buildcraft-transport", "src", "main", "resources",
        "assets", "buildcrafttransport", "textures", "pipes"
    )

    paintable = load_mask(tex_dir)
    print(f"Mask has {len(paintable)} paintable positions")

    total_files = 0
    total_pixels = 0

    for basename in FLUID_TEXTURES:
        src_path = os.path.join(tex_dir, f"{basename}.png")
        if not os.path.exists(src_path):
            print(f"WARNING: {src_path} not found, skipping")
            continue

        for dye_name, dye_rgb in DYE_COLOURS.items():
            dst_name = f"{basename}_dyed_{dye_name}.png"
            dst_path = os.path.join(tex_dir, dst_name)
            replaced = generate_dyed_variant(src_path, dst_path, dye_rgb, paintable)
            total_files += 1
            total_pixels += replaced

    print(f"\nGenerated {total_files} dyed textures ({total_pixels} pixels replaced)")


if __name__ == "__main__":
    main()
