package art.arcane.iris.engine.framework.nativefit;

import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;

/**
 * One chunk the platform decorates with structure starts, as the platform code hands it to the modules' fit: its live
 * blocks, the area it writes ({@code area}, world heights, inclusive), the world's bottom and top heights, the world
 * seed, the terrain's surface height per column and the platform's natural block per height (both world heights), and
 * the starts in the order the platform places them.
 */
public record NativeFitChunk<S>(
        NativeFitBlocks<S> blocks,
        NativeFitBox area,
        int bottomY,
        int topY,
        long seed,
        IntBinaryOperator surfaceY,
        IntFunction<S> naturalBlock,
        List<NativeFitStart<S>> starts
) {
    public NativeFitChunk {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(surfaceY, "surfaceY");
        Objects.requireNonNull(naturalBlock, "naturalBlock");
        starts = List.copyOf(starts);
    }
}
