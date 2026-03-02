package buildcraft.core.tile;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;

/** Implemented by TileSpringOil in the energy module. */
public interface ITileOilSpring {
    /** Pumps should call this when they pump oil from this spring. */
    void onPumpOil(GameProfile pumpOwner, BlockPos oilPos);
}
