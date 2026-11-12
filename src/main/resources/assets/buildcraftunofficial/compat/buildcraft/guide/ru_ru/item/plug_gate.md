<lore>
Редстоун в наши дни занимает много места. Почему бы не получить более тонкий контроль над своей системой в более компактном пространстве с помощью труб — просто разместите Гейт на трубе и начинайте думать.
</lore>
<no_lore>
Гейт — это улучшаемый модуль, создающий логистическую основу, которая помогает управлять системой труб.
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

<chapter name="Размещение гейта"/>
Гейт — это модуль, поэтому его можно устанавливать только на трубах, причём с любой стороны трубы.
Любая труба, размещённая рядом с этой стороны, не соединится.

<chapter name="Уровни гейтов"/>
Гейты бывают разных уровней, предоставляющих разное количество слотов:
Основной гейт — предоставляет 1 набор слотов (его нельзя изменить).
Железный гейт — предоставляет 2 набора слотов.
Гейт из адского кирпича — предоставляет 4 набора слотов.
Золотой гейт — предоставляет 8 наборов слотов.

<chapter name="Модификаторы гейтов"/>
Гейты можно модифицировать, чтобы получить слоты параметров:
Лазуритовое расширение — предоставляет 1 слот параметров каждому триггеру.
Кварцевое расширение — предоставляет 1 слот параметров каждому набору (сократит количество слотов гейта вдвое).
Алмазное расширение — предоставляет 3 слота параметров каждому набору (сократит количество слотов гейта вдвое).

<chapter name="Варианты гейтов"/>
Использование гейта И гарантирует, что <bold>все</bold> подключённые триггеры активны, прежде чем соединение с действиями станет активным.
Использование гейта ИЛИ гарантирует, что <bold>один или несколько</bold> подключённых триггеров активны, прежде чем соединение с действиями станет активным.

<chapter name="Триггеры"/>
Значки в левой части называются триггерами. Их перетаскивают в слоты в левой части интерфейса.
Разные триггеры используются для обнаружения разных вещей, и у каждого свои условия. Когда условие триггера выполнено, соседнее соединение становится активным.

<chapter name="Действия"/>
Значки в правой части называются действиями. Их перетаскивают в слоты в правой части интерфейса.
Разные действия приводят к выполнению разных задач. Когда соседнее соединение активно, подключённые действия выполняются.

<chapter name="Параметры"/>
Когда установлены слоты параметров, рядом с некоторыми триггерами/действиями становятся доступны дополнительные слоты.
Эти дополнительные слоты могут давать дополнительную функциональность, делая ваши триггеры/действия более конкретными.
В этих дополнительных слотах вы либо щёлкаете по слоту, чтобы открыть дополнительные варианты, либо помещаете в него предметы в качестве фильтра.

<chapter name="Соединения"/>
Линии посередине служат для соединения триггеров с действиями, чтобы они могли работать вместе.
По ним можно щёлкать, чтобы соединить/разъединить их и требовать нескольких триггеров/действий.
Когда триггер срабатывает, линия посередине загорается (лишь частично, если требуется несколько триггеров).
Когда линия загорается, выполняются подключённые к ней действия.
