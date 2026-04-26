package buildcraft.lib.inventory;



import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.inventory.IItemTransactor;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.tile.item.StackInsertionFunction;

public class ItemTransactorTester {
    private static void assertTrue(boolean val) {
        if (!val) throw new IllegalStateException("Assertion failed!");
    }

    private static void assertFalse(boolean val) {
        if (val) throw new IllegalStateException("Assertion failed!");
    }
    public static void testSimpleMoving(GameTestHelper helper) {
        IItemTransactor trans = new ItemHandlerSimple(2, null);

        System.out.println("Assertion 1: Is newly created trans empty?");
        assertTrue(trans.extract(null, 1, 1, false).isEmpty());

        ItemStack insert = new ItemStack(Items.APPLE);
        ItemStack leftOver = trans.insert(insert.copy(), false, false);

        System.out.println("Assertion 2: Is leftOver empty after insert?");
        assertTrue(leftOver.isEmpty());

        ItemStack extracted = trans.extract(null, 1, 1, false);

        System.out.println("Assertion 3: Extracted match inserted?");
        System.out.println("Insert Item: " + insert.getItem() + " Components: " + insert.getComponents());
        System.out.println("Extract Item: " + extracted.getItem() + " Components: " + extracted.getComponents());
        assertTrue(ItemStack.isSameItemSameComponents(insert, extracted));

        extracted = trans.extract(null, 1, 1, false);

        System.out.println("Assertion 4: Is empty after second extract?");
        assertTrue(extracted.isEmpty());
        helper.succeed();
    }

    public static void testLimitedInventory(GameTestHelper helper) {
        IItemTransactor limited = new ItemHandlerSimple(2, (i, s) -> true, StackInsertionFunction.getInsertionFunction(4), null);

        ItemStack toInsert = new ItemStack(Items.APPLE, 9);
        ItemStack toInsertCopy = toInsert.copy();
        ItemStack supposedLeftOver = new ItemStack(Items.APPLE);

        ItemStack actuallyLeftOver = limited.insert(toInsert, false, false);

        assertTrue(ItemStack.isSameItemSameComponents(toInsert, toInsertCopy));
        assertTrue(ItemStack.isSameItemSameComponents(supposedLeftOver, actuallyLeftOver));
        helper.succeed();
    }

    /** Regression: {@link ItemHandlerSimple#serializeNBT()}/{@link ItemHandlerSimple#deserializeNBT}
     * must preserve the full {@code DataComponentPatch} through a save/load cycle. The pre-fix
     * id+count format silently dropped enchantments, custom names, damage, dyed colors, etc., so
     * any tile inventory routed through ItemHandlerSimple (Builder, Filler, Architect, Auto
     * Workbench, Assembly Table, Chute, Filtered Buffer, pipe filters, ...) blanked components
     * on world reload. */
    public static void testComponentRoundTrip(GameTestHelper helper) {
        ItemHandlerSimple original = new ItemHandlerSimple(4);

        ItemStack damagedPick = new ItemStack(Items.DIAMOND_PICKAXE);
        damagedPick.setDamageValue(123);

        ItemStack namedSword = new ItemStack(Items.IRON_SWORD);
        namedSword.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));

        ItemStack enchantedPick = new ItemStack(Items.DIAMOND_PICKAXE);
        net.minecraft.core.RegistryAccess registries = helper.getLevel().registryAccess();
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> fortune =
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        enchantedPick.enchant(fortune, 3);

        ItemStack plainStack = new ItemStack(Items.APPLE, 7);

        original.setStackInSlot(0, damagedPick.copy());
        original.setStackInSlot(1, namedSword.copy());
        original.setStackInSlot(2, enchantedPick.copy());
        original.setStackInSlot(3, plainStack.copy());

        CompoundTag tag = original.serializeNBT();

        ItemHandlerSimple roundTripped = new ItemHandlerSimple(4);
        roundTripped.deserializeNBT(tag);

        assertTrue(ItemStack.isSameItemSameComponents(damagedPick, roundTripped.getStackInSlot(0)));
        assertTrue(roundTripped.getStackInSlot(0).getDamageValue() == 123);
        assertTrue(ItemStack.isSameItemSameComponents(namedSword, roundTripped.getStackInSlot(1)));
        assertTrue(ItemStack.isSameItemSameComponents(enchantedPick, roundTripped.getStackInSlot(2)));
        assertTrue(ItemStack.isSameItemSameComponents(plainStack, roundTripped.getStackInSlot(3)));
        assertTrue(roundTripped.getStackInSlot(3).getCount() == 7);

        helper.succeed();
    }

    /** Existing world saves stored stacks under {@code id} + {@code count} only. The deserializer
     * must still read those entries -- losing pre-fix inventories on first load would be worse
     * than the original component-drop bug. */
    public static void testLegacyIdCountFallback(GameTestHelper helper) {
        CompoundTag legacyTag = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("id", BuiltInRegistries.ITEM.getKey(Items.GOLD_INGOT).toString());
        entry.putInt("count", 5);
        list.add(entry);
        list.add(new CompoundTag());
        legacyTag.put("items", list);

        ItemHandlerSimple handler = new ItemHandlerSimple(2);
        handler.deserializeNBT(legacyTag);

        ItemStack loaded = handler.getStackInSlot(0);
        assertTrue(loaded.is(Items.GOLD_INGOT));
        assertTrue(loaded.getCount() == 5);
        assertTrue(handler.getStackInSlot(1).isEmpty());

        helper.succeed();
    }
}
