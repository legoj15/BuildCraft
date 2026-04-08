/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.Random;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Utilities based around more complex (but common) usages of {@link Random}. */
public class RandUtil {
    /** Creates a {@link Random} instance for a specific generator, for the specified chunk, in the specified world.
     *
     * @param level The level to generate for.
     * @param chunkX The chunk X co-ord to generate for.
     * @param chunkZ The chunk Z co-ord to generate for.
     * @param magicNumber The magic number, specific to the generator. Each different generator that calls this should
     *            have a different number, so that different generators don't start by generating structures in the same
     *            place.
     * @return A {@link Random} instance that starts off with the same seed given the same arguments. */
    public static Random createRandomForChunk(Level level, int chunkX, int chunkZ, long magicNumber) {
        long worldSeed = level instanceof ServerLevel sl ? sl.getSeed() : 0L;
        return createRandomForChunk(worldSeed, chunkX, chunkZ, magicNumber);
    }

    /** Creates a {@link Random} instance for a specific generator, for the specified chunk, for a given world seed.
     *
     * @param worldSeed The seed of a world to generate for.
     * @param chunkX The chunk X co-ord to generate for.
     * @param chunkZ The chunk Z co-ord to generate for.
     * @param magicNumber The magic number, specific to the generator.
     * @return A {@link Random} instance that starts off with the same seed given the same arguments. */
    public static Random createRandomForChunk(long worldSeed, int chunkX, int chunkZ, long magicNumber) {
        // Ensure we have the same seed for the same chunk
        // (this is similar to the code that calls IWorldGenerator.generate)
        Random worldRandom = new Random(worldSeed);
        long xSeed = worldRandom.nextLong() >> 2 + 1L;
        long zSeed = worldRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ worldSeed;
        // XOR our own number so that we differ from other generators
        chunkSeed ^= magicNumber;
        return new Random(chunkSeed);
    }
}
