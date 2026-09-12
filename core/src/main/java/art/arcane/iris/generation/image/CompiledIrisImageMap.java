package art.arcane.iris.generation.image;


import java.util.Objects;

public final class CompiledIrisImageMap {
    private final State state;

    CompiledIrisImageMap(State state) {
        this.state = Objects.requireNonNull(state, "Compiled image-map state");
    }

    public static CompiledIrisImageMap compile(IrisImageMap definition, IrisImage image) {
        return IrisImageMapCompiler.compile(definition, image);
    }

    public IrisImageMap getDefinition() {
        return state.definition();
    }

    public IrisImageMapSourceMetadata getSourceMetadata() {
        return state.sourceMetadata();
    }

    public IrisImageMapType getType() {
        return state.type();
    }

    public int getSourceWidth() {
        return state.sourceMetadata().width();
    }

    public int getSourceHeight() {
        return state.sourceMetadata().height();
    }

    public String getContentHash() {
        return state.contentHash();
    }

    public String contentHash() {
        return state.contentHash();
    }

    public CompiledIrisImageMap withoutDecodedValues() {
        return state.scalarValues() == null && state.targetIndices() == null
                ? this
                : new CompiledIrisImageMap(state.withoutDecodedValues());
    }

    public int getClippedPixelCount() {
        return state.clippedPixelCount();
    }

    public int getUnknownColorPixelCount() {
        return state.unknownColorPixelCount();
    }

