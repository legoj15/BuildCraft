###### Changes since 2026.2.1:

- Fuel temperature now matters in the Combustion Engine: hot fuel gives 25% more power than cool, searing fuel 25% less and runs the engine hot enough to need extra coolant. Hot and searing fuels used to be refused outright.
- Oil now spawns in the Nether, always searing: slicks on the lava sea, buried pockets and small spouts, with rare infinite springs in Crimson Forests. Existing config files still list the Nether as excluded; remove it from `excludedDimensions` to turn it on.
- Gaseous fuels explode if spilled in the Nether.
- Lasers work underwater: water no longer blocks a laser's line of sight, and a beam fired through water fizzes with bubbles.
- Lasers, laser tables, engines, tanks, chutes, distillers, heat exchangers and quarry frames can be waterlogged, so they no longer sit in a pocket of air when placed underwater.
- Pipes sound like what they're made of: wooden pipes creak like scaffolding, iron, gold and stripes pipes ring like copper grates, emzuli pipes chime like amethyst, stone, obsidian, void and structure pipes sound like stone, and sandstone and clay pipes each have their own. Diamond, wooden diamond, daizuli, lapis and quartz pipes keep the classic metal sound. Redstone engines now sound wooden, Stirling engines stony, markers like a higher-pitched torch and quarry frames like metal grates.
- Looking at a marker laser shows its length in blocks under the crosshair, including the faint preview lines while holding a Marker Wrench.
- Wrenches come in three tiers. The Wrench is now the Basic Wrench. The Marker Connector is now the Marker Wrench and also works as a wrench; aiming at a marker laser or a volume box still links or edits it instead of turning the block behind it. The new Volume Wrench (diamond chipset, diamond gear and a Marker Wrench) is the only tool that can drag a volume box's corners.
- A Filler or Architect Table placed against survival markers now keeps their area as a volume box: the Volume Wrench can resize it (up to the marker range on each side), resizing restarts the machine's job, and the box disappears when the machine is broken. Boxes from the creative Volume Box stay as before.
