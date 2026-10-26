---
navigation:
  title: "Power Requested"
  parent: contents/power/power_delivery.md
  position: 17
---

# Power Requested

There's no point pushing power into a pipe nobody is drinking from.

## Requirements

When selected, the connected actions will activate while at least one downstream device on the pipe network is asking for power. Use it to gate engines so they only run when something is actually consuming, sparing fuel and avoiding wasted overflow.

## Pipe Compatibility

The trigger reads the live power-request value from the pipe's flow handler. It works on both MJ kinesis pipes and FE pipes — the flow handler reports requested-but-undelivered power for whichever flavour the pipe carries.
