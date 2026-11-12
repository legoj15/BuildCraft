<lore>
有了光线感应器，你就可以利用光的力量来控制你的门。
</lore>
<no_lore>
光线感应器是一种可插拔组件，用于检测其前方方块的光照等级，并将该数据提供给同一根管道相邻面上的门。
</no_lore>

<recipes stack="buildcraftunofficial:plug_light_sensor"/>

<chapter name="可插拔组件机制"/>
这些数据让相邻的门能够通过明亮和黑暗触发条件检测当前是亮还是暗。
这些明亮和黑暗触发条件可以通过门来触发动作。

<link to="buildcraftunofficial:trigger/light_high"/>

<link to="buildcraftunofficial:trigger/light_low"/>

<chapter name="管道连接"/>
可插拔组件只能放置在管道上，并且可以放置在管道的任意一面。
当它被放置在两根管道之间时，会阻止这两根管道之间任何其他管道和管线的连接。

<usages stack="buildcraftunofficial:plug_light_sensor"/>
