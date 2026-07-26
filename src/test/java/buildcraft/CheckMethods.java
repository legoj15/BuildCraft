/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

public class CheckMethods { 
    @Test
    public void dumpMethods() { 
        for(Method m : BlockBehaviour.class.getDeclaredMethods()) { 
            if (m.getParameterCount() == 5 && m.getParameterTypes()[4] == boolean.class) {
                System.out.println("FOUND TARGET METHOD: " + m.getName() + " with params: " + java.util.Arrays.toString(m.getParameterTypes())); 
            }
        } 
    } 
}
