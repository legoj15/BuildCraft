---
navigation:
  title: "MJ 发电机"
  parent: contents/power/engine.md
  position: 3
  icon: buildcraftunofficial:mj_dynamo
item_ids:
  - buildcraftunofficial:mj_dynamo
---

# MJ 发电机

<ItemImage id="buildcraftunofficial:mj_dynamo" scale="2" float="right" />

假如你遇到的机器或装置不接受 MJ，这台“引擎”便会将能量转换成它们更熟悉的形式。

## 信息

MJ 发电机可在任意非输出面接受 MJ，并在内部缓冲区中最多储存 **1,000 MJ**，同时即时将其转换为 FE。

它会通过活塞面产生 **相当于 4 MJ/t 的 FE** 作为基础输出。确切的 FE 与 MJ 换算比率由当前游戏配置决定。

<Row>
<RecipesFor id="buildcraftunofficial:mj_dynamo" fallbackText="" />
</Row>

## 升级

与发电机交互打开它的界面，即可使用四个升级槽。将齿轮放入槽中即可提高输出速率：

- **铁齿轮**：+2 MJ/t 等值的 FE
- **金齿轮**：+3 MJ/t 等值的 FE

每个放入齿轮的槽位都会独立叠加，输出提高时，发电机每刻消耗的 MJ 也会随之增多（它只是变快了；并不会用同样数量的 MJ 产生更多 FE）。

## 引擎机制

BuildCraft 引擎共有 5 个温度阶段，这些阶段决定了引擎的运行速度：蓝色、绿色、黄色、红色和黑色。

MJ 发电机运行时会逐渐升温并经历这些阶段，但到黑色为止，**不会过热**，因此永远无需冷却。

发电机始终会向活塞面上的接收端推送 FE。

你可以使用扳手旋转发电机，以改变它供能的目标方块。

MJ 发电机可以“串联”成一行，总共最多 4 台，逐台累加以提高吞吐量。

与所有引擎一样，它**需要红石信号才能运行。**

你可以使用门来检测发电机的温度阶段，帮助你控制它们。
