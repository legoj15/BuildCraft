---
navigation:
  title: "集成台"
  parent: contents/silicon/crafting.md
  position: 3
  icon: buildcraftunofficial:integration_table
item_ids:
  - buildcraftunofficial:integration_table
---

# 集成台

<ItemImage id="buildcraftunofficial:integration_table" scale="2" float="right" />

第三台激光工作台。装配台制造零件，程控台编写电路板；而这台负责把东西组合到一起：把一块写好的电路板装进机器人。

## 使用方法

把要改变的物品放进中央槽位，把要与它组合的零件放进周围的网格，然后将一台已供能的[激光](./laser.md)对准该台。台上会显示它将制作的产物；当激光输送完所需的能量后，输入物品便被消耗，产物出现在输出槽位中。如果没有显示任何内容，说明这一组合并非配方：网格中必须恰好放置配方所要求的零件，多一件也不行。

## 为机器人编程

将一台未编程的[机器人](../item/robot.md)放在中央，在旁边放入一块写好的[红石电路板](../item/redstone_board.md)，再消耗 5,000 MJ。机器人会保留它原有的电量，而一台完全耗尽电量的机器人会被补足至 2,000 MJ，以便它能立刻飞往对接站充电。

<Row>
<RecipesFor id="buildcraftunofficial:integration_table" fallbackText="" />
</Row>

## 另见

- [程控台](./programming_table.md)
- [装配台](./assembly_table.md)
- [机器人入门](../concept/robots.md)
