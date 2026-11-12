<lore>
无需再手动逐一记录结构的形状与复杂细节，这个方块可以捕捉标记盒内的所有数据；交给<link inline="buildcraftunofficial:block/builder"/>后，你可以按 1:1 原样重建，也可以只使用结构的形状。
</lore>
<no_lore>
建筑工作台会把地标标定区域的内容扫描到蓝图或模板中，生成一份已填充的快照物品，之后可由<link inline="buildcraftunofficial:block/builder"/>在别处重建。
</no_lore>
<chapter name="信息"/>
将建筑工作台放置在由<link inline="buildcraftunofficial:block/marker_volume"/>标定出的区域一角，即可把该角登记为捕获的起始角。破坏或移除建筑工作台会移除标记盒，此后需要用地标重新标定
将<link inline="buildcraftunofficial:item/blueprint_clean"/>或<link inline="buildcraftunofficial:item/template_clean"/>放入输入槽位。在槽位下方的输入框中输入名称，以便该快照在你的物品栏或<link inline="buildcraftunofficial:block/library"/>中与其他快照区分。界面顶部的 3D 预览会旋转展示标定区域当前内容的实时模型，让你看到将要捕获的内容。
只要插入任意蓝图或模板，扫描就会立即开始。扫描会遍历整个标记盒，并把已填充（已使用）的快照弹出到输出槽位。扫描不消耗能量，而且已用过的蓝图和模板可以再次使用，用来覆盖其中的内容。
<recipes_usages stack="buildcraftunofficial:architect"/>
<chapter name="蓝图与模板的区别"/>
<bold>蓝图</bold>会记录标记盒内每个位置的完整方块状态，以及其中的所有实体。这会捕获所有方块的朝向、状态和物品栏。建筑机会用捕获时相同的材料将它们重建出来。
<bold>模板</bold>只记录每个位置是被占用还是为空。建筑机会用你为它储备的任意一种方块填充每个已捕获的位置；非常适合柱子、墙体这类可反复使用的结构。从技术角度讲，模板的扫描速度是蓝图的三倍，序列化出的文件也小得多。
