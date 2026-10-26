---
navigation:
  title: "Requester"
  parent: contents/robots/station.md
  position: 44
  icon: buildcraftunofficial:requester
item_ids:
  - buildcraftunofficial:requester
---

# Requester

<ItemImage id="buildcraftunofficial:requester" scale="2" float="right" />

An order form in block form. Write down what you want in the top half, and Delivery robots keep the bottom half stocked.

## Using it

Place the Requester next to a pipe that carries a [Docking Station](../item/robot_station.md); the station finds it on any side. Click an item into one of the twenty request slots in the upper half, holding as many as you want delivered, and that becomes a standing order. A Delivery robot takes the order, loads the item at a station whose gate has Provide Items active, flies back and puts it into the delivery slot below the request. Deliveries can be pulled out by hand or by any pipe; a slot accepts only the item requested above it, so nothing else can be hoppered in.

Each order is served by one robot at a time, and a robot that cannot find the item gives up on it until its next search.

<Row>
<RecipesFor id="buildcraftunofficial:requester" fallbackText="" />
</Row>

## See also

- [Getting Started with Robots](../concept/robots.md)
