---
navigation:
  title: "区域规划机"
  parent: contents/robots/robot.md
  position: 8
  icon: buildcraftunofficial:zone_planner
item_ids:
  - buildcraftunofficial:map_location
  - buildcraftunofficial:zone_planner
---

# 区域规划机

<ItemImage id="buildcraftunofficial:zone_planner" scale="2" float="right" />

一张供你在世界上作画的地图桌。画出机器人可耕种的田地、可砍伐的森林、可开采的矿坑，再把每幅画交给一扇门。

## 绘制区域

打开区域规划机后，空手拖动可平移地图，滚动可缩放；完全缩小后便能以勘测视图总览已加载的区域。手持[颜料刷](../item/paintbrush.md)在地图上拖动即可绘制该颜色的区域，右键拖动可擦除。区域规划机会保存十六个区域，每种颜色一个，关闭后也会记住它们。区域是柱形的：画出的一个方格会覆盖所有高度。

## 复制区域

把<ItemLink id="buildcraftunofficial:map_location" />连同一把该区域颜色的颜料刷一起放入输出槽位，区域规划机便会把该区域写入图纸。输入槽位则相反，会把定位图纸上的区域取入区域规划机，旁边放一把颜料刷可选择要改写哪种颜色。存有区域的定位图纸可以放入石英门或钻石门上“工作区域”动作的参数槽；机器人此后便只在那幅画内工作。

<Row>
<RecipesFor id="buildcraftunofficial:zone_planner" fallbackText="" />
</Row>

## 另见

- [机器人入门](../concept/robots.md)
