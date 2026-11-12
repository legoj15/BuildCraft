<lore>
能量管网上的一道阀门——当机器只需要涓涓细流时，就把它调低。
</lore>
<no_lore>
铁质 FE 管道传输 Forge 能量，并能限制允许通过的总量。
</no_lore>

<recipes stack="buildcraftunofficial:pipe_iron_rf"/>

<chapter name="管道机制"/>
铁质 FE 管道默认最多可承载 320 FE/t，并充当限流器。用扳手右击它，即可让上限在七个档位间循环切换——从管道的全速率开始，每档减半，直至完全归零——从而可以收紧传输量，或将其完全停止。

当前限制值会显示在管道上，而且每次更改时都会通过屏幕消息予以确认。Forge 能量由木质 FE 管道送入管网。
<link to="buildcraftunofficial:pipe/wood_rf"/>

<chapter name="门控"/>
放置在管道上的门可以利用“设置能量限制”动作自动设置相同的限制值，根据任意触发条件来切换上限。
<link to="buildcraft:set_power_limit"/>

<usages stack="buildcraftunofficial:pipe_iron_rf"/>
