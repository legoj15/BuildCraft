package buildcraft.lib.tile;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;

public abstract class TileMarker<C extends MarkerConnection<C>> extends BlockEntity implements IDebuggable {
    /** Set to true when this BE is being removed due to chunk unload, not block breaking. */
    private boolean chunkUnloading = false;

    public TileMarker(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- NBT ---

    // Platform bridge — TileMarker extends BlockEntity directly (not TileBC_Neptune), so it carries
    // its own copy of the load/save signature directive (see TileBC_Neptune for the rationale). Subclasses
    // override writeData/readData (NOT the platform methods).
    //? if >=1.21.10 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeData(new BCValueOutput(output));
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readData(new BCValueInput(input));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(new BCValueOutput(tag));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(new BCValueInput(tag));
    }*/
    //?}

    protected void writeData(BCValueOutput output) {
    }

    protected void readData(BCValueInput input) {
    }

    public abstract MarkerCache<? extends MarkerSubCache<C>> getCache();

    public MarkerSubCache<C> getLocalCache() {
        return getCache().getSubCache(level);
    }

    /**
     * @return True if this has lasers being emitted. Activates the surrounding
     *         "glow"
     *         parts for the block model.
     */
    public abstract boolean isActiveForRender();

    public C getCurrentConnection() {
        if (level == null)
            return null;
        return getLocalCache().getConnection(getBlockPos());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            getLocalCache().loadMarker(getBlockPos(), this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
        if (level != null && !level.isClientSide()) {
            // Just unload the tile reference — keep the position and connections in the cache
            getLocalCache().unloadMarker(getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide() && !chunkUnloading) {
            // Block was actually broken — fully remove from cache and tear down connections
            getLocalCache().removeMarker(getBlockPos());
        }
    }

    protected void disconnectFromOthers() {
        C currentConnection = getCurrentConnection();
        if (currentConnection != null) {
            currentConnection.removeMarker(getBlockPos());
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        if (level == null)
            return;
        C current = getCurrentConnection();
        MarkerSubCache<C> cache = getLocalCache();
        left.add("Exists = " + (cache.getMarker(getBlockPos()) == this));
        if (current == null) {
            left.add("Connection = null");
        } else {
            left.add("Connection:");
            current.getDebugInfo(getBlockPos(), left);
        }
    }
}
