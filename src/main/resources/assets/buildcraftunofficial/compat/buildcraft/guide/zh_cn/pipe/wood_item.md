<lore>
想把物品取出来并运往别处？只需给它供能，看着它们流动即可。
</lore>
<no_lore>
木质运输管道用于在接收到能量时从容器中提取物品。
</no_lore>

<recipes stack="buildcraftunofficial:pipe_wood_item"/>

<chapter name="管道机制"/>
供能后，它会提取物品，并将它们运送到任何其他相连的容器/管道中。
物品的提取会从管道实心的侧面进行，并被送往透明面朝向的那一侧。
如果有多个透明面朝向的侧面，物品会在这些方向之间随机移动。
如果物品无处可去，被提取的物品就会从管道中掉出，散落在地面上。

某些机器可以直接将物品自动输出到木质运输管道中，而无需管道自身供能。
使用管塞或给管道刷上不同的颜色，都可以阻止管道相互连接。

<chapter name="供能"/>
它可以从任何 MJ 能量源接收能量，例如：引擎、管道能量适配器或管道脉冲器。
<link to="buildcraftunofficial:block/engine_redstone"/>
<link to="buildcraftunofficial:item/plug_power_adaptor"/>
<link to="buildcraftunofficial:item/plug_pulsar"/>
它每接收一次能量脉冲，就会从相邻的容器中提取物品。
脉冲包含的能量越多，每次脉冲提取的物品就越多。

<usages stack="buildcraftunofficial:pipe_wood_item"/>
