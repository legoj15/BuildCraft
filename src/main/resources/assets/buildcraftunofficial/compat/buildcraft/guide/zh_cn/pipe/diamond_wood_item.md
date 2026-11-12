<lore>
想把特定物品取出来并运往别处？只需给它供能，看着它们流动即可。
</lore>
<no_lore>
木质钻石运输管道用于在接收到能量时，根据其过滤器从容器中提取选定的物品。
</no_lore>

<recipes stack="buildcraftunofficial:pipe_diamond_wood_item"/>

<chapter name="管道机制"/>
供能后，它会提取过滤器中选定的物品，并将它们运送到任何其他相连的容器/管道中。
物品的提取会从管道实心的侧面进行，并被送往透明面朝向的那一侧。
如果有多个透明面朝向的侧面，物品会随机选择一个方向移动。
如果物品无处可去，被提取的物品就会从管道中掉出，散落在地面上。

某些机器可以直接将物品自动输出到木质钻石运输管道中，而无需管道自身供能（这种方式不会遵循过滤器）。
使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

<chapter name="过滤"/>
打开木质钻石运输管道的 GUI 即可设置过滤器。
将想要的物品放入槽位中，即可过滤它提取的物品。
你可以使用允许列表、阻止列表或轮询（按顺序）选项来改变过滤器的行为：
允许列表：只提取选中的物品。
阻止列表：提取选中物品以外的任何物品。
轮询（按顺序）：按上方过滤槽位中选择的确切顺序（从左到右）提取物品

列表可放入过滤槽位中，为你提供更多扩展的过滤选项。
<link to="buildcraftunofficial:item/list"/> 

<chapter name="供能"/>
它可以从任何 MJ 能量源接收能量，例如：引擎、管道能量适配器或管道脉冲器。
<link to="buildcraftunofficial:block/engine_redstone"/>
<link to="buildcraftunofficial:item/plug_power_adaptor"/>
<link to="buildcraftunofficial:item/plug_pulsar"/> 
它每接收一次能量脉冲，就会从相邻的容器中提取物品。
脉冲包含的能量越多，每次脉冲提取的物品就越多。

<usages stack="buildcraftunofficial:pipe_diamond_wood_item"/>
