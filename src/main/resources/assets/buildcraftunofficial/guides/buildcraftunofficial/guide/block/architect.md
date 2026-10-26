---
navigation:
  title: "Architect Table"
  parent: contents/building/builders.md
  position: 2
  icon: buildcraftunofficial:architect
item_ids:
  - buildcraftunofficial:architect
---

# Architect Table

<ItemImage id="buildcraftunofficial:architect" scale="2" float="right" />

Instead of manually collecting the shape and intricacies of a structure by hand, this block can capture all the data within a marked volume, and when given to a [Builder](./builder.md), you can either rebuild it 1:1, or use just the shape of the structure.

## Information

Place the Architect at the corner of a volume defined with [Land Mark](./marker_volume.md)s to register that as the starting corner to capture. Breaking or removing the Architect Table will remove the volume, and it will need to be re-marked with Land Markers

Place a [Blueprint](../item/blueprint_clean.md) or [Template](../item/template_clean.md) into the input slot. Type a name into the field below the slot to differentiate between other snapshots whilst in your inventory or a [Electronic Library](./library.md). The 3D preview at the top of the GUI rotates a live model of the marked area's current contents so you can see what will be captured.

Scanning starts as soon as any blueprint or template is inserted. Scanning runs over the whole volume, and ejects a filled (used) snapshot into the output slot. Scans cost no power, and you can use already used blueprint and templates again to overwrite them.

<Row>
<RecipesFor id="buildcraftunofficial:architect" fallbackText="" />
</Row>

**Used to craft:** <ItemLink id="buildcraftunofficial:replacer" />

## Blueprint vs Template

**Blueprints** record the full block state of every position plus any entities in the volume. This will capture the rotation, state, and inventory of all blocks. The Builder reconstructs them with the same materials they were captured with.

**Templates** record only whether each position is occupied or empty. The Builder fills every captured position with whichever single block you stock it with; useful for repetitive reusable structures like columns or walls. On a technical note, templates scan three times faster than blueprints and serialize into a much smaller file.
