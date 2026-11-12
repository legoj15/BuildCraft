---
navigation:
  title: "Деревянная FE-труба"
  parent: contents/power/pipe_rf.md
  position: 30
  icon: buildcraftunofficial:pipe_wood_rf
item_ids:
  - buildcraftunofficial:pipe_wood_rf
---

# Деревянная FE-труба

<ItemImage id="buildcraftunofficial:pipe_wood_rf" scale="2" float="right" />

Каждой сети Forge Energy нужен вход. Поставьте её вплотную к работающему динамо — и пусть энергия льётся потоком.

<Row>
<RecipesFor id="buildcraftunofficial:pipe_wood_rf" fallbackText="" />
</Row>

## Механика труб

Деревянная FE-труба — это путь, которым Forge Energy попадает в сеть. MJ-динамо и другие источники FE подключаются к ней; она втягивает их выходную энергию и передаёт её обычным FE-трубам, присоединённым к её остальным сторонам, а те доставляют её к машинам, которые её потребляют.

По умолчанию она передаёт до 160 FE/т. Как и Деревянная транспортная труба, она не соединяется с другой деревянной FE-трубой.

## Питание

MJ-динамо или любой генератор Forge Energy из другого мода может питать Деревянную FE-трубу.

- [MJ-динамо](../block/mj_dynamo.md)

Затем сеть доставляет эту энергию любой машине, потребляющей FE, например FE-двигателю, который преобразует её обратно в MJ.

- [FE-двигатель](../block/engine_rf.md)

**Используется для крафта:** <ItemLink id="buildcraftunofficial:pipe_wood_power" />
