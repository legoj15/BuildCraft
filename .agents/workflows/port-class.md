---
description: How to port a class from BuildCraft 1.12 (Forge) to NeoForge 26.1.1
---

# Port a Class to NeoForge 26.1.1

Follow this checklist when porting any class from the old BuildCraft 1.12 codebase to NeoForge 26.1.1.

## Pre-Port

1. **Identify the source file** in the old codebase (e.g. `src_old_license/` or the 1.12 jar).
2. **Determine which module it belongs to**: `buildcraft-api`, `buildcraft-lib`, or `buildcraft-core`.
3. **Check dependencies**: Does this class depend on other unported classes? If so, port or stub those first.

## Package Declaration

4. **Set the correct package** based on the target module:
   - `buildcraft-api` → `package buildcraft.api.*`
   - `buildcraft-lib` → `package buildcraft.lib.*`
   - `buildcraft-core` → `package buildcraft.core.*`
5. **Never** use a package from another module (e.g. don't put `package buildcraft.lib.foo` in a file inside `buildcraft-core`). This causes a **split-package** error at runtime.
6. Run `./gradlew checkSplitPackages` to verify.

## Constructor & Registration (Items)

7. **Accept `Item.Properties`** from the registrar — do NOT create your own `new Item.Properties()` inside the constructor.
8. **Use `ITEMS.registerItem()`** (not `ITEMS.register()`) so the item ID is injected into Properties automatically.
9. **Pass extra properties** (e.g. `.stacksTo(1)`) via a lambda as the third argument of `registerItem()`. Do NOT use `new Item.Properties()`, as this triggers a registry freeze error on startup if properties (like armor materials) try to resolve against the registry too early.
10. Example:
```java
// In BCCoreItems.java:
public static final DeferredItem<MyItem> MY_ITEM = ITEMS.registerItem("my_item",
        MyItem::new, props -> props.stacksTo(1));

// In MyItem.java:
public MyItem(Item.Properties properties) {
    super(properties); // Do NOT modify properties here
}
```

## Constructor & Registration (Blocks)

11. **Accept `BlockBehaviour.Properties`** from the registrar.
12. **Use `BLOCKS.registerBlock()`** (not `BLOCKS.register()`).
13. Register a corresponding `BlockItem` using `ITEMS.registerSimpleBlockItem()`.

## API Changes

14. **`ResourceLocation`** → use `Identifier.parse("namespace:path")` in 1.21.11.
15. **`onItemUse`** → `useOn(UseOnContext context)`.
16. **`swingArm`** → `swing(hand)`.
17. **`setMaxStackSize`** → `properties.stacksTo(n)`.
18. **`doesSneakBypassUse`** → override still exists, signature now uses `LevelReader`.
19. **`INBTSerializable`** → removed in NeoForge 1.21.x; use custom serialization.

## Metadata

20. **`neoforge.mods.toml`** must exist in `META-INF/` of every module's resources directory:
    - `buildcraft-api`: `buildcraft-api/resources/META-INF/neoforge.mods.toml`
    - `buildcraft-lib`: `buildcraft-lib/src/main/resources/META-INF/neoforge.mods.toml`
    - `buildcraft-core`: `buildcraft-core/src/main/resources/META-INF/neoforge.mods.toml`
21. If the new class introduces a cross-module dependency, add it to the `[[dependencies.*]]` section.

## Assets & Localization (REQUIRED — do NOT skip)

Every new block or item needs ALL of the following assets. Missing any one of these will cause invisible items, purple/black checkerboard textures, or untranslated names in-game.

### For every new Item:
22. **`items/<id>.json`** — item definition wrapper (NeoForge 1.21.11 requirement):
    ```json
    { "model": { "type": "minecraft:model", "model": "<modid>:item/<id>" } }
    ```
23. **`models/item/<id>.json`** — the actual item model (parent + textures).
24. **`textures/item/<id>.png`** — the item texture (singular `item/`, not `items/`).
25. **`lang/en_us.json`** — add `"item.<modid>.<id>": "Display Name"`.

### For every new Block:
26. **`items/<id>.json`** — item definition wrapper for the BlockItem:
    ```json
    { "model": { "type": "minecraft:model", "model": "<modid>:item/<id>" } }
    ```
27. **`models/item/<id>.json`** — item model (usually `"parent": "<modid>:block/<id>"`).
28. **`blockstates/<id>.json`** — blockstate JSON mapping properties to models.
29. **`models/block/<id>.json`** — block model (parent + textures).
30. **`textures/block/<id>/`** — block texture PNG(s) (singular `block/`, not `blocks/`).
31. **`lang/en_us.json`** — add `"block.<modid>.<id>": "Display Name"`.

### For blocks/items with GUIs:
32. **`textures/gui/<name>.png`** — GUI background texture.
33. **`lang/en_us.json`** — add GUI title key (e.g. `"tile.<modid>.<id>.name": "Display Name"`).

### For blocks/items in creative tab:
34. Add `event.accept(BCXxxBlocks.<ID>)` or `event.accept(BCXxxItems.<ID>)` in the module's `buildCreativeTabContents` handler.

## Compile & Test

// turbo
35. Run `./gradlew checkSplitPackages` to verify no package conflicts.
// turbo
36. Run `./gradlew build -x test` to compile.
// turbo
37. Run `./gradlew :buildcraft-all:runClient` to launch the game and verify the class loads.
38. Check the game log for errors related to the new class.