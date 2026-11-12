---
navigation:
  title: "木质流体管道"
  parent: contents/transport/pipe_fluid.md
  position: 16
  icon: buildcraftunofficial:pipe_wood_fluid
item_ids:
  - buildcraftunofficial:pipe_wood_fluid
---

# 木质流体管道

<ItemImage id="buildcraftunofficial:pipe_wood_fluid" scale="2" float="right" />

想要取出流体并把它运到别处？只需给它供能，看着它流动就行了。

<Row>
<RecipesFor id="buildcraftunofficial:pipe_wood_fluid" fallbackText="" />
</Row>

## 管道机制

供能后，它会提取流体，并将流体输送到任何其他相连的储罐/管道中。

某些机器可以直接将流体输出到木质流体管道中，无需供能。

流体提取会从管道实心的侧面流出，送往透明面朝向的那一侧。

如果透明面朝向多个方向，流体会沿每个方向均匀分配。

某些机器可以直接将流体输出到木质流体管道中，无需供能。

使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

## 供能

它可以从任何 MJ 能量源接收能量，例如：引擎、管道能量适配器或管道脉冲器。

- [红石引擎](../block/engine_redstone.md)
- [管道能量适配器](../item/plug_power_adaptor.md)
- [管道脉冲器](../item/plug_pulsar.md)

它每收到一次能量脉冲，就会从相邻的储罐中提取一次流体。

脉冲的能量越多，每次脉冲提取的流体就越多。

木质流体管道会以每秒 0.2 桶的速度将流体输送到相邻的管道和储罐。

流体管道带有一个内部流体储罐，每一面可容纳 1 桶，中心也可容纳 1 桶。

**用于合成:** <ItemLink id="buildcraftunofficial:pipe_wood_item" />
