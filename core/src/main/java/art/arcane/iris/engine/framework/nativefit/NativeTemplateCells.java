package art.arcane.iris.engine.framework.nativefit;

/**
 * A piece's processed template blocks inside a region, at world positions: {@code states[i]} is the block at
 * {@code (xs[i], ys[i], zs[i])}, null where the processors removed the template's block. {@code resolved} is false for
 * a piece without a template. {@code readBlocks} counts the template blocks the platform read and processed.
 */
public record NativeTemplateCells<S>(boolean resolved, int[] xs, int[] ys, int[] zs, S[] states, int readBlocks) {
}
