package buildcraft.lib.misc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.core.BCLog;

public class AdvancementUtil {
    private static final Set<Identifier> UNKNOWN_ADVANCEMENTS = new HashSet<>();

    public static void unlockAdvancement(Player player, Identifier advancementName) {
        unlockAdvancement(player, advancementName, "code_trigger");
    }

    /**
     * Awards {@code player}. A fake player (a stripes pipe or robot using an item, any machine acting for its owner)
     * is never awarded through its own tracker — on 1.21.1 and 26.2 NeoForge gives fake players a no-op one, so the
     * owner never got anything — but routed to the real player with its UUID: the owner when online, nobody
     * otherwise. See {@link #unlockAdvancement(UUID, Level, Identifier, String)}.
     */
    public static void unlockAdvancement(Player player, Identifier advancementName, String criterionName) {
        if (player instanceof FakePlayer) {
            unlockAdvancement(player.getUUID(), player.level(), advancementName, criterionName);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            ServerAdvancementManager advancementManager = server.getAdvancements();
            var holder = advancementManager.get(advancementName);
            if (holder != null) {
                // never assume the advancement exists, we create them but they are removable by datapacks
                PlayerAdvancements tracker = serverPlayer.getAdvancements();
                tracker.award(holder, criterionName);
            } else if (UNKNOWN_ADVANCEMENTS.add(advancementName)) {
                BCLog.logger.warn("[lib.advancement] Attempted to trigger undefined advancement: " + advancementName);
            }
        }
    }

    /**
     * Awards the real, connected player with this UUID — the path for a machine acting for its owner, and the one
     * the {@link Player}-typed overload routes every {@link FakePlayer} through. A fake player's own
     * {@code getAdvancements()} is exactly the surface NeoForge has been changing (a no-op tracker on 1.21.1 and
     * 26.2; on 1.21.10 - 26.1.x a tracker shared by UUID with the real owner). Looking the connected
     * {@link ServerPlayer} up by UUID is correct on every line: it awards the owner if they're online and cleanly
     * no-ops (returns {@code false}) if not.
     */
    public static boolean unlockAdvancement(UUID playerId, Level level, Identifier advancementName) {
        return unlockAdvancement(playerId, level, advancementName, "code_trigger");
    }

    public static boolean unlockAdvancement(UUID playerId, Level level, Identifier advancementName, String criterionName) {
        if (level.isClientSide()) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        // Never a fake player (they are not in the player list), which also keeps the two overloads from recursing.
        if (player != null && !(player instanceof FakePlayer)) {
            unlockAdvancement(player, advancementName, criterionName);
            return true;
        }
        return false;
    }
}
