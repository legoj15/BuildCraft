---
navigation:
  title: "木质 FE 管道"
  parent: contents/power/pipe_rf.md
  position: 30
  icon: buildcraftunofficial:pipe_wood_rf
item_ids:
  - buildcraftunofficial:pipe_wood_rf
---

# 木质 FE 管道

<ItemImage id="buildcraftunofficial:pipe_wood_rf" scale="2" float="right" />

每一座 Forge 能量电网都需要一个入口。把它贴靠在一台运转中的发电机上，任能量倾泻而过。

<Row>
<RecipesFor id="buildcraftunofficial:pipe_wood_rf" fallbackText="" />
</Row>

## 管道机制

木质 FE 管道是 Forge 能量进入管网的方式。MJ 发电机和其他 FE 能量源与它相连接；它把这些能量源的输出抽进来，再传给接在它另外几侧的普通 FE 管道，由它们继续把能量送往消耗能量的机器。

它默认最多可传输 160 FE/t。与木质运输管道一样，它不会与另一根木质 FE 管道连接。

## 供能

MJ 发电机，或来自其他模组的任何 Forge 能量发电机，都可以为木质 FE 管道供给能量。

- [MJ 发电机](../block/mj_dynamo.md)

随后，管网会把能量输送到任何消耗 FE 的机器，例如 FE 引擎——它会把能量转换回 MJ。

- [FE 引擎](../block/engine_rf.md)

**用于合成:** <ItemLink id="buildcraftunofficial:pipe_wood_power" />
