---
navigation:
  title: "采石场"
  parent: contents/mining_fluids/mining.md
  position: 19
  icon: buildcraftunofficial:quarry
item_ids:
  - buildcraftunofficial:quarry
---

# 采石场

<ItemImage id="buildcraftunofficial:quarry" scale="2" float="right" />

一台 [矿井](./mining_well.md) 只能钻出一根竖井，而采石场会把整片区域从世界上啃掉。只要有合适的能量供给，再配上一个足够大的 [地标](./marker_volume.md) 框架，它就能自己一路露天开采到基岩。

## 信息

用地标（默认最大 64×64）围住你想要开采的区域，把采石场放置在其中任意一个地标旁边，然后为它供能。采石场会先在区域四周搭起框架，随后采矿头在框架内移动，逐一开采每个方块。产出的物品会流入任意相邻的容器或管道；如果没有地方容纳掉落物，采石场就会把物品直接丢到世界中。

采石场会一直工作，直到抵达基岩，或再无可开采的方块为止。它侧面的红色 <Color color="#FF5555">LED</Color> 亮起表示它有工作但没有能量；绿色 <Color color="#55FF55">LED</Color> 亮起表示它正在开采；两盏 <Color color="#FF5555">LE</Color><Color color="#55FF55">D</Color> 同时亮起则表示它处于闲置或已完成状态。

<Row>
<RecipesFor id="buildcraftunofficial:quarry" fallbackText="" />
</Row>

## 挖掘等级

采石场的采矿头开采方块时，就如同手持一把钻石镐。只有当它遇到无法通过的流体（例如熔岩或原油），或钻石镐无法成功开采的方块时，它才会跳过不开采。

在采石场顶部放置一台 [液泵](./pump.md)，即可抽干它先前跳过的熔岩或原油。待液体被清空、采石场完成其余区域的作业后，它会回过头来开采原本位于液体下方的方块。

方块被开采时掉落的经验球（红石矿石、青金石、钻石、煤炭等）会生成在采石场方块本身所在的位置，而不是矿坑中，这样你无需一路垂降到基岩，就能直接在机器旁收集经验。

## 能量

和矿井一样，能量供给越多，开采速度就越快。红石信号会暂停采石场，但不会重置它的进度。
