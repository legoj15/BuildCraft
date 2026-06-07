package buildcraft.energy.tile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.core.tile.ITileOilSpring;
import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.GameProfileUtil;

/**
 * Block entity for oil springs (at bedrock level in large oil wells).
 * Tracks per-player pump progress.
 */
public class TileSpringOil extends BlockEntity implements ITileOilSpring {

    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:black_gold");

    private final Map<GameProfile, PlayerPumpInfo> pumpProgress = new ConcurrentHashMap<>();

    /** An approximation of the total number of oil source blocks in the oil spring.
     * Note: Should only be set by the generator and loadAdditional. */
    public int totalSources;

    public TileSpringOil(BlockPos pos, BlockState state) {
        super(BCEnergyBlockEntities.SPRING_OIL.get(), pos, state);
    }

    @Override
    public void onPumpOil(GameProfile profile, BlockPos oilPos) {
        if (profile == null) {
            return;
        }
        PlayerPumpInfo info = pumpProgress.computeIfAbsent(profile, PlayerPumpInfo::new);
        info.lastPumpTick = level.getGameTime();
        info.sourcesPumped++;

        if (info.sourcesPumped >= totalSources * 7 / 8) {
            if (oilPos.equals(getBlockPos().above())) {
                if (GameProfileUtil.getId(profile) != null) {
                    AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(profile), level, ADVANCEMENT);
                }
            }
        }
    }

    //? if >=1.21.10 {
    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BCValueInput in = new BCValueInput(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BCValueInput in = new BCValueInput(tag);*/
    //?}
        totalSources = in.getIntOr("totalSources", 0);
        int pumpCount = in.getIntOr("pumpCount", 0);
        for (int i = 0; i < pumpCount; i++) {
            String prefix = "pump_" + i + "_";
            String name = in.getStringOr(prefix + "name", "Unknown");
            String idStr = in.getStringOr(prefix + "id", "");
            UUID id = null;
            try {
                if (!idStr.isEmpty()) id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {}
            long tick = in.getLongOr(prefix + "tick", -1);
            int pumped = in.getIntOr(prefix + "pumped", 0);
            GameProfile gp = new GameProfile(id, name);
            PlayerPumpInfo info = new PlayerPumpInfo(gp);
            info.lastPumpTick = tick;
            info.sourcesPumped = pumped;
            pumpProgress.put(gp, info);
        }
    }

    //? if >=1.21.10 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BCValueOutput out = new BCValueOutput(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        BCValueOutput out = new BCValueOutput(tag);*/
    //?}
        out.putInt("totalSources", totalSources);
        out.putInt("pumpCount", pumpProgress.size());
        int i = 0;
        for (PlayerPumpInfo info : pumpProgress.values()) {
            String prefix = "pump_" + i + "_";
            if (GameProfileUtil.getName(info.profile) != null) {
                out.putString(prefix + "name", GameProfileUtil.getName(info.profile));
            }
            if (GameProfileUtil.getId(info.profile) != null) {
                out.putString(prefix + "id", GameProfileUtil.getId(info.profile).toString());
            }
            out.putLong(prefix + "tick", info.lastPumpTick);
            out.putInt(prefix + "pumped", info.sourcesPumped);
            i++;
        }
    }

    static class PlayerPumpInfo {
        final GameProfile profile;
        long lastPumpTick = -1;
        int sourcesPumped = 0;

        public PlayerPumpInfo(GameProfile profile) {
            this.profile = profile;
        }
    }
}
