/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics;

import net.minecraft.world.entity.Entity;

/**
 * Predicate identifying which entities a robot AI may interact with (e.g. "a mob, or an angry wolf" for the
 * knight, "any animal" for the butcher). Ported from 7.1.x {@code buildcraft.core.lib.utils.IEntityFilter}.
 */
@FunctionalInterface
public interface IEntityFilter {

    boolean matches(Entity entity);
}
