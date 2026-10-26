---
navigation:
  title: "Pipe Signals"
  parent: contents/logic/wire.md
  position: 27
---

# Pipe Signals

For each of the 16 pipe wire colors, you can send and recieve a unique signal to and from other gates.

The wire in its darker, unpowered state is off. When there's a signal going through the wire, it begins glowing, which means that it is on.

## As an Action

Emitting a Pipe Signal is done on the Actions side of the gate. Select the color of pipe signal you wish to send, and when the gate is triggered, it will activate, causing the associated pipe wire color to glow.

- [Pipe Wires](../item/wire.md)

## As a Trigger

There are two trigger states for each color of Pipe Signal; "Pipe Signal On" will trigger the gate when that color of Pipe Wire is glowing, and "Pipe Signal Off" will trigger the gate when that color of Pipe Wire is *not* glowing.

## Parameters

When a gate has parameter slots free, the Pipe Signal On / Off entries for every connected wire colour become available as parameters. Adding one to a **Trigger** means the gate fires only when that secondary wire matches its required state, so you can require the states of multiple wires to matched before an Action is activated.

Adding one to an **Action** is the equivalent of activating that color of pipe wire, so for a gate with 1 Action Parameter, each Action slot could activate 2 Pipe Wires.
