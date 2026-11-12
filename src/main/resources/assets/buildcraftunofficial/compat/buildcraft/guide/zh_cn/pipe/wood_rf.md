<lore>
每一座 Forge 能量电网都需要一个入口。把它贴靠在一台运转中的发电机上，任能量倾泻而过。
</lore>
<no_lore>
木质 FE 管道会从相邻的能量源抽取 Forge 能量，并将其注入 FE 管道网络。
</no_lore>

<recipes stack="buildcraftunofficial:pipe_wood_rf"/>

<chapter name="管道机制"/>
木质 FE 管道是 Forge 能量进入管网的方式。MJ 发电机和其他 FE 能量源与它相连接；它把这些能量源的输出抽进来，再传给接在它另外几侧的普通 FE 管道，由它们继续把能量送往消耗能量的机器。

它默认最多可传输 160 FE/t。与木质运输管道一样，它不会与另一根木质 FE 管道连接。

<chapter name="供能"/>
MJ 发电机，或来自其他模组的任何 Forge 能量发电机，都可以为木质 FE 管道供给能量。
<link to="buildcraftunofficial:block/mj_dynamo"/>
随后，管网会把能量输送到任何消耗 FE 的机器，例如 FE 引擎——它会把能量转换回 MJ。
<link to="buildcraftunofficial:block/engine_rf"/>

<usages stack="buildcraftunofficial:pipe_wood_rf"/>
