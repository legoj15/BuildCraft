---
navigation:
  title: "Wooden Diamond Fluid Pipe"
  parent: contents/transport/pipe_fluid.md
  position: 13
  icon: buildcraftunofficial:pipe_diamond_wood_fluid
item_ids:
  - buildcraftunofficial:pipe_diamond_wood_fluid
---

# Wooden Diamond Fluid Pipe

<ItemImage id="buildcraftunofficial:pipe_diamond_wood_fluid" scale="2" float="right" />

Want to take a certain fluid out and transport it somewhere else? Just give it power and watch it flow.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_diamond_wood_fluid" fallbackText="" />
</Row>

## Pipe Mechanics

When powered, It will extract the fluid selected by the filter into any other connected tanks/pipes.

The extraction of fluids will occur out of solid side of the pipe towards the clear facing sides.

If it has multiple clear facing sides, the the fluids will evenly split in each direction.

Some machines can output fluids directly into it without needing power (This will not respect the filter).

Using Pipe Plugs or painting pipes a different colour can stop pipes connecting.

## Filtering

Opening up the Wooden Diamond Fluid Pipe GUI will allow you to setup a filter.

Placing a bucket (or other fluid container) of the fluid you want in the slots will allow you to filter what it extracts.

You can use the Allowlist and Blocklist options to change what your filter does:

Allowlist: Only the fluids selected will be extracted.

Blocklist: Any fluid other than the fluid selected will be extracted.

## Powering

It can receive power from any MJ power source such as: an engine, a power adaptor, or a pipe pulsar.

- [Redstone Engine](../block/engine_redstone.md)
- [Pipe Power Adaptor](../item/plug_power_adaptor.md)
- [Pipe Pulsar](../item/plug_pulsar.md)

For each pulse of  power it receives, it will extract from the adjacent tank.

The more power a pulse has, the more fluids will be extracted per pulse.

The Wooden Diamond Fluid pipe transports fluids to adjacent pipes and tanks at 1.6 buckets per second.

Fluid pipes have an internal fluid tank which can hold 1 bucket per side and 1 Bucket in the centre.

**Used to craft:** <ItemLink id="buildcraftunofficial:pipe_diamond_wood_item" />
