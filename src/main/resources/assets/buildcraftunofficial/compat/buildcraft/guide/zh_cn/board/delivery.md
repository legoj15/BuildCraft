<lore>
快递员。它读取订单表格，然后照单履约。
</lore>
<no_lore>
配送板让机器人履约物品请求：请求机中的订单，以及对接站门上的“请求物品”动作。
</no_lore>
<chapter name="行为"/>
机器人会找到最近的待处理请求并预留下来，在门的“提供物品”动作处于激活状态的对接站装载所请求的物品，然后飞往发出请求的对接站，把物品交出去。如果它到处都找不到该物品，就会搁置这份请求，直到下一次搜索。无法再交付的货物会在启用“接受物品”的对接站卸下，实在没办法时才会被丢在地上。编程消耗 12,800 MJ。
<chapter name="另见"/>
<link to="buildcraftunofficial:block/requester"/>
<link to="buildcraftunofficial:action/station_request_items"/>
<link to="buildcraftunofficial:concept/robots"/>
