---
navigation:
  title: "液泵"
  parent: contents/mining_fluids/fluid.md
  position: 11
  icon: buildcraftunofficial:pump
item_ids:
  - buildcraftunofficial:pump
---

# 液泵

<ItemImage id="buildcraftunofficial:pump" scale="2" float="right" />

手动收集流体有时相当乏味且危险，因此需要一种自动采集流体的方法。

要从世界中采集流体，请将液泵放在你想抽取的流体的源方块上方，并为其供给能量。

下方的流体源会从最上方的源开始自上而下地被“吸”走，并自动输出到相邻的管道/储罐中。

它会在 64 格半径内自上而下地泵取流体，并从相连流体的外缘向内泵取。

## 机器原理

与大多数机器一样，它需要能量。供给的能量越多，泵送速度就越快。

对液泵施加红石信号会使其停止泵送，所以要小心拉杆的放置位置。

## 放在矿井或采石场上

放在矿井或采石场顶部时，液泵不再向下泵取流体，转而抽干挡住机器去路的熔岩、原油或其他黏稠液体：它会先消耗自身储存的能量，等到机器没有别的东西可挖时再消耗机器的能量（只有与液泵同属一个所有者的机器才会共享能量）。液体被抽干后，采石场会回头处理原先被液体覆盖的方块。液泵停在那里期间，其状态灯会变为蓝色：有液体排队等待抽干时明亮，没有液体时暗淡。它无法清除黑曜石之类的固体方块。请用管道将泵出的液体运走，否则液泵的储罐半满时就会停止工作。

<Row>
<RecipesFor id="buildcraftunofficial:pump" fallbackText="" />
</Row>

**用于合成:** <ItemLink id="buildcraftunofficial:builder" />
