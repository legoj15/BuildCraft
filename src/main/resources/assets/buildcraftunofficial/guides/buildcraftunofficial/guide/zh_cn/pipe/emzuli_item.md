---
navigation:
  title: "靛翡翠运输管道"
  parent: contents/transport/pipe_item.md
  position: 53
  icon: buildcraftunofficial:pipe_emzuli_item
item_ids:
  - buildcraftunofficial:pipe_emzuli_item
---

# 靛翡翠运输管道

<ItemImage id="buildcraftunofficial:pipe_emzuli_item" scale="2" float="right" />

利用门来提取特定物品，是过滤技术领域最新的技术革命。

<Row>
<RecipesFor id="buildcraftunofficial:pipe_emzuli_item" fallbackText="" />
</Row>

## 管道机制

在供能时，它会提取过滤器中选定的物品，并将其输送到任何其他相连的容器/管道中。

物品会从管道实心的侧面提取出来，并被送往透明面朝向的那些侧面。

如果有多个透明面朝向的侧面，物品会随机朝一个方向移动。

如果提取出的物品无处可去，它们就会从管道中掉落到地上。

某些机器可以自动将物品直接输出到靛翡翠运输管道中，而无需管道自身供能（这不会遵循过滤器）。

使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

## 过滤

打开靛翡翠运输管道的 GUI 后，会看到四种颜色，对应四个独立的过滤选项。

将物品放入各个槽位，即可决定每个过滤器提取什么。

槽位旁颜料刷的颜色决定了物品被提取时会被染上的颜色，点击颜料刷即可更改。

要选择由四个过滤器中的哪一个进行提取，需要在管道上放置一个门，并设置对应颜色的提取预设动作。例如：红石信号 -> 红色提取预设。

- [Emzuli Extraction Presets](../concept/emzuli_extraction_presets.md)

可以在过滤槽位中使用列表，以获得扩展的过滤选项。

- [列表](../item/list.md)

## 供能

它可以接收来自任何 MJ 能量源的能量，例如：引擎、管道能量适配器或管道脉冲器。

每接收一次能量脉冲，它就会从相邻的容器中提取物品。

脉冲包含的能量越多，每次脉冲提取的物品就越多。
