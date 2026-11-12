---
navigation:
  title: "FE 引擎"
  parent: contents/power/engine.md
  position: 2
  icon: buildcraftunofficial:engine_rf
item_ids:
  - buildcraftunofficial:engine_rf
---

# FE 引擎

<ItemImage id="buildcraftunofficial:engine_rf" scale="2" float="right" />

也许你手中正握着一种与你的机器不兼容的能量。这台引擎会接收它，并将其转换为 MJ。

## 信息

FE 引擎会主动从其侧面或背面任何提供能量能力的方块中抽取 FE，并在转换前最多在内部储存 **10,000 FE**。

默认情况下，它以 **4 MJ/t** 的速率通过活塞面输出。具体的 FE 与 MJ 转换比率由当前游戏配置决定。

<Row>
<RecipesFor id="buildcraftunofficial:engine_rf" fallbackText="" />
</Row>

## 升级

打开引擎的界面（潜行单击，或手中未拿扳手/管道时单击）即可使用四个升级槽位。将齿轮放入槽位中以提高输出速率：

- **铁齿轮**：+2 MJ/t
- **金齿轮**：+3 MJ/t

每个已填充的槽位独立生效；随着输出提高，引擎每刻消耗的 FE 也会增多（它只是变得更快了，并不会用同样多的 FE 产生更多的 MJ）。

## 引擎机制

BuildCraft 引擎共有 5 个温度阶段，它们决定了引擎的运行速度：蓝色、绿色、黄色、红色和黑色。

FE 引擎运行时会逐渐升温经过这些阶段，但最高止于黑色，且**不会过热**，因此永远无需冷却。

引擎总是会连接到最近的兼容 MJ 消耗设备。

你可以使用扳手旋转它，以更改它为哪个方块供能。

FE 引擎可以“串联”成一行，最多共 5 台，MJ 会依次穿过各台引擎，从而提高总输出。

与所有引擎一样，它**需要红石信号才能运行。**

门可用于检测引擎的温度阶段，帮助你控制它们。
