---
navigation:
  title: "Stripes Transport Pipe"
  parent: contents/transport/pipe_item.md
  position: 52
  icon: buildcraftunofficial:pipe_stripes_item
item_ids:
  - buildcraftunofficial:pipe_stripes_item
---

# Stripes Transport Pipe

<ItemImage id="buildcraftunofficial:pipe_stripes_item" scale="2" float="right" />

Imagine a pipe which can place blocks, break blocks and use blocks. This is that pipe your imagining.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_stripes_item" fallbackText="" />
</Row>

## Pipe Operation

When powered, It will break the block directly in front of it and pass it on to the adjacent pipe or inventory.

When a block is fed through it, It will place the block directly in front of it.

When an item is fed through it, It will attempt to 'use' it on the block/entity in front of it.

## Pipe Direction

An Striped Transport Pipe has to be 'pointing' in a direction to effect the block in front of it.

With multiple pipes/inventories connected to it, It will lose its ability affect from the world.

## Pipe Mechanics

If there is no where for the travelling items to go, they will be dropped out of the pipe onto the ground.

Using Pipe Plugs or painting pipes a different colour can stop pipes connecting.

## Powering

It can receive power from any MJ power source such as: an engine, a power adaptor, or a pipe pulsar.

- [Redstone Engine](../block/engine_redstone.md)
- [Pipe Power Adaptor](../item/plug_power_adaptor.md)
- [Pipe Pulsar](../item/plug_pulsar.md)

For each pulse of power it receives, it will use the power it received and attempt to break the block in front of it.

The more power a pulse has, the more power will be used to break the block.
