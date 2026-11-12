---
navigation:
  title: "填充机"
  parent: contents/building/builders.md
  position: 7
  icon: buildcraftunofficial:filler
item_ids:
  - buildcraftunofficial:filler
---

# 填充机

<ItemImage id="buildcraftunofficial:filler" scale="2" float="right" />

有时你想要的并不是一座建筑。只是一块平整的地面、一个球体、宽阔的阶梯，或是一个掏空的房间。填充机可以让你在划定的体积内选取几何样式，而无需使用模板。

## 信息

将填充机放置在由[地标](./marker_volume.md)标定出的区域一角，从界面左侧把一个样式拖入填充机的样式槽位，并为其供应能量和材料。填充机还接受一些可选参数（角偏移、尺寸、百分比），并会将其资源物品栏中的方块放置到标记盒内对应的位置上。体积内已有的流体会被原样保留，填充机会在它们周围照常完成工作。如果某个保护或圈地类模组不允许填充机在某个位置放置方块，它会跳过该位置、继续处理其余部分，并在约 10 秒后再次尝试。

**挖掘**开关会在填充前先清除形状的内部。挖出的方块会从填充机中弹出（前提是它们能被铁镐挖掘）。

**反转**开关会对样式加以扩展，转而作用于*不属于*样式形状的方块。

**预览**开关（眼睛图标）会在样式尚未填充的每个位置上绘制半透明方块，填充机附近的所有人都能看到。填充机放置或破坏方块时，预览会自动隐藏。

**控制模式**指示器（由管道门设置）会在开启（默认状态）、关闭和循环之间切换。如果相邻的门正在设置样式参数，则会出现一个挂锁图标。

<Row>
<RecipesFor id="buildcraftunofficial:filler" fallbackText="" />
</Row>

**用于合成:** <ItemLink id="buildcraftunofficial:builder" />
