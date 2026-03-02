---
description: How to port a class from BuildCraft 1.12 (Forge) to NeoForge 1.21.11
---

# Port a Class to NeoForge 1.21.11

Follow this checklist when porting any class from the old BuildCraft 1.12 codebase to NeoForge 1.21.11.

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

## Compile & Test

// turbo
22. Run `./gradlew checkSplitPackages` to verify no package conflicts.
// turbo
23. Run `./gradlew build -x test` to compile.
// turbo
24. Run `./gradlew :buildcraft-core:runClient` to launch the game and verify the class loads.
25. Check the game log for errors related to the new class.
