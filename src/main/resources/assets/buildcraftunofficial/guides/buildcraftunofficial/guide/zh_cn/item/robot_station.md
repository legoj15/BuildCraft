---
navigation:
  title: "对接站"
  parent: contents/robots/station.md
  position: 17
  icon: buildcraftunofficial:robot_station
item_ids:
  - buildcraftunofficial:robot_station
---

# 对接站

<ItemImage id="buildcraftunofficial:robot_station" scale="2" float="right" />

机器人的停歇之处，用螺栓固定在管道一侧。把它固定在哪根管道上，便决定了机器人能在那里做什么。

## 放置

右键点击管道的任意一面，即可在那里装上一个对接站。然后手持已编程的[机器人](./robot.md)右键点击对接站，把机器人放置上去；该站随即成为机器人的母站，机器人也与它**绑定**。机器人的母站前方必须留出两格空位。潜行并用扳手右键点击机器人即可将它收回（手里拿着工具的机器人会先放下工具）。

## 由管道决定

在动能（能量）管道上，对接站用管道中的能量为停靠的机器人充电。在**木质运输管道**上，对接站可以发放管道提取面所朝物品栏中的物品；在木质流体管道上，它也能以同样的方式发放流体。在任何运输管道上，对接站都能从机器人手中接过货物，并将其推入管道。比如，圆石管道上的对接站就只能接收交付的货物。

## 由门决定

充电不需要门，但若没有对接站所在管道上的门下令，上文的物品与流体往来一律不会发生。对接站动作如下：

- [提供物品](../action/station_provide_items.md)
- [接受物品](../action/station_accept_items.md)
- [提供流体](../action/station_provide_fluids.md)
- [接受流体](../action/station_accept_fluids.md)
- [请求物品](../action/station_request_items.md)
- [请求所需物品](../action/station_request_needed_items.md)
- [禁用机器人](../action/station_forbid_robot.md)
- [强制机器人](../action/station_force_robot.md)

禁用机器人和强制机器人以某一种机器人为参数（用已编程的机器人点击参数槽位，或空手点击以在各块电路板之间循环切换），前者会把这类机器人拒之门外，后者则会把对接站预留给这类机器人。请求物品会向路过的配送机器人索取一件物品，就好比一个只有一个槽位的[请求机](../block/requester.md)。门还可以读取对接站的状态：

- [机器人入驻](../trigger/robot_in_station.md)
- [转接坞已连接](../trigger/station_linked.md)
- [转接坞已预留](../trigger/station_reserved.md)
- [Robot Sleep](../trigger/robot_sleep.md)

而母站给自己机器人下达的指令，就是这些机器人动作：

- [工作区域](../action/robot_work_in_area.md)
- [前往转接坞](../action/robot_goto_station.md)
- [唤醒](../action/robot_wake_up.md)
- [Robot Filter](../action/robot_filter.md)
- [Robot Filter Tool](../action/robot_filter_tool.md)

<Row>
<RecipesFor id="buildcraftunofficial:robot_station" fallbackText="" />
</Row>

## 另见

- [机器人入门](../concept/robots.md)
