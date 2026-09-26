package buildcraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;

import net.minecraft.world.phys.Vec3;

public class TestHelper {
    public static void assertVec3Equals(Vec3 expected, Vec3 centerExact2) {
        if (expected.distanceTo(centerExact2) > 1e-12) {
            Assertions.fail(centerExact2 + " was not equal to expected " + expected);
        }
    }

    /** Walks up from the working directory to the repo root. Unit tests run from a Stonecutter node
     *  directory ({@code versions/<id>}), not the root, so the location cannot be assumed. */
    public static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/buildcraft"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root from " + Paths.get("").toAbsolutePath());
    }
}
