/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}
import net.minecraft.world.item.DyeColor;

import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryAttachments;
import buildcraft.lib.test.GoldenFixtures;
import buildcraft.platform.registry.BCAttachment;
import buildcraft.platform.registry.BCAttachmentData;
import buildcraft.transport.BCTransportAttachments;

/**
 * Golden save format of BuildCraft's player attachments (wire colours placed, pluggables placed, oil and fuel
 * production): the {@code neoforge:attachments} compound an entity writes, through NeoForge's own attachment holder,
 * so the platform attachment seam ({@code NeoForgeAttachmentServices}) is proven to write today's bytes. Also pins
 * that all three are flagged to be copied on death.
 *
 * <p>What breaks it: a changed attachment id, a changed payload key or type, or a serializer that no longer writes
 * through the payload's own save method.
 *
 * <p>Whether the attachments survive death is pinned as far as BuildCraft owns it: all three are flagged copy-on-death
 * (NeoForge's own copy then runs each through its serializer, which the round-trip test covers). The copy itself is
 * NeoForge's, and is not run here.
 */
public class AttachmentGoldenTest extends VanillaSetupBaseTester {

    /** A bare holder; {@code deserializeAttachments} is protected, so it is reached from a subclass. */
    private static final class TestHolder extends AttachmentHolder {
        CompoundTag save() {
            //? if >=1.21.10 {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, vanillaLookup());
            serializeAttachments(output);
            return output.buildResult();
            //?} else {
            /*return serializeAttachments(vanillaLookup());
            *///?}
        }

        void load(CompoundTag tag) {
            //? if >=1.21.10 {
            deserializeAttachments(TagValueInput.create(ProblemReporter.DISCARDING, vanillaLookup(), tag));
            //?} else {
            /*deserializeAttachments(vanillaLookup(), tag);
            *///?}
        }
    }

    private static <T extends BCAttachmentData> AttachmentType<T> type(BCAttachment<T> attachment) {
        return NeoForgeAttachmentServices.typeOf(attachment);
    }

    private static List<AttachmentType<?>> types() {
        return List.of(type(BCTransportAttachments.WIRE_COLOURS_PLACED),
                type(BCTransportAttachments.PLUGGABLES_PLACED),
                type(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION));
    }

    private static TestHolder filled() {
        TestHolder holder = new TestHolder();
        BCTransportAttachments.WireColoursPlaced wires =
                holder.getData(type(BCTransportAttachments.WIRE_COLOURS_PLACED));
        wires.markPlaced(DyeColor.RED);
        wires.markPlaced(DyeColor.BLUE);
        BCTransportAttachments.PluggablesPlaced plugs =
                holder.getData(type(BCTransportAttachments.PLUGGABLES_PLACED));
        plugs.markPlaced(BCTransportAttachments.PluggablesPlaced.Kind.GATE);
        plugs.markPlaced(BCTransportAttachments.PluggablesPlaced.Kind.TIMER);
        BCFactoryAttachments.OilAndFuelProduction oil =
                holder.getData(type(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION));
        oil.recordProduction(BCEnergyFluids.BASE_NAMES.get(0), 1234);
        oil.recordProduction(BCEnergyFluids.BASE_NAMES.get(2), 20_000);
        return holder;
    }

    @Test
    public void playerAttachmentsKeepTheirSaveFormat() {
        GoldenFixtures.assertGolden("player_attachments", filled().save());
    }

    @Test
    public void playerAttachmentsRoundTrip() {
        CompoundTag saved = filled().save();
        TestHolder loaded = new TestHolder();
        loaded.load(saved);
        Assertions.assertEquals(saved, loaded.save(), "a saved holder loads back to the same tag");
        Assertions.assertTrue(loaded.getData(type(BCTransportAttachments.WIRE_COLOURS_PLACED))
                .markPlaced(DyeColor.GREEN), "an unsaved colour is still unplaced after the load");
        Assertions.assertFalse(loaded.getData(type(BCTransportAttachments.WIRE_COLOURS_PLACED))
                .markPlaced(DyeColor.RED), "a saved colour is placed after the load");
        Assertions.assertEquals(1234, loaded.getData(type(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION))
                .get(BCEnergyFluids.BASE_NAMES.get(0)), "a production counter survives the load");
    }

    @Test
    public void playerAttachmentsAreFlaggedCopyOnDeath() throws ReflectiveOperationException {
        Field copyOnDeath = AttachmentType.class.getDeclaredField("copyOnDeath");
        copyOnDeath.setAccessible(true);
        for (AttachmentType<?> type : types()) {
            Assertions.assertTrue(copyOnDeath.getBoolean(type), type + " must be copied on death");
        }
    }
}
