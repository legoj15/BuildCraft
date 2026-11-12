---
navigation:
  title: "配送板"
  parent: contents/robots/board.md
  position: 45
  icon: buildcraftunofficial:redstone_board
---

# 配送板

快递员。它读取订单表格，然后照单履约。

## 行为

机器人会找到最近的待处理请求并预留下来，在门的“提供物品”动作处于激活状态的对接站装载所请求的物品，然后飞往发出请求的对接站，把物品交出去。如果它到处都找不到该物品，就会搁置这份请求，直到下一次搜索。无法再交付的货物会在启用“接受物品”的对接站卸下，实在没办法时才会被丢在地上。编程消耗 12,800 MJ。

## 另见

- [请求机](../block/requester.md)
- [请求物品](../action/station_request_items.md)
- [机器人入门](../concept/robots.md)
