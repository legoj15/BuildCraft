---
navigation:
  title: "Wooden Transport Pipe"
  parent: contents/transport/pipe_item.md
  position: 37
  icon: buildcraftunofficial:pipe_wood_item
item_ids:
  - buildcraftunofficial:pipe_wood_item
---

# Wooden Transport Pipe

<ItemImage id="buildcraftunofficial:pipe_wood_item" scale="2" float="right" />

Want to take items out and transport them somewhere else? Just give it power and watch it them flow.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_wood_item" fallbackText="" />
</Row>

## Pipe Mechanics

When powered, It will extract and transport the items to any other connecting inventories/pipes.

The extraction of items will occur out of solid side of the pipe and transport them towards the clear facing sides.

If it has multiple clear facing sides, the items will move randomly between them.

If there is no where for the items to go, the extracted items will drop out of the pipe onto the ground.

Some machines can automatically output items directly into a Wooden Transport Pipe without the pipe needing to be powered.

Using Pipe Plugs or painting pipes a different colour can stop pipes connecting.

## Powering

It can receive power from any MJ power source such as: an engine, a power adaptor, or a pipe pulsar.

- [Redstone Engine](../block/engine_redstone.md)
- [Pipe Power Adaptor](../item/plug_power_adaptor.md)
- [Pipe Pulsar](../item/plug_pulsar.md)

For each pulse of power it receives, it will extract items from the adjacent inventory.

The more power a pulse has, the more items will be extracted per pulse.

**Used to craft:** <ItemLink id="buildcraftunofficial:pipe_wood_fluid" />, <ItemLink id="buildcraftunofficial:pipe_wood_power" />
