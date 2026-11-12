---
navigation:
  title: "木质动能管道"
  parent: contents/power/pipe_power.md
  position: 20
  icon: buildcraftunofficial:pipe_wood_power
item_ids:
  - buildcraftunofficial:pipe_wood_power
---

# 木质动能管道

<ItemImage id="buildcraftunofficial:pipe_wood_power" scale="2" float="right" />

每个能量网络都需要一个入口。把它抵在运转中的引擎上，让能量倾泻而过。

<Row>
<RecipesFor id="buildcraftunofficial:pipe_wood_power" fallbackText="" />
</Row>

## 管道机制

木质动能管道是能量进入管网的方式。引擎和其他 MJ 能量源与它相连；它将这些能量源的输出抽入，再传给其余各面连接的普通动能管道，由它们把能量继续送往消耗能量的机器。

它默认最高可传输 16 MJ/t，途中损耗极小。与木质运输管道一样，它不会与另一条木质动能管道相连。

## 供能

除红石引擎外的任何引擎都可以为木质动能管道供能。

- [斯特林引擎](../block/engine_stone.md)
- [燃油引擎](../block/engine_iron.md)

管道能量适配器可以让输送的能量在末端跨入可接收能量的运输或流体管道。

- [管道能量适配器](../item/plug_power_adaptor.md)

**用于合成:** <ItemLink id="buildcraftunofficial:pipe_wood_item" />, <ItemLink id="buildcraftunofficial:pipe_wood_rf" />
