<lore>
能量网络上的阀门——当机器只需要涓涓细流时，把流量调小即可。
</lore>
<no_lore>
铁质动能管道传输 MJ 能量，并且可以限制允许通过的能量数量。
</no_lore>

<recipes stack="buildcraftunofficial:pipe_iron_power"/>

<chapter name="管道机制"/>
铁质动能管道默认最多可承载 32 MJ/t，并且可以充当能量限制器。用扳手右击它，即可在七个档位间循环调整上限——从管道的满速率开始逐级减半，直到完全归零——传输量既可以被压低，也可以被彻底切断。

当前的限制值会显示在管道上，并且每次更改时都会在屏幕消息中予以确认。能量由木质动能管道送入管网。
<link to="buildcraftunofficial:pipe/wood_power"/>

<chapter name="门控"/>
安放在管道上的门可以使用“设置能量限制”系列动作自动设置相同的限制，根据任意触发器更换上限。
<link to="buildcraft:set_power_limit"/>

<usages stack="buildcraftunofficial:pipe_iron_power"/>
