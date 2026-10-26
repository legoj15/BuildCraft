---
navigation:
  title: "Diamond Transport Pipe"
  parent: contents/transport/pipe_item.md
  position: 46
  icon: buildcraftunofficial:pipe_diamond_item
item_ids:
  - buildcraftunofficial:pipe_diamond_item
---

# Diamond Transport Pipe

<ItemImage id="buildcraftunofficial:pipe_diamond_item" scale="2" float="right" />

Choosing which items can travel where might be useful for your system.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_diamond_item" fallbackText="" />
</Row>

## Setting the Filter

The Diamond Transport Pipe has each of its sides distinguished by a different colour.

Opening up the Diamond Transport Pipe GUI will allow you to setup a filter.

Placing a items you want in a certain slots colour will allow you to filter which items can go in which direction.

Having multiple of the same items in a colour will allow you to add 'weight' to where you fluids will go.

A List can be used in a filtering slots to give you expanded filtering options.

- [List](../item/list.md)

## Pipe Mechanics

In case of their being multiple directions items could go, the items will be distributed (depending of the weight you give them) between them.

Any valid direction with no filter set will allow any item to pass through but filtered directions will take priority.

Items will not travel back in the direction it came from and only in a different direction.

If there is no connecting pipe or the adjacent inventory is full and there is no where else for the item to go, the item will drop out of the pipe onto the ground.

Some machines can automatically output items directly into a Diamond Transport Pipe without needing to be fed through an extraction pipe.

Using Pipe Plugs or painting pipes a different colour can stop pipes connecting.

**Used to craft:** <ItemLink id="buildcraftunofficial:filtered_buffer" />, <ItemLink id="buildcraftunofficial:pipe_diamond_fluid" />, <ItemLink id="buildcraftunofficial:pipe_diamond_power" />
