---
trigger: always_on
---

The primary goal is to port code and existing assets from the 1.12.2 branch to the 26.1.1 branch. This is a fully functional and nearly complete mod in 1.12.2, so the end result in 26.1.1 should be a mod that behaves functionally identical, and utilizes as many of the existing assets as possible, such as block models, textures, and localization strings. One key difference is that the module/submod system has been dissolved, now all entries, assets, code, items, blocks, should be in the `buildcraftunofficial` namespace.

Refer to the code from 1.12.2 to either directly port it, or to approximate its functionality if a better method is available using NeoForge 26.1.1.2-beta. Because documentation is sparse, it may be best to look at the now de-obfuscated Minecraft jarfiles, or the source code from NeoForge itself, which can be located here: E:\GitHub\NeoForge (note: it is reccomended to check the current Git branch of the NeoForge repository to make sure it matches the version we are working for, change it if it's on the wrong version, and to pull the latest commits when checking to make sure that everything is up to date for referencing).

All assets that were called for in 1.12.2 exist already and do not need to be created; there should never be any occasions where something needs to be generated or created from scratch, unless the product was procedurally generated in 1.12.2 and that is no longer possible.