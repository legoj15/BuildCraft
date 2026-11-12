<lore>
机器人的停歇之处，用螺栓固定在管道一侧。把它固定在哪根管道上，便决定了机器人能在那里做什么。
</lore>
<no_lore>
对接站是一种供机器人停靠的可插拔组件。它所在的管道以及那条管道上的门，共同决定停靠的机器人可以做什么：充电、取送物品和流体，或者只是休息。
</no_lore>
<chapter name="放置"/>
右键点击管道的任意一面，即可在那里装上一个对接站。然后手持已编程的<link inline="buildcraftunofficial:item/robot"/>右键点击对接站，把机器人放置上去；该站随即成为机器人的母站，机器人也与它<bold>绑定</bold>。机器人的母站前方必须留出两格空位。潜行并用扳手右键点击机器人即可将它收回（手里拿着工具的机器人会先放下工具）。
<chapter name="由管道决定"/>
在动能（能量）管道上，对接站用管道中的能量为停靠的机器人充电。在<bold>木质运输管道</bold>上，对接站可以发放管道提取面所朝物品栏中的物品；在木质流体管道上，它也能以同样的方式发放流体。在任何运输管道上，对接站都能从机器人手中接过货物，并将其推入管道。比如，圆石管道上的对接站就只能接收交付的货物。
<chapter name="由门决定"/>
充电不需要门，但若没有对接站所在管道上的门下令，上文的物品与流体往来一律不会发生。对接站动作如下：
<link to="buildcraftunofficial:action/station_provide_items"/>
<link to="buildcraftunofficial:action/station_accept_items"/>
<link to="buildcraftunofficial:action/station_provide_fluids"/>
<link to="buildcraftunofficial:action/station_accept_fluids"/>
<link to="buildcraftunofficial:action/station_request_items"/>
<link to="buildcraftunofficial:action/station_request_needed_items"/>
<link to="buildcraftunofficial:action/station_forbid_robot"/>
<link to="buildcraftunofficial:action/station_force_robot"/>
禁用机器人和强制机器人以某一种机器人为参数（用已编程的机器人点击参数槽位，或空手点击以在各块电路板之间循环切换），前者会把这类机器人拒之门外，后者则会把对接站预留给这类机器人。请求物品会向路过的配送机器人索取一件物品，就好比一个只有一个槽位的<link inline="buildcraftunofficial:block/requester"/>。门还可以读取对接站的状态：
<link to="buildcraftunofficial:trigger/robot_in_station"/>
<link to="buildcraftunofficial:trigger/station_linked"/>
<link to="buildcraftunofficial:trigger/station_reserved"/>
<link to="buildcraftunofficial:trigger/robot_sleep"/>
而母站给自己机器人下达的指令，就是这些机器人动作：
<link to="buildcraftunofficial:action/robot_work_in_area"/>
<link to="buildcraftunofficial:action/robot_goto_station"/>
<link to="buildcraftunofficial:action/robot_wake_up"/>
<link to="buildcraftunofficial:action/robot_filter"/>
<link to="buildcraftunofficial:action/robot_filter_tool"/>
<recipes_usages stack="buildcraftunofficial:robot_station"/>
<chapter name="另见"/>
<link to="buildcraftunofficial:concept/robots"/>
