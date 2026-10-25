###### Changes since 2026.2.1:

- Fuel temperature now matters in the Combustion Engine: hot fuel gives 25% more power than cool, searing fuel 25% less and runs the engine hot enough to need extra coolant. Hot and searing fuels used to be refused outright.
- Oil now spawns in the Nether, always searing: slicks on the lava sea, buried pockets and small spouts, with rare infinite springs in Crimson Forests. Existing config files still list the Nether as excluded; remove it from `excludedDimensions` to turn it on.
- Gaseous fuels explode if spilled in the Nether.
- A Pump on top of a Mining Well drains the lava or oil that stopped the well so the well digs on, drawing on the well's power when its own runs out. Its status light turns blue to show the link.
- Lasers work underwater: water no longer blocks a laser's line of sight, and a beam fired through water fizzes with bubbles.
- Lasers, laser tables, engines, tanks, chutes, distillers, heat exchangers and quarry frames can be waterlogged, so they no longer sit in a pocket of air when placed underwater.
- Pipes sound like what they're made of: wooden pipes creak like scaffolding, iron, gold and stripes pipes ring like copper grates, emzuli pipes chime like amethyst, stone, obsidian, void and structure pipes sound like stone, and sandstone and clay pipes each have their own. Diamond, wooden diamond, daizuli, lapis and quartz pipes keep the classic metal sound. Redstone engines now sound wooden, Stirling engines stony, markers like a higher-pitched torch and quarry frames like metal grates.
- Looking at a marker laser shows its length in blocks under the crosshair, including the faint preview lines while holding a Marker Connector.
- The Filler has a Preview toggle (the eye button on its screen). On, it draws see-through cubes over every spot its pattern still has to fill, for everyone near it, and hides them while it is placing or breaking blocks. It replaces the Filler Planner: the Filler Planner item and the Volume Box addon spots are gone, so Filler Planners in inventories and on Volume Boxes disappear; Fillers that were driven by a Filler Planner in an older world take over the Volume Box, pattern and inverted setting the planner held, the first time each loads.
- Changing a Filler's pattern, parameters, invert toggle or box now reaches other players watching it straight away.
- The Zone Planner's map now shows terrain past your render distance: anywhere in its survey area that someone has visited is drawn from what the server remembers, without loading any chunks.
- Facades cast the same soft corner shadows as the blocks they copy, so a wall or floor of facades blends with real blocks instead of each facade looking flat-lit. Hollow facades stay unshaded.
