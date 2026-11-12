---
navigation:
  title: "程控台"
  parent: contents/robots/robot.md
  position: 14
  icon: buildcraftunofficial:programming_table
item_ids:
  - buildcraftunofficial:programming_table
---

# 程控台

<ItemImage id="buildcraftunofficial:programming_table" scale="2" float="right" />

一台分工不同的激光工作台：它不组装芯片，而是把构想写入红石电路板。从网格中挑选一份工作，为它供能，一块知道自己该做什么的电路板便应运而生。

## 使用方法

把一块空白的[红石电路板](../item/redstone_board.md)放入输入槽位，从电路板网格中挑选一份工作，再将一台已供能的[激光](./laser.md)对准该台。进度条会随着激光输送的能量而填充；当它达到电路板的成本时，输入物品被消耗，写好的电路板落入输出槽位。如果输出槽位被占用，程控台就会等待。写好的电路板可以放回去重写为另一份工作，但需全额支付新工作的成本。

视工作不同，电路板的成本为 800、3,200、12,800 或 51,200 MJ；因此，一档用于芯片绰绰有余的激光，在写建造者板时要花上不少时间。“电路板”章节下的每个电路板页面都标明了各自的成本。

<Row>
<RecipesFor id="buildcraftunofficial:programming_table" fallbackText="" />
</Row>

## 另见

- [集成台](./integration_table.md)
- [机器人入门](../concept/robots.md)
