---
navigation:
  title: "Advanced Crafting Table"
  parent: contents/silicon/crafting.md
  position: 8
  icon: buildcraftunofficial:advanced_crafting_table
item_ids:
  - buildcraftunofficial:advanced_crafting_table
---

# Advanced Crafting Table

<ItemImage id="buildcraftunofficial:advanced_crafting_table" scale="2" float="right" />

A single [Auto Workbench](./autoworkbench_item.md) is fine when you only want one recipe at a time. By the time you've stamped out a Redstone Chipset, though, you've already built Lasers and seen what they can do. A crafting table that runs straight off them is a natural next step.

## Information

Define the desired crafting recipe by placing the ingredients in the crafting grid or by selecting a recipe from the crafting book.

The 5×3 buffer on the left is the materials supply. Ingredients can be piped in from any side, and the output can be extracted from any side as well.

Any recipe usable in a crafting table will work.

When a grid could make more than one thing, the button above the result slot cycles through the options. It also offers the same recipe in other materials: with oak planks laid out as stairs it steps through every wood's stairs, and picking one rewrites the blueprint grid to that wood, so the grid always shows what is being crafted. The item tags that count as interchangeable are listed in the craftingVariantTags config option.

The button beside it makes the table accept substitute materials. Normally the buffer must hold the blueprint's exact items. With the toggle on, the table may craft the selected recipe from any other material that still fits it and makes exactly the item shown, such as birch planks for an oak planks blueprint. It uses one material for every slot of a given blueprint item (never a mix of woods) and prefers the blueprint's own items when the buffer has them.

<Row>
<RecipesFor id="buildcraftunofficial:advanced_crafting_table" fallbackText="" />
</Row>

## Power

Each craft costs an equivalent 500 MJ in laser power. The more Lasers you point at it, the faster it accumulates power and the faster it crafts.

The table only asks for laser power once it has a valid recipe in the blueprint grid; an empty table is invisible to Lasers, so they go looking for other work. Any power it has already built up is kept across recipe changes, so swapping blueprints or pausing to wait for materials doesn't waste MJ.
