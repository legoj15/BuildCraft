---
navigation:
  title: "钻石运输管道"
  parent: contents/transport/pipe_item.md
  position: 50
  icon: buildcraftunofficial:pipe_diamond_item
item_ids:
  - buildcraftunofficial:pipe_diamond_item
---

# 钻石运输管道

<ItemImage id="buildcraftunofficial:pipe_diamond_item" scale="2" float="right" />

自行选择物品可以去往何处，或许对你的系统很有帮助。

<Row>
<RecipesFor id="buildcraftunofficial:pipe_diamond_item" fallbackText="" />
</Row>

## 设置过滤器

钻石运输管道的各个侧面以不同的颜色区分。

打开钻石运输管道的界面后，即可设置过滤器。

把想要的物品放入某种颜色的槽位中，就能决定哪些物品朝哪个方向输送。

在同一颜色中放入多份相同物品，可以为物品的去向增加“权重”。

在过滤槽位中放入列表，可以获得更多过滤选项。

- [列表](../item/list.md)

## 管道机制

当物品存在多个可去的方向时，物品会（根据你设定的权重）在这些方向之间分配。

未设置过滤器的有效方向允许任意物品通过，但已过滤的方向会被优先选择。

物品不会沿来路返回，只会朝其他方向前进。

如果没有相连的管道，或相邻的容器已满且物品无处可去，物品就会从管道中掉落到地上。

某些机器可以自动将物品直接输出到钻石运输管道中，无需再经由提取管道输送。

使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

**用于合成:** <ItemLink id="buildcraftunofficial:filtered_buffer" />, <ItemLink id="buildcraftunofficial:pipe_diamond_fluid" />, <ItemLink id="buildcraftunofficial:pipe_diamond_power" />
