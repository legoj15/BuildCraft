---
navigation:
  title: "Logic Gates"
  parent: contents/logic/gate.md
  position: 27
  icon: buildcraftunofficial:plug_gate
item_ids:
  - buildcraftunofficial:plug_gate
---

# Logic Gates

<ItemImage id="buildcraftunofficial:plug_gate" scale="2" float="right" />

如今的红石电路太占地方了。何不借助管道，在更紧凑的空间里更精细地控制你的系统？只需在管道上放一个门，然后开动脑筋。

<Row>
<Recipe id="buildcraftunofficial:gate_basic" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_iron_and" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_nether_brick_and" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_iron_and_lapis" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_iron_and_quartz" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_swap_gold_and_diamond_to_or" fallbackText="" />
<Recipe id="buildcraftunofficial:gate_swap_gold_or_diamond_to_and" fallbackText="" />
</Row>

*Two of these recipes each stand for 12 of their kind.*

## 门的放置

门是一种可插拔组件，因此只能放置在管道上，并且可以放置在管道的任意一面。

在该面与其相邻的任何管道都不会连接。

## 门的等级

门分为不同的等级，各自提供的槽位数量不同：

基础门 — 提供 1 组槽位（无法改装）。

铁质门 — 提供 2 组槽位。

地狱砖质门 — 提供 4 组槽位。

金质门 — 提供 8 组槽位。

## 门的改装

门可以通过改装来提供参数槽位：

青金石扩展 — 为每个触发条件提供 1 个参数槽位。

石英扩展 — 为每组槽位提供 1 个参数槽位（会使门的槽位数量减半）。

钻石扩展 — 为每组槽位提供 3 个参数槽位（会使门的槽位数量减半）。

## 门的变体

使用与门时，必须**全部**相连的触发条件都处于激活状态，通往动作的连接才会激活。

使用或门时，必须有**一个或多个**相连的触发条件处于激活状态，通往动作的连接才会激活。

## 触发条件

左侧的图标称为触发条件。可将它们拖入界面左侧的槽位中。

不同的触发条件用于检测不同的情况，各自有不同的判定标准。当某个触发条件的判定标准得到满足时，相邻的连接就会激活。

## 动作

右侧的图标称为动作。可将它们拖入界面右侧的槽位中。

不同的动作会完成不同的任务。当相邻的连接处于激活状态时，与之相连的动作就会执行。

## 参数

安装参数槽位后，部分触发条件/动作旁边会出现额外的槽位。

这些额外的槽位可以提供更多功能，让你的触发条件/动作更加具体。

在这些额外的槽位中，你可以点击槽位来查看更多选项，或者放入物品作为过滤条件。

## 连接

中间的线条用于将触发条件与动作连接起来，使它们能够协同工作。

点击这些线条可以将其连接/断开，从而要求多个触发条件/动作。

当某个触发条件得到满足时，中间的线条会亮起（如果需要多个触发条件，则只会部分亮起）。

当线条亮起时，与之相连的动作就会执行。
