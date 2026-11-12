<lore>
Насест для робота, прикрученный к боку трубы. От того, к какой трубе его прикрутить, зависит, что робот сможет там делать.
</lore>
<no_lore>
Док-станция — это модуль трубы, к которому пристыковываются роботы. Труба, на которой она установлена, и гейт на этой трубе вместе решают, что может делать пристыкованный робот: подзаряжаться, забирать и доставлять предметы и жидкости или просто отдыхать.
</no_lore>
<chapter name="Установка"/>
Щёлкните правой кнопкой по любой грани трубы, чтобы установить станцию на неё. Затем щёлкните правой кнопкой по станции запрограммированным <link inline="buildcraftunofficial:item/robot"/>, чтобы поставить робота: эта станция становится домом робота, и робот <bold>соединён</bold> с ней. Перед домашней станцией робота должны находиться два свободных блока. Присядьте и щёлкните по роботу гаечным ключом, чтобы забрать его (робот с инструментом сначала отдаёт инструмент).
<chapter name="Что решает труба"/>
На кинетической (энергетической) трубе станция подзаряжает пристыкованного робота от энергии трубы. На <bold>Деревянной транспортной трубе</bold> станция может выдавать предметы из инвентаря, на который направлена сторона извлечения трубы, а на Деревянной жидкостной трубе — так же выдавать жидкость. На любой транспортной трубе станция может принимать груз от робота и проталкивать его в трубу. Станция на булыжниковой трубе, к примеру, может только принимать доставки.
<chapter name="Что решает гейт"/>
Для подзарядки гейт не нужен, но ни одна из описанных выше передач предметов и жидкостей не происходит без разрешения гейта на трубе станции. Действия станции:
<link to="buildcraftunofficial:action/station_provide_items"/>
<link to="buildcraftunofficial:action/station_accept_items"/>
<link to="buildcraftunofficial:action/station_provide_fluids"/>
<link to="buildcraftunofficial:action/station_accept_fluids"/>
<link to="buildcraftunofficial:action/station_request_items"/>
<link to="buildcraftunofficial:action/station_request_needed_items"/>
<link to="buildcraftunofficial:action/station_forbid_robot"/>
<link to="buildcraftunofficial:action/station_force_robot"/>
«Запретить робота» и «Принудить робота» принимают в качестве параметра тип робота (щёлкните по слоту запрограммированным роботом или пустой рукой, чтобы перебирать платы) и, соответственно, не подпускают к станции роботов такого типа или резервируют её для них. «Запросить предметы» запрашивает предмет у пролетающих мимо роботов-разносчиков — как однослотовый <link inline="buildcraftunofficial:block/requester"/>. Гейт также может считывать состояние станции:
<link to="buildcraftunofficial:trigger/robot_in_station"/>
<link to="buildcraftunofficial:trigger/station_linked"/>
<link to="buildcraftunofficial:trigger/station_reserved"/>
<link to="buildcraftunofficial:trigger/robot_sleep"/>
А приказы, которые домашняя станция отдаёт своему роботу, — это действия робота:
<link to="buildcraftunofficial:action/robot_work_in_area"/>
<link to="buildcraftunofficial:action/robot_goto_station"/>
<link to="buildcraftunofficial:action/robot_wake_up"/>
<link to="buildcraftunofficial:action/robot_filter"/>
<link to="buildcraftunofficial:action/robot_filter_tool"/>
<recipes_usages stack="buildcraftunofficial:robot_station"/>
<chapter name="См. также"/>
<link to="buildcraftunofficial:concept/robots"/>
