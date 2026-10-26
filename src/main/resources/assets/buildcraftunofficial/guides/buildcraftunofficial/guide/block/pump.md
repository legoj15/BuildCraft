---
navigation:
  title: "Pump"
  parent: contents/mining_fluids/fluid.md
  position: 6
  icon: buildcraftunofficial:pump
item_ids:
  - buildcraftunofficial:pump
---

# Pump

<ItemImage id="buildcraftunofficial:pump" scale="2" float="right" />

The collection of fluids by hand can be quite tedious and dangerous at times, creating the need for an automated way to gather fluids.

To gather fluids from the world, place a pump above a source block of the fluid you want to pump up and supply it with power.

The fluids sources found below will be 'sucked' up from the top source downwards and will be auto-outputted into adjacent pipes/tanks.

It will pump fluids in a 64 blocks radius from top to bottom and the outer edges of the connecting fluids inwards.

## Machine Mechanics

As with most machines, it requires power. The more power you supply it, the faster it will pump.

Applying a redstone signal to the pump will stop the pump from pumping so be careful where you place your levers.

## On a Mining Well or Quarry

Placed on top of a Mining Well or a Quarry, the pump stops pumping below itself and instead drains the lava, oil or other thick liquid in the machine's way, spending its own stored power first and then the machine's once the machine has nothing else to dig (only a machine with the same owner shares its power). A Quarry goes back for the blocks the liquid was covering once it is drained. Its status light turns blue while it sits there: bright while it has liquid lined up to drain, dim while it has none. It cannot clear a solid block such as obsidian. Pipe the pumped liquid away, or the pump stops once its tank is half full.

<Row>
<RecipesFor id="buildcraftunofficial:pump" fallbackText="" />
</Row>

**Used to craft:** <ItemLink id="buildcraftunofficial:builder" />
