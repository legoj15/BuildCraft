---
navigation:
  title: "条纹运输管道"
  parent: contents/transport/pipe_item.md
  position: 56
  icon: buildcraftunofficial:pipe_stripes_item
item_ids:
  - buildcraftunofficial:pipe_stripes_item
---

# 条纹运输管道

<ItemImage id="buildcraftunofficial:pipe_stripes_item" scale="2" float="right" />

想象一根能放置方块、破坏方块并使用方块的管道。你正在想象的就是这根管道。

<Row>
<RecipesFor id="buildcraftunofficial:pipe_stripes_item" fallbackText="" />
</Row>

## 管道运作

在供能状态下，它会破坏正前方的方块，并将其传递给相邻的管道或容器。

当方块被输送进管道时，它会将该方块放置在正前方。

当物品被输送进管道时，它会尝试对正前方的方块/实体“使用”该物品。

## 管道方向

条纹运输管道必须“朝向”某个方向，才能对前方的方块产生作用。

如果有多根管道/多个容器连接到它，它就会失去对世界产生作用的能力。

## 管道机制

如果收下的物品无处可去，就会从管道中掉落到地上。

使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

## 供能

它可以从任何 MJ 能量源接收能量，例如：引擎、管道能量适配器或管道脉冲器。

- [红石引擎](../block/engine_redstone.md)
- [管道能量适配器](../item/plug_power_adaptor.md)
- [管道脉冲器](../item/plug_pulsar.md)

它每收到一次能量脉冲，就会消耗所收到的能量，并尝试破坏前方的方块。

脉冲的能量越多，用于破坏方块的能量就越多。
