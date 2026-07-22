###### Changes since 2026.2.0-br2:

* Spectators can now view Assembly Table, Advanced Crafting Table, and Integration Table interfaces read-only, matching every other BuildCraft machine and vanilla containers.
* Added a Simplified Chinese (`zh_cn`) translation, contributed by beizhou1 (#28). Covers 493 of 971 translatable strings; the rest fall back to English until translated.
* A large amount of text that was hardcoded in English now goes through the language files, so translations can actually reach it: every coloured pipe, pipe wire, paintbrush and lens name; all energy and fluid units in machine readouts, ledgers and pipe tooltips; the guide book's contents spread, category titles, placeholder pages and recipe listings; the Builder's progress counters; and the Schematic, Blueprint and Fragile Fluid Container messages. Colour names now use Minecraft's own translations, so they are already localised everywhere.
* The Filler and Filler Planner now explain their pattern slot and pattern parameters in the help ledger — previously the two most important controls on those screens were the only ones with no help. The Filler Planner also regained its help ledger, which had been switched off by mistake.
* With RF unit names enabled, the FE Engine's upgrade help, the FE limiter pipe's gate actions, and the guide book's pipe chapter now say "RF" like the rest of the interface instead of remaining "FE".
* The Replacer's help now states that the single-block schematics are not consumed, which differs from older BuildCraft versions.
* The help ledger shows a warning icon when a screen has no help available, instead of opening an empty panel that looked identical to a working one.
* The Fragile Fluid Container's contents now follow your unit display settings rather than always showing raw millibuckets.
* Fixed a Simplified Chinese tooltip that displayed a literal `\n` instead of a line break, and removed translations of two filter buttons that still described mechanics from Minecraft 1.12.
