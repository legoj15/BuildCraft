---
navigation:
  title: "搬运者板"
  parent: contents/robots/board.md
  position: 37
  icon: buildcraftunofficial:redstone_board
---

# 搬运者板

一个会飞的漏斗。在这边装货，到那边卸货，永远循环往复。

## 行为

货舱为空时，机器人会寻找一个门上启用了“提供物品”的对接站，装载该站的木质运输管道能从其后方容器中抽出的任何物品；载有货物时，它会寻找一个启用了“接受物品”的对接站，并把货物卸入该管道。母站门上的“过滤”动作会限制它愿意搬运的物品。当两种对接站都找不到时，它便会休眠。为它编程需要 800 MJ。

## 另见

- [储罐板](./tank.md)
- [机器人入门](../concept/robots.md)
