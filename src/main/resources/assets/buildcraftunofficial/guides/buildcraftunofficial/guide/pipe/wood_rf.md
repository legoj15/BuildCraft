---
navigation:
  title: "Wooden FE Pipe"
  parent: contents/power/pipe_rf.md
  position: 30
  icon: buildcraftunofficial:pipe_wood_rf
item_ids:
  - buildcraftunofficial:pipe_wood_rf
---

# Wooden FE Pipe

<ItemImage id="buildcraftunofficial:pipe_wood_rf" scale="2" float="right" />

Every Forge Energy grid needs a way in. Sit one against a running dynamo and let the energy pour through.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_wood_rf" fallbackText="" />
</Row>

## Pipe Mechanics

The Wooden FE Pipe is how Forge Energy enters a network. An MJ Dynamo and other FE sources connect to it; it pulls their output in and passes it along to the plain FE Pipes attached to its other sides, which carry it on to the machines that consume it.

It moves up to 160 FE/t by default. Like the Wooden Transport Pipe, it will not connect to another wooden FE pipe.

## Powering

An MJ Dynamo, or any Forge Energy generator from another mod, can feed a Wooden FE Pipe.

- [MJ Dynamo](../block/mj_dynamo.md)

The network then delivers that energy to any FE-consuming machine, such as the FE Engine, which converts it back into MJ.

- [FE Engine](../block/engine_rf.md)

**Used to craft:** <ItemLink id="buildcraftunofficial:pipe_wood_power" />
