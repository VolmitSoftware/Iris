package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionStack;
import art.arcane.iris.generation.terrain.IrisDimensionStackBlend;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public final class DimensionStackContext {
    private static final int LAYOUT_CACHE_SIZE = 65536;
    private static final long SEAM_SALT = 0x44494D535441434BL;
    private static final long HASH_OFFSET = 0xCBF29CE484222325L;
    private static final long HASH_PRIME = 0x100000001B3L;

    private final Engine engine;
    private final int outputHeight;
    private final int spacer;
    private final List<DimensionTerrainContext> layersTopToBottom;
    private final List<DimensionTerrainContext> layersBottomToTop;
    private final List<SeamOffsetSampler> seamOffsetSamplersBottomToTop;
    private final Cache<Long, DimensionStackLayout> layoutCache;
    private final boolean volumetric;

    private DimensionStackContext(ContextState state) {
        engine = state.engine();
        outputHeight = state.outputHeight();
        spacer = state.spacer();
        layersBottomToTop = List.copyOf(state.layersBottomToTop());
        ArrayList<DimensionTerrainContext> reversed = new ArrayList<>(layersBottomToTop);
        Collections.reverse(reversed);
        layersTopToBottom = List.copyOf(reversed);
        seamOffsetSamplersBottomToTop = List.copyOf(state.seamOffsetSamplersBottomToTop());
        layoutCache = Caffeine.newBuilder().maximumSize(LAYOUT_CACHE_SIZE).build();
        volumetric = layersBottomToTop.stream().anyMatch(DimensionTerrainContext::hasTerrain3D);
    }

    public static DimensionStackContext create(Engine engine, IrisDimensionStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("Dimension stack configuration is required");
        }
        KList<String> dimensionKeys = stack.getDimensions();
        validateRuntimeContract(engine.getDimension(), dimensionKeys);
        if (stack.getSpacer() < 0 || stack.getSpacer() > 256) {
            throw new IllegalArgumentException("Dimension stack spacer must be between 0 and 256");
        }

        ArrayList<DimensionTerrainContext> layersBottomToTop = new ArrayList<>(dimensionKeys.size());
        for (int keyIndex = dimensionKeys.size() - 1; keyIndex >= 0; keyIndex--) {
            String dimensionKey = dimensionKeys.get(keyIndex);
            IrisDimension dimension = dimensionKey.equals(engine.getDimension().getLoadKey())
                    ? engine.getDimension()
                    : engine.getData().getDimensionLoader().load(dimensionKey);
            if (dimension == null) {
                throw new IllegalArgumentException(
                        "Dimension stack reference does not resolve in this pack: " + dimensionKey);
            }
            layersBottomToTop.add(DimensionTerrainContext.forStack(engine, dimension));
        }

        List<SeamOffsetSampler> seamSamplers = createSeamSamplers(
                engine,
                stack.getBlend(),
                layersBottomToTop
        );
        return new DimensionStackContext(new ContextState(
                engine,
                engine.getHeight(),
                stack.getSpacer(),
                layersBottomToTop,
                seamSamplers
        ));
    }

    private static void validateRuntimeContract(IrisDimension host, KList<String> dimensionKeys) {
        if (dimensionKeys == null || dimensionKeys.size() < 2) {
            throw new IllegalArgumentException("Dimension stack requires at least two dimensions");
        }
        String hostKey = host.getLoadKey();
        if (!hostKey.equals(dimensionKeys.get(dimensionKeys.size() - 1))) {
            throw new IllegalArgumentException("Dimension stack host must be its final bottom dimension");
        }
        int hostReferences = 0;
        for (String dimensionKey : dimensionKeys) {
            if (dimensionKey == null || dimensionKey.isBlank()) {
                throw new IllegalArgumentException("Dimension stack keys must be nonblank");
            }
            if (hostKey.equals(dimensionKey)) {
                hostReferences++;
            }
        }
        if (hostReferences != 1) {
            throw new IllegalArgumentException("Dimension stack must contain its host exactly once");
        }
    }

    private static List<SeamOffsetSampler> createSeamSamplers(
            Engine engine,
            IrisDimensionStackBlend blend,
            List<DimensionTerrainContext> layersBottomToTop
    ) {
        int seamCount = layersBottomToTop.size() - 1;
        ArrayList<SeamOffsetSampler> samplers = new ArrayList<>(seamCount);
        if (blend == null || blend.getAmplitude() == 0) {
            for (int seamIndex = 0; seamIndex < seamCount; seamIndex++) {
                samplers.add((x, z) -> 0);
            }
            return samplers;
        }
        if (blend.getAmplitude() < 0 || blend.getAmplitude() > 256) {
            throw new IllegalArgumentException("Dimension stack blend amplitude must be between 0 and 256");
        }
        IrisGeneratorStyle style = blend.getStyle();
        if (style == null) {
            throw new IllegalArgumentException("Dimension stack blend style is required when amplitude is positive");
        }

        for (int seamIndex = 0; seamIndex < seamCount; seamIndex++) {
            DimensionTerrainContext lower = layersBottomToTop.get(seamIndex);
            DimensionTerrainContext upper = layersBottomToTop.get(seamIndex + 1);
            long salt = seamSalt(
                    seamIndex,
                    lower.getDimension().getLoadKey(),
                    upper.getDimension().getLoadKey()
            );
            RNG seamRng = new RNG(engine.getSeedManager().getTerrain() ^ salt);
            CNG seamNoise = style.create(seamRng, engine.getData(), engine);
            int amplitude = blend.getAmplitude();
            samplers.add((x, z) -> clampSeamOffset(
                    seamNoise.fitDouble(-amplitude, amplitude, x, z), amplitude));
        }
        return samplers;
    }

    static int clampSeamOffset(double sampledOffset, int amplitude) {
        if (!Double.isFinite(sampledOffset)) {
            return 0;
        }
        double clamped = Math.max(-amplitude, Math.min(amplitude, sampledOffset));
        return (int) Math.round(clamped);
    }

    static long seamSalt(int seamIndex, String lowerDimensionKey, String upperDimensionKey) {
        long hash = HASH_OFFSET ^ SEAM_SALT;
        hash = hashString(hash, lowerDimensionKey);
        hash ^= seamIndex;
        hash *= HASH_PRIME;
        return hashString(hash, upperDimensionKey);
    }

    private static long hashString(long hash, String value) {
        long result = hash;
        for (int characterIndex = 0; characterIndex < value.length(); characterIndex++) {
            result ^= value.charAt(characterIndex);
            result *= HASH_PRIME;
        }
        return result;
    }

    public DimensionStackLayout sample(int x, int z) {
        return sample(x, z, usesTemporaryNaturalFallback(x, z));
    }

    private DimensionStackLayout sample(int x, int z, boolean naturalFallback) {
        ArrayList<DimensionStackLayout.LayerInput> layerInputs =
                new ArrayList<>(layersBottomToTop.size());
        for (DimensionTerrainContext terrainContext : layersBottomToTop) {
            DimensionTerrainContext.ColumnSample column = terrainContext.sampleColumn(
                    x, z, naturalFallback);
            int normalTerrainHeight = localCoordinate(
                    column.terrainHeight(),
                    terrainContext.getLocalHeight()
            );
            int fluidHeight = localCoordinate(
                    column.fluidHeight(),
                    terrainContext.getLocalHeight()
            );
            layerInputs.add(new DimensionStackLayout.LayerInput(
                    terrainContext,
                    column.biome(),
                    column.region(),
                    column.rockBlock(),
                    column.fluidBlock(),
                    column.surfaceBlock(),
                    normalTerrainHeight,
                    fluidHeight,
                    column.terrainColumn()
            ));
        }

        int[] seamOffsets = new int[seamOffsetSamplersBottomToTop.size()];
        for (int seamIndex = 0; seamIndex < seamOffsets.length; seamIndex++) {
            seamOffsets[seamIndex] = seamOffsetSamplersBottomToTop.get(seamIndex).sample(x, z);
        }
        return DimensionStackLayout.create(outputHeight, spacer, layerInputs, seamOffsets);
    }

    private static int localCoordinate(double sample, int localHeight) {
        int maximum = Math.max(0, localHeight - 1);
        long rounded = Math.round(sample);
        return (int) Math.max(0L, Math.min(maximum, rounded));
    }

    public int getStackTerrainHeight(int x, int z) {
        return (int) (sampleTopHeights(x, z) >>> 32);
    }

    public int getStackTopHeight(int x, int z) {
        return (int) sampleTopHeights(x, z);
    }

    public DimensionStackLayout getLayout(int x, int z) {
        boolean naturalFallback = usesTemporaryNaturalFallback(x, z);
        long columnKey = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        return resolveLayout(
                layoutCache,
                columnKey,
                naturalFallback,
                () -> sample(x, z, naturalFallback)
        );
    }

    public DimensionStackLayout.Layer getLayerAt(int x, int y, int z) {
        return getLayout(x, z).layerAt(y);
    }

    static DimensionStackLayout resolveLayout(
            Cache<Long, DimensionStackLayout> cache,
            long columnKey,
            boolean naturalFallback,
            Supplier<DimensionStackLayout> sampler
    ) {
        if (naturalFallback) {
            return sampler.get();
        }
        return cache.get(columnKey, ignored -> sampler.get());
    }

    private long sampleTopHeights(int x, int z) {
        if (volumetric) {
            DimensionStackLayout layout = getLayout(x, z);
            return ((long) layout.clippedStackTerrainTopY() << 32)
                    | (layout.clippedStackTopY() & 0xFFFFFFFFL);
        }
        boolean naturalFallback = usesTemporaryNaturalFallback(x, z);
        int baseY = 0;
        int renderedTerrainTopY = Integer.MIN_VALUE;
        int renderedContentTopY = Integer.MIN_VALUE;
        int outputMaxY = outputHeight - 1;
        int previousContentTopY = Integer.MIN_VALUE;
        int previousRenderMinY = Integer.MIN_VALUE;
        int previousRenderMaxY = Integer.MIN_VALUE;
        int previousSurfaceY = Integer.MIN_VALUE;
        boolean previousVisible = false;
        for (int layerIndex = 0; layerIndex < layersBottomToTop.size(); layerIndex++) {
            DimensionTerrainContext terrainContext = layersBottomToTop.get(layerIndex);
            if (layerIndex > 0) {
                int gapMinY = (int) Math.max(0L, (long) previousContentTopY + 1L);
                int gapMaxY = (int) Math.min((long) outputMaxY, (long) baseY - 1L);
                int previousTerrainY = previousVisible && previousSurfaceY >= previousRenderMinY
                        ? Math.min(outputMaxY, previousSurfaceY)
                        : Integer.MIN_VALUE;
                renderedTerrainTopY = DimensionStackLayout.eraseHeightThroughGap(
                        renderedTerrainTopY,
                        gapMinY,
                        gapMaxY,
                        previousTerrainY
                );
                renderedContentTopY = DimensionStackLayout.eraseHeightThroughGap(
                        renderedContentTopY,
                        gapMinY,
                        gapMaxY,
                        previousVisible ? previousRenderMaxY : Integer.MIN_VALUE
                );
            }
            int localTerrainHeight = localCoordinate(
                    terrainContext.getNormalTerrainHeight(x, z, naturalFallback),
                    terrainContext.getLocalHeight()
            );
            int localFluidHeight = localCoordinate(
                    terrainContext.getFluidHeight(x, z, naturalFallback),
                    terrainContext.getLocalHeight()
            );
            int surfaceY = saturatedAdd(baseY, localTerrainHeight);
            int fluidY = saturatedAdd(baseY, localFluidHeight);
            int layerContentTopY = Math.max(surfaceY, fluidY);
            int renderMinY = Math.max(0, baseY);
            int renderMaxY = Math.min(outputMaxY, layerContentTopY);
            if (renderMinY <= renderMaxY) {
                renderedContentTopY = Math.max(renderedContentTopY, renderMaxY);
                renderedTerrainTopY = DimensionStackLayout.mergeVisibleTerrainHeight(
                        renderedTerrainTopY,
                        renderMinY,
                        renderMaxY,
                        surfaceY,
                        outputMaxY
                );
            }
            previousContentTopY = layerContentTopY;
            previousRenderMinY = renderMinY;
            previousRenderMaxY = renderMaxY;
            previousSurfaceY = surfaceY;
            previousVisible = renderMinY <= renderMaxY;
            if (layerIndex + 1 < layersBottomToTop.size()) {
                int seamOffset = seamOffsetSamplersBottomToTop.get(layerIndex).sample(x, z);
                baseY = saturatedAdd(
                        layerContentTopY,
                        saturatedAdd(spacer, saturatedAdd(1, seamOffset))
                );
            }
        }
        int clippedTerrainTopY = Math.max(0, renderedTerrainTopY);
        int clippedContentTopY = Math.max(0, renderedContentTopY);
        return ((long) clippedTerrainTopY << 32) | (clippedContentTopY & 0xFFFFFFFFL);
    }

    private static int saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }

    public int getOutputHeight() {
        return outputHeight;
    }

    public int getSpacer() {
        return spacer;
    }

    public boolean usesTemporaryNaturalFallback(int x, int z) {
        return engine.answersFromNaturalTerrain(x, z);
    }

    public List<DimensionTerrainContext> getLayersTopToBottom() {
        return layersTopToBottom;
    }

    public List<DimensionTerrainContext> getLayersBottomToTop() {
        return layersBottomToTop;
    }

    private interface SeamOffsetSampler {
        int sample(double x, double z);
    }

    private record ContextState(
            Engine engine,
            int outputHeight,
            int spacer,
            List<DimensionTerrainContext> layersBottomToTop,
            List<SeamOffsetSampler> seamOffsetSamplersBottomToTop
    ) {
    }

}
