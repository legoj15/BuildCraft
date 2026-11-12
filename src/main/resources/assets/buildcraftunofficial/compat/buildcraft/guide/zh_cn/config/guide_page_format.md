指南页面全部以 markdown 的一个子集编写。

<italic>（本页大部分内容尚待补充，不过这里先列出基本标记的清单）</italic>

# 简单单标记
每个标记都采用 &lt;name arguments/&gt; 的形式——其中 name 是标记名，结尾的“/”表示该标记独立自闭合。有些标记还带有“键值对”形式的参数，例如：
<guide_md>
"level"="42"
</guide_md>

## 新页面
&lt;new_page/&gt; 会将其后的内容移到下一页。

## 链接
这类标记会创建指向另一个指南书页面的链接，并使用所链接页面的标题和图标。你也可以为某个物品（ItemStack）或语句（Statement）创建链接，即使它们并没有现成的指南书页面。
例如：
<guide_md>
&lt;link to="buildcraftunofficial:config/json_insn_format"/&gt;
</guide_md>
结果为：
<link to="buildcraftunofficial:config/json_insn_format"/>

或者：
<guide_md>
&lt;link to="minecraft:iron_ingot" type="item_stack"/&gt;
</guide_md>
结果为：
<link to="minecraft:iron_ingot" type="item_stack"/>

## 章节
&lt;chapter/&gt; 会创建一个“章节标题”，显示在章节列表中时呈现为彩色标题。它只有一个可用的参数："level"，其值必须是 0 或更大的整数。此外，你也可以像普通的 markdown 格式那样使用多个井号（#）来创建标题。井号的数量（减去一）就等于标题的级别。
例如：
<guide_md>
 ### Level 3 chapter
&lt;chapter name="Another level 3 chapter" level="2"/&gt;
</guide_md>
结果为：
### Level 3 chapter
<chapter name="Another level 3 chapter" level="2"/>

# 简单双标记
所有这些标记都必须包围住一些内容：也就是说，它们必须同时具有起始标记 &lt;italic&gt; 和结束标记 &lt;/italic&gt;。
它们都会影响起始标记与结束标记之间所包含的文本。

- <italic>斜体</italic>
- <bold>粗体</bold>
- <underline>下划线</underline>
- <strikethrough>删除线</strikethrough>
- <red>红色</red>
- <black>黑色</black>
- <dark_blue>深蓝色</dark_blue>
- <dark_green>深绿色</dark_green>
- <dark_aqua>深青色</dark_aqua>
- <dark_red>深红色</dark_red>
- <dark_purple>深紫色</dark_purple>
- <gold>金色</gold>
- <gray>灰色</gray>
- <dark_gray>深灰色</dark_gray>
- <blue>蓝色</blue>
- <green>绿色</green>（绿色）
- <aqua>青色</aqua>（青色）
- <light_purple>浅紫色</light_purple>
- <yellow>黄色</yellow>（黄色）
- <white>白色</white>（白色）

不建议使用 green、aqua、yellow 或 white，因为这些颜色在页面上通常很难看清。

# 开关式标记
以下标记全都基于选项，可由玩家启用或禁用。

## 背景故事
如果勾选了“显示背景故事 [ ]”框，那么 &lt;no_lore&gt; 标记中的所有内容都会被隐藏；否则 &lt;lore&gt; 标记中的所有内容会被隐藏。

lore 标记应用于展示“游戏内”式的指南信息，以玩家的视角进行叙述。（就好像他们正在亲身研究一切是如何运作的那样。）与之相对的标记则用于以更正式、类似维基百科的语气传达信息，纯粹而简洁。

该选项在游戏内默认启用，而在导出时始终禁用。

## 提示
如果勾选了“显示提示 [ ]”框，那么 &lt;no_hint&gt; 标记中的所有内容都会被隐藏；否则 &lt;hint&gt; 标记中的所有内容会被隐藏。

