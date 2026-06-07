/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Version-neutral block/item registration. The property-carrying {@code registerBlock}/{@code registerItem}
 * overloads diverged across the 1.21.2 cliff: 1.21.10+ <i>defer</i> the {@code Properties} (a {@code Supplier}
 * for blocks, a {@code UnaryOperator} for items) so the registry can inject the registration ID before the
 * properties are built — {@code Item.Properties} has required an ID since 1.21.2 — whereas 1.21.1 takes an
 * <i>eager</i> {@code Properties}. There is no call form valid on both, so BuildCraft's {@code BC*Items}/
 * {@code BC*Blocks} route through here. The {@code >=1.21.10} branch is exactly the call BuildCraft already
 * makes today, so the released nodes are unchanged. The 2-arg (no-properties) forms exist on every node and
 * are plain passthroughs, kept here only so the call sites stay uniform.
 */
public class RegistrationUtilBC {
    public static <B extends Block> DeferredBlock<B> registerBlock(
            DeferredRegister.Blocks reg, String name,
            Function<BlockBehaviour.Properties, ? extends B> factory,
            Supplier<BlockBehaviour.Properties> props) {
        //? if >=1.21.10 {
        return reg.registerBlock(name, factory, props);
        //?} else {
        /*return reg.registerBlock(name, factory, props.get());*/
        //?}
    }

    public static <B extends Block> DeferredBlock<B> registerBlock(
            DeferredRegister.Blocks reg, String name,
            Function<BlockBehaviour.Properties, ? extends B> factory) {
        return reg.registerBlock(name, factory);
    }

    public static <I extends Item> DeferredItem<I> registerItem(
            DeferredRegister.Items reg, String name,
            Function<Item.Properties, ? extends I> factory,
            UnaryOperator<Item.Properties> props) {
        //? if >=1.21.10 {
        return reg.registerItem(name, factory, props);
        //?} else {
        /*return reg.registerItem(name, factory, props.apply(new Item.Properties()));*/
        //?}
    }

    public static <I extends Item> DeferredItem<I> registerItem(
            DeferredRegister.Items reg, String name,
            Function<Item.Properties, ? extends I> factory,
            Supplier<Item.Properties> props) {
        //? if >=1.21.10 {
        return reg.registerItem(name, factory, props);
        //?} else {
        /*return reg.registerItem(name, factory, props.get());*/
        //?}
    }

    public static <I extends Item> DeferredItem<I> registerItem(
            DeferredRegister.Items reg, String name,
            Function<Item.Properties, ? extends I> factory) {
        return reg.registerItem(name, factory);
    }
}
