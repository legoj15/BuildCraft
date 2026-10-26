---
navigation:
  title: "Wooden Diamond Transport Pipe"
  parent: contents/transport/pipe_item.md
  position: 47
  icon: buildcraftunofficial:pipe_diamond_wood_item
item_ids:
  - buildcraftunofficial:pipe_diamond_wood_item
---

# Wooden Diamond Transport Pipe

<ItemImage id="buildcraftunofficial:pipe_diamond_wood_item" scale="2" float="right" />

Want to take certain items out and transport them somewhere else? Just give it power and watch them flow.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_diamond_wood_item" fallbackText="" />
</Row>

## Pipe Mechanics

When powered, It will extract the items selected in the filter and transport them to any other connected inventories/pipes.

The extraction of items will occur out of solid side of the pipe and transport them towards the clear facing sides.

If it has multiple clear facing sides, the items will move in a random direction.

If there is no where for the item to go, the extracted items will drop out of the pipe onto the ground.

Some machines can automatically output items directly into a Wooden Diamond Transport Pipe without the pipe needing to be powered (This will not respect the filter).

Using Pipe Plugs or painting pipes a different colour can stop pipes connecting.

## Filtering

Opening up the Wooden Diamond Transport Pipe GUI will allow you to setup a filter.

Placing items you want in the slots will allow you to filter what it extracts.

You can use the Allowlist, Blocklist or Round Robin options to change what your filter does:

Allowlist: Only the items selected will be extracted.

Blocklist: Any items other than the items selected will be extracted.

Round robin: Extracts the items in the exact order selected in the above filter slots (from left to right)

A List can be used in a filtering slots to give you expanded filtering options.

- [List](../item/list.md)

## Powering

It can receive power from any MJ power source such as: an engine, a power adaptor, or a pipe pulsar.

- [Redstone Engine](../block/engine_redstone.md)
- [Pipe Power Adaptor](../item/plug_power_adaptor.md)
- [Pipe Pulsar](../item/plug_pulsar.md)

For each pulse of power it receives, it will extract items from the adjacent inventory.

The more power a pulse has, the more items will be extracted per pulse.

**Used to craft:** <ItemLink id="buildcraftunofficial:pipe_diamond_wood_fluid" />, <ItemLink id="buildcraftunofficial:pipe_diamond_wood_power" />, <ItemLink id="buildcraftunofficial:pipe_emzuli_item" />
