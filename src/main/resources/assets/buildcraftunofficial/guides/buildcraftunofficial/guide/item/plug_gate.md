---
navigation:
  title: "Logic Gates"
  parent: contents/logic/gate.md
  position: 3
  icon: buildcraftunofficial:plug_gate
item_ids:
  - buildcraftunofficial:plug_gate
---

# Logic Gates

<ItemImage id="buildcraftunofficial:plug_gate" scale="2" float="right" />

Redstone takes up a lot of space nowadays. Why not have finer control over your system in a more compact space using pipes; just place a Gate on a pipe and start thinking.

<Row>
<Recipe id="buildcraftunofficial:gate_basic" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_iron_and" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_nether_brick_and" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_iron_and_lapis" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_iron_and_quartz" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_swap_gold_and_diamond_to_or" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_swap_gold_or_diamond_to_and" fallbackText="" />
</Row>

*Two of these recipes each stand for 12 of their kind.*

## Gate Placement

A Gate is a pluggable so it can only be placed on pipes and can be placed on any side of a pipe.

Any pipe placed next to it on that side will not connect.

## Gate Tiers

Gates come in different tiers that provide different amounts of slots:

Basic Gate — Provides 1 set of slots (this cannot be modified).

Iron Gate — Provides 2 sets of slots.

Nether Brick Gate — Provides 4 sets of slots.

Gold Gate — Provides 8 sets of slots.

## Gate Modifiers

Gates can be modified to provide parameter slots:

Lapis Expansion — Provides 1 parameter slot to each trigger.

Quartz Expansion — Provides 1 parameter slot to each set (will cut the amount of slots the gate has by half).

Diamond Expansion — Provides 3 parameter slots to each set (will cut the amount of slots the gate has by half).

## Gate Variants

Using an AND Gate ensures that **all** connected Triggers are active before the connection to the actions is made active.

Using an OR Gate ensures that **one or more** connected Triggers are active before the connection to the actions is made active.

## Triggers

The icons on the left-hand side are known as Triggers. These are dragged into the slots on the left side of the GUI.

Different Triggers are used to detect different things, each with a different criteria. When a Trigger's criteria is met, the adjacent connection will become active.

## Actions

The icons on the right-hand side are known as Actions. These are dragged into the slots on the right side of the GUI.

Different Actions result in different tasks being completed. When the adjacent connection is active, the connected Actions are undertaken.

## Parameters

When Parameter slots are installed, extra slots next to some triggers/actions become available.

These extra slots can provide extra functionality to make your triggers/actions more specific.

In these extra slots you either: click the slot to access more options or place items in as a filter.

## Connections

The lines down the middle are used to connect the Triggers to the Actions, so they can work together.

They can be clicked to be connected/disconnected to require multiple triggers/actions.

When a trigger is met, the line in the middle will light up (only partially if there are multiple triggers required).

When a line is lit up, then the actions they are connected to will be undertaken.