hint 标记应用于展示额外的“提示”，说明所描述的事物可以用来做什么，或是与其他方块/物品搭配使用的不同方式。它本质上是为各种事物提供“推荐用法”。（自行摸索出这些用法本身就是乐趣之一，因此这些内容并非必需。正因如此，它默认处于禁用状态，但可以在游戏内切换，并计划在导出时也能切换。）

## 详细信息
如果配置选项 "display.guideBookEnableDetail" 被设为 "true"，那么 &lt;no_detail&gt; 标记中的所有内容都会被隐藏；否则 &lt;detail&gt; 标记中的所有内容会被隐藏。

detail 标记应用于展示计算各种数值时用到的所有数字，例如管道流速、提取速率、脉冲频率等。

该选项默认禁用，但可在游戏内切换，并计划在导出时也可切换。

# 其他
## 配方（单个）
该标记会显示为给定物品找到的第一个配方。例如：
<guide_md>
&lt;recipe stack="buildcraftunofficial:gear_stone"/&gt;
</guide_md>
<recipe stack="buildcraftunofficial:gear_stone"/>

你还可以用 `id` 属性按注册 ID 固定指定某一个配方。当按物品匹配无法唯一选出你想要的配方时，就可以使用它——例如某个合成配方的产物带有数据组件（某个特定的门变体），而基于组件的产物匹配无法从一个裸物品中把它找出来：
<guide_md>
&lt;recipe id="buildcraftunofficial:gate_basic"/&gt;
</guide_md>
<recipe id="buildcraftunofficial:gate_basic"/>

添加 `type="assembling"` 则会改为固定指定一个装配台配方，它按注册名查找（而不是按合成配方的 ID）。这使页面能够以精心安排的顺序，交错排列某个门的合成配方与装配配方：
<guide_md>
&lt;recipe id="gate-and-IRON-NO_MODIFIER" type="assembling"/&gt;
</guide_md>
<recipe id="gate-and-IRON-NO_MODIFIER" type="assembling"/>

## 配方循环
`recipe_cycle` 会把一族几乎相同的配方折叠进单个面板，并让它们同步轮换——输入、产物以及（装配配方的）MJ 消耗会一同推进到下一个配方。可以用它把某个门的与门和或门变体，或一整套转换配方，收拢成一个紧凑的动画条目，而不是许多个。用 `match` 选择配方——它是一个子串，会与每个配方的 ID（若指定了 `type="assembling"`，则与其注册名）进行比对。下例中，单个面板轮换展示铁质门的与门和或门装配配方（`match="-IRON-NO_MODIFIER"` 会同时选中两者）：
<guide_md>
&lt;recipe_cycle type="assembling" match="-IRON-NO_MODIFIER"/&gt;
</guide_md>
<recipe_cycle type="assembling" match="-IRON-NO_MODIFIER"/>

## 全部配方
该标记会显示给定物品的所有配方。例如：
<guide_md>
&lt;recipes stack="buildcraftunofficial:gear_wood"&gt;
</guide_md>
<recipes stack="buildcraftunofficial:gear_wood" chapter_level="2"/>

## 用途
与配方类似，该标记会显示给定物品的所有用途。例如：
<guide_md>
&lt;usages stack="buildcraftunofficial:engine_redstone"&gt;
</guide_md>
<usages stack="buildcraftunofficial:engine_redstone" chapter_level="2"/>

## 配方与用途
它实际上把上面两个标记合而为一，例如：
<guide_md>
&lt;recipes_usages stack="buildcraftunofficial:pipe_structure"&gt;
</guide_md>
<recipes_usages stack="buildcraftunofficial:pipe_structure" chapter_level="2"/>

## 分组
该标记会显示与给定物品、语句等相适合的分组。

## 图片
该标记会显示一张图片。它有 3 个参数：
* src：图片的源位置。
* width：图片显示时所用的宽度。默认为源图片的宽度（不过这可能不是你想要的效果）
* height：图片显示时所用的高度。

例如：
<guide_md>
&lt;image src="buildcraftunofficial:item/wrench" width="64" height="64"/&gt;
</guide_md>
<image src="buildcraftunofficial:item/wrench" width="64" height="64"/>

