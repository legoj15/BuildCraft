---
navigation:
  title: "Set Power Limit"
  parent: contents/power/power_delivery.md
  position: 18
---

# Set Power Limit

The Iron and Diamond power-pipe variants throttle the energy passing through them. A connected gate can step the throttle up or down at will, swapping the pipe's transfer cap to a different rate.

## Behaviour

The MJ kinesis pipes ([Iron Kinesis Pipe](../pipe/iron_power.md) and [Diamond Kinesis Pipe](../pipe/diamond_power.md)) expose seven MJ/t actions, from the pipe's full transfer rate down to 0 MJ/t. The FE/RF pipes ([Iron FE Pipe](../pipe/iron_rf.md) and [Diamond FE Pipe](../pipe/diamond_rf.md)) expose the same seven steps in RF/t. Selecting the 0-rate entry blocks all power transfer through the pipe entirely.

- [Iron Kinesis Pipe](../pipe/iron_power.md)
- [Diamond Kinesis Pipe](../pipe/diamond_power.md)

Available when FE compatibility is enabled:

- [Iron FE Pipe](../pipe/iron_rf.md)
- [Diamond FE Pipe](../pipe/diamond_rf.md)

The actual values shown on each entry depend on the pipe. Each pipe's gate only offers the seven actions matching its own transfer table.
