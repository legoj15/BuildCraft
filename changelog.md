###### Changes since 2026.2.0-br1:
- Fixed a crash when opening certain guide pages. Thank you for your crash log, [Ryk7039](https://github.com/Ryk7039)!
- Machines now update chunks a lot less often (this should improve performance)
- Updated code relating to how spectators can view machine GUIs
- Auto Workbench now correctly accepts FE when the BuildCraft config is set for machines to use FE
- RF/FE and MJ pipes now share the same rendering code, so any visual bugs that were exclusive to RF/FE pipes should be gone
- Editing a volume defined by a volume box or land markers now only updates that volume instead of every single one in the loaded dimension
- Items ejected from pipes into an inventory now top up partially-filled stacks before starting new ones, matching every other BuildCraft item output.
- Made the Oil generation code far more sensible; fixes multiple sources of freezes/server hangs on 1.21.1. Thank you for investigation, [MapperTV](https://github.com/MapperTV)!
- **Updated the NeoForge minimums:** 1.21.1 is now 21.1.151+, 1.21.10 is now 21.10.63+