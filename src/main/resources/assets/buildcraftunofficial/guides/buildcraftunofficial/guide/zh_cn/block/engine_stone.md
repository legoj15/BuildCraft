---
navigation:
  title: "斯特林引擎"
  parent: contents/power/engine.md
  position: 10
  icon: buildcraftunofficial:engine_stone
item_ids:
  - buildcraftunofficial:engine_stone
---

# 斯特林引擎

<ItemImage id="buildcraftunofficial:engine_stone" scale="2" float="right" />

在用基础的红石引擎做过各种尝试后，你发现了一种燃烧熔炉燃料的方法，能够以 1 MJ/t 的速率真正产出可观的能量。

## 信息

就产能而言，斯特林引擎与普通熔炉的效率相同。

<Row>
<RecipesFor id="buildcraftunofficial:engine_stone" fallbackText="" />
</Row>

## 引擎机制

BuildCraft 的引擎有 5 个温度阶段，它们决定了引擎的运行速度：蓝色、绿色、黄色、红色和黑色。

只有当引擎内部的能量缓冲区被填满、产生的能量无处可去时，斯特林引擎才会变黑；此时引擎会过热，需要用扳手敲打才能冷却。

引擎总会连接到最近的兼容 MJ 消耗设备。

你可以用扳手旋转它，来改变它为哪个方块供能。

斯特林引擎可以“串联”成一行，一行总共最多 3 台引擎。

与所有引擎一样，它**需要红石信号才能运行。**

可以使用门来检测引擎的温度阶段，帮助你控制它们。
