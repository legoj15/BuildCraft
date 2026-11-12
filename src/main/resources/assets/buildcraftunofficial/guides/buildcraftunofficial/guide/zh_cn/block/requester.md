---
navigation:
  title: "请求机"
  parent: contents/robots/station.md
  position: 25
  icon: buildcraftunofficial:requester
item_ids:
  - buildcraftunofficial:requester
---

# 请求机

<ItemImage id="buildcraftunofficial:requester" scale="2" float="right" />

方块形态的订货单。在上半部分写下你想要的物品，配送机器人会让下半部分始终保持有货。

## 使用方法

把请求机放在一根带有[对接站](../item/robot_station.md)的管道旁；对接站无论位于哪一侧都能找到它。将物品点入上半部分二十个请求槽之一，手持的数量即希望交付的数量，这便成为一份长期订单。配送机器人接下订单，在门上启用了“提供物品”动作的对接站装载该物品，飞回来并把它放进请求下方的交付槽。交付物可以用手或任何管道取出；每个槽位只接受其上方所请求的物品，因此其他东西无法经漏斗塞入。

每份订单同一时间只由一个机器人处理，找不到物品的机器人会暂时放弃该订单，直到它的下一次搜索。

<Row>
<RecipesFor id="buildcraftunofficial:requester" fallbackText="" />
</Row>

## 另见

- [机器人入门](../concept/robots.md)
