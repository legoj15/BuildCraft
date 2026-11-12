---
navigation:
  title: "建造者板"
  parent: contents/robots/board.md
  position: 34
  icon: buildcraftunofficial:redstone_board
---

# 建造者板

工地监工的跑腿伙计：它让建筑机的料箱始终满满当当，工程绝不会因为缺少一块方块而停摆。

## 行为

机器人会在自己区域内约 110 格的范围里，寻找缺少材料的最近一台[建筑机](../block/builder.md)。它会从门的“提供物品”动作处于激活状态的对接站处，每次取回一组所缺的物品，然后飞到机器旁将其放入。当建筑机集齐了所有物料，或其储存已满时，机器人便会休眠。它只为蓝图效力；使用模板工作的建筑机不需要任何投递。为其编程需要 51,200 MJ，是最昂贵的一块板。

## 另见

- [建筑机](../block/builder.md)
- [机器人入门](../concept/robots.md)
