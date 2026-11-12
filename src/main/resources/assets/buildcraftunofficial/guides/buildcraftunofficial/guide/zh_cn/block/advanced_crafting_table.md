---
navigation:
  title: "高级工作台"
  parent: contents/silicon/crafting.md
  position: 4
  icon: buildcraftunofficial:advanced_crafting_table
item_ids:
  - buildcraftunofficial:advanced_crafting_table
---

# 高级工作台

<ItemImage id="buildcraftunofficial:advanced_crafting_table" scale="2" float="right" />

如果你一次只想运行一种配方，一台[自动合成台](./autoworkbench_item.md)便已足够。不过，等你做出一块红石芯片时，你早已造好激光并见识过它们的能耐。一台直接由激光驱动的工作台，正是顺理成章的下一步。

## 信息

将材料放入合成网格，或从配方书中选择一个配方，即可定义想要的合成配方。

左侧的 5×3 缓冲区是材料供应区。材料可以从任意一面用管道输入，成品也可以从任意一面取出。

任何能在工作台中使用的配方都可行。

当网格可以合成不止一种物品时，结果槽上方的按钮会在各个选项之间循环切换。它还会提供相同配方的其他材料版本：以橡木木板摆成楼梯为例，它会逐一列出每种木头的楼梯，选中其一便会将蓝图网格改写为那种木头，因此网格始终显示当前正在合成的内容。被视为可互换的物品标签列于 craftingVariantTags 配置选项中。

它旁边的按钮可让工作台接受替代材料。通常情况下，缓冲区必须存放与蓝图完全一致的物品。开启该开关后，工作台可以用任何仍然符合所选配方的其他材料进行合成，且产出的正是所显示的物品，例如用白桦木板执行橡木木板蓝图。对于蓝图中的同一种物品，它的每个槽位只会使用一种材料（绝不会混用不同的木头），并且当缓冲区中备有蓝图原物品时会优先使用。

<Row>
<RecipesFor id="buildcraftunofficial:advanced_crafting_table" fallbackText="" />
</Row>

## 能量

每次合成相当于消耗 500 MJ 的激光能量。你将越多的激光对准它，它积累能量的速度就越快，合成速度也就越快。

只有当蓝图网格中存在有效配方时，工作台才会请求激光能量；空的工作台对激光是不可见的，因此激光会去寻找其他工作。它已积累的能量会在配方更换时保留，因此更换蓝图或暂停等待材料都不会浪费 MJ。
