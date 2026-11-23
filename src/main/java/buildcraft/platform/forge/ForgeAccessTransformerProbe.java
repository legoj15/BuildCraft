/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

//? if >=1.21.11 {
import net.minecraft.world.entity.animal.equine.AbstractHorse;
//?} else {
/^import net.minecraft.world.entity.animal.horse.AbstractHorse;
^///?}

import buildcraft.api.core.BCLog;

/^*
 * Proves, at boot, that the access transformer in the jar (META-INF/accesstransformer.cfg) was applied by the Forge that
 * loaded it. Nothing else on the boot path does: a dev run gets the same file from the mavenizer, so it cannot show a
 * missing one; the members the file opens are read only when a feature uses them (a horse's inventory on a horse
 * capability query, a fluid's spread on a flowing fluid), so a jar whose transformer did not apply would boot clean and
 * fail later, where the member is first used.
 *
 * <p><b>The probe.</b> The transformer rewrites a class's access flags as the class loads, so the flags of the loaded
 * class are what it did. {@code AbstractHorse.inventory} is {@code protected} in vanilla, and Forge's own transformer
 * (checked in the universal jars of all five Forge lines) and its patches leave it that way, so BuildCraft's line is the
 * only thing that makes it public and the flag discriminates. It is a member every Forge node's file opens (the class is
 * {@code animal.equine} from 1.21.11 and {@code animal.horse} on 1.21.1; a dev run tolerates the line whose class is
 * absent, a production install's tolerance is still open). The read is a class literal and a reflective field lookup: the class is loaded, never initialised,
 * and the field's value is never read.
 *
 * <p><b>The two lines</b> the release test rig greps for ({@code testing/}, gitignored), both logged once on the
 * {@code BuildCraft} logger, on a server and a client alike:
 * <ul>
 * <li>INFO {@code BuildCraft access transformer applied}: the member is public. Another mod's transformer opening the
 * same member would also make it so; stripping BuildCraft's file is the negative test that tells them apart.</li>
 * <li>ERROR {@code BuildCraft access transformer NOT applied: AbstractHorse.inventory}: it is not, or the probe could
 * not read it (the same line, with {@code (cannot read it: <exception>)} after the member).</li>
 * </ul>
 * The probe never throws: a missing transformer already fails, loudly, where a member is used, and a diagnostic must not
 * be the reason the mod does not load.
 *^/
final class ForgeAccessTransformerProbe {

    /^* The member the probe reads, for the log line only: the same text on every Forge line. *^/
    private static final String MEMBER = "AbstractHorse.inventory";

    private ForgeAccessTransformerProbe() {}

    /^* Logs whether the access transformer's widening of {@link #MEMBER} is in force. Call it first in the mod's
     * constructor. *^/
    static void check() {
        try {
            Field inventory = AbstractHorse.class.getDeclaredField("inventory");
            if (Modifier.isPublic(inventory.getModifiers())) {
                BCLog.logger.info("BuildCraft access transformer applied");
            } else {
                BCLog.logger.error("BuildCraft access transformer NOT applied: {}", MEMBER);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BCLog.logger.error("BuildCraft access transformer NOT applied: {} (cannot read it: {})", MEMBER,
                    e.toString());
        }
    }
}
*///?}