    public boolean containsWorld(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            return false;
        }
        double sourceX = state.sourceTransform().sourceX(worldX, worldZ);
        double sourceZ = state.sourceTransform().sourceZ(worldX, worldZ);
        return validSourceCoordinate(sourceX, sourceZ)
                && sourceX >= 0D
                && sourceZ >= 0D
                && sourceX < state.sourceMetadata().width()
                && sourceZ < state.sourceMetadata().height();
    }

    public boolean containsWorldForSampling(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            return false;
        }
        double sourceX = state.sourceTransform().sourceX(worldX, worldZ);
        double sourceZ = state.sourceTransform().sourceZ(worldX, worldZ);
        if (!validSourceCoordinate(sourceX, sourceZ)) {
            return false;
        }
        long baseX = floorToLong(sourceX);
        long baseZ = floorToLong(sourceZ);
        return switch (state.sampling()) {
            case NEAREST -> sourceIndexInBounds(baseX, baseZ);
            case BILINEAR -> bilinearKernelInBounds(sourceX, sourceZ, baseX, baseZ);
            case BICUBIC -> bicubicKernelInBounds(sourceX, sourceZ, baseX, baseZ);
        };
    }

    public double sampleNormalized(double worldX, double worldZ) {
        requireScalarType();
        requireWorldCoordinate(worldX, worldZ);
        double sourceX = state.sourceTransform().sourceX(worldX, worldZ);
        double sourceZ = state.sourceTransform().sourceZ(worldX, worldZ);
        requireSourceCoordinate(sourceX, sourceZ);
        double sampled = switch (state.sampling()) {
            case NEAREST -> scalarAt(floorToLong(sourceX), floorToLong(sourceZ));
            case BILINEAR -> bilinear(sourceX, sourceZ);
            case BICUBIC -> bicubic(sourceX, sourceZ);
        };
        return clamp01(sampled);
    }

    public double sampleHeight(double worldX, double worldZ) {
        if (!isHeightType()) {
            throw new IrisImageMapValidationException("Image map type " + state.type() + " does not produce terrain heights");
        }
        double normalized = sampleNormalized(worldX, worldZ);
        double height = state.minimumHeight()
                + (normalized * (state.maximumHeight() - state.minimumHeight()))
                + state.verticalOffset();
        if (!state.clampHeight()) {
            return height;
        }
        return Math.max(state.minimumHeight(), Math.min(state.maximumHeight(), height));
    }

    public String sampleTarget(double worldX, double worldZ) {
        if (state.type() != IrisImageMapType.COLOR_MAP) {
            throw new IrisImageMapValidationException("Image map type " + state.type() + " does not produce legend targets");
        }
        if (state.targetIndices() == null) {
            throw new IrisImageMapValidationException("Compiled image-map view does not retain decoded target data");
        }
        requireWorldCoordinate(worldX, worldZ);
        double transformedX = state.sourceTransform().sourceX(worldX, worldZ);
        double transformedZ = state.sourceTransform().sourceZ(worldX, worldZ);
        requireSourceCoordinate(transformedX, transformedZ);
        long sourceX = floorToLong(transformedX);
        long sourceZ = floorToLong(transformedZ);
        int resolvedX = resolveIndex(sourceX, state.sourceMetadata().width());
        int resolvedZ = resolveIndex(sourceZ, state.sourceMetadata().height());
        if (resolvedX < 0 || resolvedZ < 0) {
            return state.fallbackTarget();
        }
        int targetIndex = state.targetIndices().get((resolvedZ * state.sourceMetadata().width()) + resolvedX);
        return targetIndex < 0 ? null : state.targets()[targetIndex];
    }

    private void requireWorldCoordinate(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            throw new IrisImageMapValidationException("World coordinates must be finite, got " + worldX + "," + worldZ);
        }
    }

    private void requireSourceCoordinate(double sourceX, double sourceZ) {
        if (!validSourceCoordinate(sourceX, sourceZ)) {
            throw new IrisImageMapValidationException(
                    "World coordinates transform outside the supported source-coordinate range: "
                            + sourceX + "," + sourceZ
            );
        }
    }

    private boolean bilinearKernelInBounds(double sourceX, double sourceZ, long baseX, long baseZ) {
        long maximumX = sourceX == baseX ? baseX : baseX + 1L;
        long maximumZ = sourceZ == baseZ ? baseZ : baseZ + 1L;
        return baseX >= 0L
                && baseZ >= 0L
                && maximumX < state.sourceMetadata().width()
                && maximumZ < state.sourceMetadata().height();
    }

    private boolean bicubicKernelInBounds(double sourceX, double sourceZ, long baseX, long baseZ) {
        if (sourceX == baseX && sourceZ == baseZ) {
            return sourceIndexInBounds(baseX, baseZ);
        }
        return baseX >= 1L
                && baseZ >= 1L
                && baseX + 2L < state.sourceMetadata().width()
                && baseZ + 2L < state.sourceMetadata().height();
    }

    private boolean sourceIndexInBounds(long sourceX, long sourceZ) {
        return sourceX >= 0L
                && sourceZ >= 0L
                && sourceX < state.sourceMetadata().width()
                && sourceZ < state.sourceMetadata().height();
    }

    private double bilinear(double sourceX, double sourceZ) {
        long x0 = floorToLong(sourceX);
        long z0 = floorToLong(sourceZ);
        double fractionX = sourceX - x0;
        double fractionZ = sourceZ - z0;
        if (fractionX == 0D && fractionZ == 0D) {
            return scalarAt(x0, z0);
        }
        if (fractionZ == 0D) {
            return lerp(scalarAt(x0, z0), scalarAt(x0 + 1L, z0), fractionX);
        }
        if (fractionX == 0D) {
            return lerp(scalarAt(x0, z0), scalarAt(x0, z0 + 1L), fractionZ);
        }
        double top = lerp(scalarAt(x0, z0), scalarAt(x0 + 1L, z0), fractionX);
        double bottom = lerp(scalarAt(x0, z0 + 1L), scalarAt(x0 + 1L, z0 + 1L), fractionX);
        return lerp(top, bottom, fractionZ);
    }

    private double bicubic(double sourceX, double sourceZ) {
        long baseX = floorToLong(sourceX);
        long baseZ = floorToLong(sourceZ);
        double fractionX = sourceX - baseX;
        double fractionZ = sourceZ - baseZ;
        if (fractionX == 0D && fractionZ == 0D) {
            return scalarAt(baseX, baseZ);
        }
        double row0 = cubicRow(baseX, baseZ - 1L, fractionX);
        double row1 = cubicRow(baseX, baseZ, fractionX);
        double row2 = cubicRow(baseX, baseZ + 1L, fractionX);
        double row3 = cubicRow(baseX, baseZ + 2L, fractionX);
        return cubic(row0, row1, row2, row3, fractionZ);
    }

    private double cubicRow(long baseX, long sourceZ, double fractionX) {
        return cubic(
                scalarAt(baseX - 1L, sourceZ),
                scalarAt(baseX, sourceZ),
                scalarAt(baseX + 1L, sourceZ),
                scalarAt(baseX + 2L, sourceZ),
                fractionX
        );
    }

    private double scalarAt(long sourceX, long sourceZ) {
        int resolvedX = resolveIndex(sourceX, state.sourceMetadata().width());
        int resolvedZ = resolveIndex(sourceZ, state.sourceMetadata().height());
        if (resolvedX < 0 || resolvedZ < 0) {
            return state.fallbackValue();
        }
        return state.scalarValues().get((resolvedZ * state.sourceMetadata().width()) + resolvedX);
    }

    private int resolveIndex(long coordinate, int length) {
        if (coordinate >= 0L && coordinate < length) {
            return (int) coordinate;
        }
        IrisImageMapOutOfBounds outOfBounds = state.outOfBounds();
        return switch (outOfBounds) {
            case FALLBACK -> -1;
            case CLAMP -> coordinate < 0L ? 0 : length - 1;
            case REPEAT -> (int) Math.floorMod(coordinate, (long) length);
            case MIRROR -> mirrorIndex(coordinate, length);
            case ERROR -> throw new IrisImageMapValidationException(
                    "Source coordinate " + coordinate + " is outside 0.." + (length - 1)
            );
        };
    }

    private int mirrorIndex(long coordinate, int length) {
        if (length == 1) {
            return 0;
        }
        long period = (long) length * 2L;
        long mirrored = Math.floorMod(coordinate, period);
        if (mirrored >= length) {
            mirrored = period - 1L - mirrored;
        }
        return (int) mirrored;
    }

    private boolean isHeightType() {
        return state.type() == IrisImageMapType.GRAYSCALE_HEIGHT
                || state.type() == IrisImageMapType.RGB_HEIGHT;
    }

    private void requireScalarType() {
        if (state.scalarValues() == null) {
            if (isHeightType()
                    || state.type() == IrisImageMapType.BINARY_MASK
                    || state.type() == IrisImageMapType.GRAYSCALE_MASK
                    || state.type() == IrisImageMapType.ALPHA_MASK) {
                throw new IrisImageMapValidationException("Compiled image-map view does not retain decoded scalar data");
            }
            throw new IrisImageMapValidationException("Image map type " + state.type() + " does not produce normalized scalar data");
        }
    }

    private static long floorToLong(double value) {
        return (long) Math.floor(value);
    }

    private static boolean validSourceCoordinate(double sourceX, double sourceZ) {
        double safeMinimum = -9_000_000_000_000_000_000D;
        double safeMaximum = 9_000_000_000_000_000_000D;
        return Double.isFinite(sourceX)
                && Double.isFinite(sourceZ)
                && sourceX >= safeMinimum
                && sourceX <= safeMaximum
                && sourceZ >= safeMinimum
                && sourceZ <= safeMaximum;
    }

    private static double lerp(double a, double b, double factor) {
        return a + ((b - a) * factor);
    }

    private static double cubic(double p0, double p1, double p2, double p3, double factor) {
        double squared = factor * factor;
        double cubed = squared * factor;
        return 0.5D * ((2D * p1)
                + ((-p0 + p2) * factor)
                + (((2D * p0) - (5D * p1) + (4D * p2) - p3) * squared)
                + ((-p0 + (3D * p1) - (3D * p2) + p3) * cubed));
    }

    private static double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    static record State(
            IrisImageMap definition,
            IrisImageMapSourceMetadata sourceMetadata,
            IrisImageMapType type,
            FloatTiles scalarValues,
            IntTiles targetIndices,
            String[] targets,
            SourceTransform sourceTransform,
            IrisImageMapSampling sampling,
            IrisImageMapOutOfBounds outOfBounds,
            double fallbackValue,
            String fallbackTarget,
            double minimumHeight,
            double maximumHeight,
            double verticalOffset,
            boolean clampHeight,
            int clippedPixelCount,
            int unknownColorPixelCount,
            String contentHash
    ) {
        State withoutDecodedValues() {
            return new State(
                    definition,
                    sourceMetadata,
                    type,
                    null,
                    null,
                    null,
                    sourceTransform,
                    sampling,
                    outOfBounds,
                    fallbackValue,
                    fallbackTarget,
                    minimumHeight,
                    maximumHeight,
                    verticalOffset,
                    clampHeight,
                    clippedPixelCount,
                    unknownColorPixelCount,
                    contentHash
            );
        }
    }

    static record SourceTransform(
            double sourceXFromWorldX,
            double sourceXFromWorldZ,
            double sourceXOffset,
            double sourceZFromWorldX,
            double sourceZFromWorldZ,
            double sourceZOffset
    ) {
        double sourceX(double worldX, double worldZ) {
            return (sourceXFromWorldX * worldX) + (sourceXFromWorldZ * worldZ) + sourceXOffset;
        }

        double sourceZ(double worldX, double worldZ) {
            return (sourceZFromWorldX * worldX) + (sourceZFromWorldZ * worldZ) + sourceZOffset;
        }
    }

    static final class FloatTiles {
        private static final int TILE_SHIFT = 20;
        private static final int TILE_SIZE = 1 << TILE_SHIFT;
        private static final int TILE_MASK = TILE_SIZE - 1;
        private final float[][] tiles;

        FloatTiles(int size) {
            int tileCount = (size + TILE_SIZE - 1) >>> TILE_SHIFT;
            tiles = new float[tileCount][];
            for (int tile = 0; tile < tileCount; tile++) {
                int remaining = size - (tile << TILE_SHIFT);
                tiles[tile] = new float[Math.min(TILE_SIZE, remaining)];
            }
        }

        float get(int index) {
            return tiles[index >>> TILE_SHIFT][index & TILE_MASK];
        }

        void set(int index, float value) {
            tiles[index >>> TILE_SHIFT][index & TILE_MASK] = value;
        }
    }

    static final class IntTiles {
        private static final int TILE_SHIFT = 20;
        private static final int TILE_SIZE = 1 << TILE_SHIFT;
        private static final int TILE_MASK = TILE_SIZE - 1;
        private final int[][] tiles;

        IntTiles(int size) {
            int tileCount = (size + TILE_SIZE - 1) >>> TILE_SHIFT;
            tiles = new int[tileCount][];
            for (int tile = 0; tile < tileCount; tile++) {
                int remaining = size - (tile << TILE_SHIFT);
                tiles[tile] = new int[Math.min(TILE_SIZE, remaining)];
            }
        }

        int get(int index) {
            return tiles[index >>> TILE_SHIFT][index & TILE_MASK];
        }

        void set(int index, int value) {
            tiles[index >>> TILE_SHIFT][index & TILE_MASK] = value;
        }
    }
}
