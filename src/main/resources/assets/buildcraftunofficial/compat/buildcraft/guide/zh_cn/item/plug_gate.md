<lore>
如今的红石电路太占地方了。何不借助管道，在更紧凑的空间里更精细地控制你的系统？只需在管道上放一个门，然后开动脑筋。
</lore>
<no_lore>
门是一种可升级的可插拔组件，用于提供一套逻辑框架，帮助你管理管道系统。
</no_lore>

// Each gate lists its crafting-table recipe (the slow, no-machine-needed way) immediately
// followed by its Assembly Table recipe, so the player sees both routes together. Crafting
// recipes are pinned by JSON id; the assembly entries use <recipe_cycle> to cycle the AND and
// OR variants through ONE panel rather than showing two near-identical ones. Crafting recipes
// exist only for the AND variants — to get an OR gate by hand you craft the AND gate then flip
// it with a swap recipe, and those swaps are the two cycling panels at the end. Each assembly
// match substring uniquely selects one gate's AND+OR pair (e.g. "-IRON-LAPIS" → the Iron+Lapis
// AND and OR recipes); "-<MAT>-NO_MODIFIER" selects the base (unmodified) gate of that material.
//
// Basic (clay brick) — crafting only; it has no Assembly Table recipe (terminal item).
<recipe id="buildcraftunofficial:gate_basic"/>
// Iron
<recipe id="buildcraftunofficial:gate_iron_and"/>
<recipe_cycle type="assembling" match="-IRON-NO_MODIFIER"/>
// Nether Brick
<recipe id="buildcraftunofficial:gate_nether_brick_and"/>
<recipe_cycle type="assembling" match="-NETHER_BRICK-NO_MODIFIER"/>
// Gold — Assembly Table only (no crafting recipe)
<recipe_cycle type="assembling" match="-GOLD-NO_MODIFIER"/>
// Iron + Lapis
<recipe id="buildcraftunofficial:gate_iron_and_lapis"/>
<recipe_cycle type="assembling" match="-IRON-LAPIS"/>
// Iron + Quartz
<recipe id="buildcraftunofficial:gate_iron_and_quartz"/>
<recipe_cycle type="assembling" match="-IRON-QUARTZ"/>
// Iron + Diamond — Assembly Table only
<recipe_cycle type="assembling" match="-IRON-DIAMOND"/>
// Nether Brick modifiers — Assembly Table only
<recipe_cycle type="assembling" match="-NETHER_BRICK-LAPIS"/>
<recipe_cycle type="assembling" match="-NETHER_BRICK-QUARTZ"/>
<recipe_cycle type="assembling" match="-NETHER_BRICK-DIAMOND"/>
// Gold modifiers — Assembly Table only
<recipe_cycle type="assembling" match="-GOLD-LAPIS"/>
<recipe_cycle type="assembling" match="-GOLD-QUARTZ"/>
<recipe_cycle type="assembling" match="-GOLD-DIAMOND"/>
// AND<->OR swaps — one shapeless craft flips a gate's logic. Each panel cycles through all 12
// material/modifier variants in that direction (craft an AND gate, then swap it to OR, or back).
<recipe_cycle match="_to_or"/>
<recipe_cycle match="_to_and"/>

<chapter name="门的放置"/>
门是一种可插拔组件，因此只能放置在管道上，并且可以放置在管道的任意一面。
在该面与其相邻的任何管道都不会连接。

<chapter name="门的等级"/>
门分为不同的等级，各自提供的槽位数量不同：
基础门 — 提供 1 组槽位（无法改装）。
铁质门 — 提供 2 组槽位。
地狱砖质门 — 提供 4 组槽位。
金质门 — 提供 8 组槽位。

<chapter name="门的改装"/>
门可以通过改装来提供参数槽位：
青金石扩展 — 为每个触发条件提供 1 个参数槽位。
石英扩展 — 为每组槽位提供 1 个参数槽位（会使门的槽位数量减半）。
钻石扩展 — 为每组槽位提供 3 个参数槽位（会使门的槽位数量减半）。

<chapter name="门的变体"/>
使用与门时，必须<bold>全部</bold>相连的触发条件都处于激活状态，通往动作的连接才会激活。
使用或门时，必须有<bold>一个或多个</bold>相连的触发条件处于激活状态，通往动作的连接才会激活。

<chapter name="触发条件"/>
左侧的图标称为触发条件。可将它们拖入界面左侧的槽位中。
不同的触发条件用于检测不同的情况，各自有不同的判定标准。当某个触发条件的判定标准得到满足时，相邻的连接就会激活。

<chapter name="动作"/>
右侧的图标称为动作。可将它们拖入界面右侧的槽位中。
不同的动作会完成不同的任务。当相邻的连接处于激活状态时，与之相连的动作就会执行。

<chapter name="参数"/>
安装参数槽位后，部分触发条件/动作旁边会出现额外的槽位。
这些额外的槽位可以提供更多功能，让你的触发条件/动作更加具体。
在这些额外的槽位中，你可以点击槽位来查看更多选项，或者放入物品作为过滤条件。

<chapter name="连接"/>
中间的线条用于将触发条件与动作连接起来，使它们能够协同工作。
点击这些线条可以将其连接/断开，从而要求多个触发条件/动作。
当某个触发条件得到满足时，中间的线条会亮起（如果需要多个触发条件，则只会部分亮起）。
当线条亮起时，与之相连的动作就会执行。