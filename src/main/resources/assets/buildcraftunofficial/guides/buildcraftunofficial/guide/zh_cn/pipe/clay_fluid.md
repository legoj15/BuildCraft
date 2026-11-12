---
navigation:
  title: "粘土流体管道"
  parent: contents/transport/pipe_fluid.md
  position: 19
  icon: buildcraftunofficial:pipe_clay_fluid
item_ids:
  - buildcraftunofficial:pipe_clay_fluid
---

# 粘土流体管道

<ItemImage id="buildcraftunofficial:pipe_clay_fluid" scale="2" float="right" />

<Row>
<RecipesFor id="buildcraftunofficial:pipe_clay_fluid" fallbackText="" />
</Row>

## 管道机制

粘土流体管道会优先将流体注入相邻的储罐，然后才会流向下一根管道。

流体不会沿来路返回，只会流向其他方向。

如果流体可以流向多个方向，流体便会在这些方向之间均匀分配（优先分配给储罐）。

某些机器可以直接将流体输出到粘土流体管道中，无需再经由供能的提取管道输送。

使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

粘土流体管道会以每秒 0.8 桶的速度将流体输送到相邻的管道和储罐。

流体管道带有一个内部流体储罐，每一面可容纳 1 桶，中心也可容纳 1 桶。

**用于合成:** <ItemLink id="buildcraftunofficial:pipe_clay_item" />
