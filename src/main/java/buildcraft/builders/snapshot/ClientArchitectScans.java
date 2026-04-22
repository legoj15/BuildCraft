package buildcraft.builders.snapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * Client-side state for the Architect Table "digitizing" effect. Each entry maps a block position
 * being scanned to its remaining display lifetime in ticks. The renderer draws a translucent
 * green cube at each tracked position with alpha proportional to the remaining lifetime, so the
 * cube fades out over ~2.5 seconds after a scan touches it.
 */
public enum ClientArchitectScans {
    INSTANCE;

    public static final int START_SCANNED_BLOCK_VALUE = 50;

    private final Map<BlockPos, Integer> scanned = new HashMap<>();

    public void onReceived(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            scanned.put(pos.immutable(), START_SCANNED_BLOCK_VALUE);
        }
    }

    public void tick() {
        scanned.entrySet().removeIf(entry -> {
            int next = entry.getValue() - 1;
            entry.setValue(next);
            return next <= 0;
        });
    }

    public Map<BlockPos, Integer> getScanned() {
        return scanned;
    }

    public void clear() {
        scanned.clear();
    }
}
