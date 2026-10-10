package art.arcane.iris.engine.framework;

import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * One of the platform's own starts before the host publishes it, as the platform code sees it: its structure, start
 * chunk, the box around its pieces in world heights (every bound inclusive), whether the platform generates it in an
 * underground step, whether the host aligns it itself (an ocean monument to the sea level, a desert or jungle temple
 * to the terrain), the world's bottom height and the terrain's surface height per column in world heights.
 */
public record NativeStartPlacement(
        String structureKey,
        int chunkX,
        int chunkZ,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean underground,
        boolean aligned,
        int bottomY,
        IntBinaryOperator surfaceY
) {
    public NativeStartPlacement {
        Objects.requireNonNull(structureKey, "structureKey");
        Objects.requireNonNull(surfaceY, "surfaceY");
    }
}
