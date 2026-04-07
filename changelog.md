###### Changes since 26.1 Beta release 4:
- Fixed GuiBC8 rendering double-titles by suppressing vanilla AbstractContainerScreen extractLabels
- Fixed MJ Dynamo and FE Engine GUI translation strings, height cutoffs, and label offsets
- Fixed translation strings and cut-off rendering in ScreenDynamoMJ and ScreenEngineFE by reverting SIZE_Y parameters and targeting correct localization keys
- Restored upgrade tooltip functionality and upgrade overlay to ScreenDynamoMJ and ScreenEngineFE
- Fixed upgrade slot misalignment and restored missing upgrade_types translation string
- Adjusted upgrade tooltips to match 1.12.2 'Redstone Flux per second' phrasing exactly
- Fixed empty upgrade tooltips by explicitly invoking the deferred upgrade map initialization routines inside the GUI rendering loop
- Restored missing energy tank tooltips in ScreenDynamoMJ and ScreenEngineFE
