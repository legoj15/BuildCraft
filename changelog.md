###### Changes since 2026.1.0:

- Added support for Minecraft 26.2 (Vulkan-capable rendering, NeoForge 26.2, JEI 30).
- The paintbrush can now recolour glazed terracotta, candles, and beds.
- Searing-hot fluids now vent rising steam from their surface. Toggle with the `searingFluidSteam` config option.
- Power Mode `DISPLAY_RF` now actually shows machine power readouts and tooltips in FE/RF (converted from MJ at the `mjRfConversionAmount` ratio) instead of MJ — previously it behaved identically to `MJ_AUTOCONVERT_RF`. This includes the Creative Engine's output-cycle message, which now follows your power mode and Flow Rate Display setting. Clarified the `powerMode` config description.
