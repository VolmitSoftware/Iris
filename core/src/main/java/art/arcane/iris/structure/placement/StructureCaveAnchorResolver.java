package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveStorage;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntPredicate;

public final class StructureCaveAnchorResolver {
    private static final int CHUNK_COLUMN_COUNT = 256;
    private static final int MAX_ATTEMPTS = 64;
    private static final int MAX_SCAN_STEP = 16;
    private static final int MAX_CLEARANCE = 64;
    private static final int MAX_GENERIC_CENTER_SEARCH = 4096;
    private static final byte LIQUID_FORCED_AIR = 3;

    private StructureCaveAnchorResolver() {
    }

    public static Anchor resolve(Engine engine, IrisStructurePlacement placement, int chunkX, int chunkZ, RNG rng) {
        Objects.requireNonNull(engine, "Cave structure anchor requires an engine");
        Objects.requireNonNull(placement, "Cave structure anchor requires a placement");
        Objects.requireNonNull(rng, "Cave structure anchor requires a random source");
        IrisStructureAnchorMode mode = placement.resolvedAnchor();
        if (!mode.isCave() || engine.getMantle() == null || engine.getMantle().getMantle() == null) {
            return null;
        }

        int worldMin = engine.getMinHeight() + 1;
        int worldMax = engine.getMinHeight() + engine.getHeight() - 2;
        int minimumY = Math.max(worldMin, Math.min(placement.getMinHeight(), placement.getMaxHeight()));
        int maximumY = Math.min(worldMax, Math.max(placement.getMinHeight(), placement.getMaxHeight()));
        if (minimumY > maximumY) {
            return null;
        }

        int attempts = Math.min(MAX_ATTEMPTS, Math.max(1, placement.getCaveAnchorAttempts()));
        int scanStep = Math.min(MAX_SCAN_STEP, Math.max(1, placement.getCaveAnchorScanStep()));
        int clearance = Math.min(MAX_CLEARANCE, Math.max(1, placement.getCaveMinimumClearance()));
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        int[] candidateColumns = candidateColumnIndices(rng, attempts);
        for (int candidateColumn : candidateColumns) {
            int blockX = chunkMinX + (candidateColumn & 15);
            int blockZ = chunkMinZ + (candidateColumn >>> 4);
            List<Integer> anchors = anchorsInColumn(
                    engine, placement, mode, blockX, blockZ,
                    minimumY, maximumY, scanStep, clearance);
            if (!anchors.isEmpty()) {
                return new Anchor(blockX, anchors.get(rng.nextInt(anchors.size())), blockZ);
            }
        }
        return null;
    }

    static int[] candidateColumnIndices(RNG rng, int attempts) {
        Objects.requireNonNull(rng, "Cave structure column selection requires a random source");
        int count = Math.min(MAX_ATTEMPTS, Math.max(1, attempts));
        int start = rng.nextInt(CHUNK_COLUMN_COUNT);
        int stride = (rng.nextInt(CHUNK_COLUMN_COUNT / 2) * 2) + 1;
        int[] columns = new int[count];
        for (int index = 0; index < count; index++) {
            columns[index] = (start + (index * stride)) & (CHUNK_COLUMN_COUNT - 1);
        }
        return columns;
    }

    static List<Integer> anchorsInColumn(
            Engine engine,
            IrisStructurePlacement placement,
            IrisStructureAnchorMode mode,
            int blockX,
            int blockZ,
            int minimumY,
            int maximumY,
            int scanStep,
            int clearance
    ) {
        if (mode == IrisStructureAnchorMode.CAVE_CENTER) {
            return centerAnchorsInColumn(
                    engine, placement, blockX, blockZ,
                    minimumY, maximumY, scanStep, clearance);
        }
        List<Integer> anchors = new ArrayList<>();
        int step = Math.max(1, scanStep);
        for (int worldY = minimumY; worldY <= maximumY; worldY += step) {
            int mantleY = toMantleY(worldY, engine.getMinHeight());
            if (!matchesGeometry(engine, mode, blockX, mantleY, blockZ, clearance)) {
                continue;
            }
            if (!matchesAnchorFluid(engine, placement, blockX, mantleY, blockZ)) {
                continue;
            }
            if (!matchesBiome(engine, placement, blockX, mantleY, blockZ)) {
                continue;
            }
            anchors.add(worldY);
        }
        return List.copyOf(anchors);
    }

    private static List<Integer> centerAnchorsInColumn(
            Engine engine,
            IrisStructurePlacement placement,
            int blockX,
            int blockZ,
            int minimumY,
            int maximumY,
            int scanStep,
            int clearance
    ) {
        List<Integer> anchors = new ArrayList<>();
        int step = Math.max(1, scanStep);
        int worldOffset = engine.getMinHeight();
        int minimumMantleY = 0;
        int maximumMantleY = engine.getHeight() - 1;
        int processedThroughWorldY = Integer.MIN_VALUE;
        IntPredicate carved = y -> isCarved(engine, blockX, y, blockZ);
        for (int worldY = minimumY; worldY <= maximumY; worldY += step) {
            if (worldY <= processedThroughWorldY) {
                continue;
            }
            int mantleY = toMantleY(worldY, worldOffset);
            if (!carved.test(mantleY)) {
                continue;
            }
            int lower = cavernLowerBound(carved, mantleY, minimumMantleY);
            int upper = cavernUpperBound(carved, mantleY, maximumMantleY);
            processedThroughWorldY = upper + worldOffset;
            if ((long) upper - lower + 1L < Math.max(1, clearance)) {
                continue;
            }
            int lowerCenterWorldY = lower + Math.floorDiv(upper - lower, 2) + worldOffset;
            addCenterAnchor(
                    anchors, engine, placement, blockX, blockZ,
                    lowerCenterWorldY, minimumY, maximumY);
            int upperCenterWorldY = lower + Math.ceilDiv(upper - lower, 2) + worldOffset;
            if (upperCenterWorldY != lowerCenterWorldY) {
                addCenterAnchor(
                        anchors, engine, placement, blockX, blockZ,
                        upperCenterWorldY, minimumY, maximumY);
            }
        }
        return List.copyOf(anchors);
    }

    private static void addCenterAnchor(
            List<Integer> anchors,
            Engine engine,
            IrisStructurePlacement placement,
            int blockX,
            int blockZ,
            int worldY,
            int minimumY,
            int maximumY
    ) {
        if (worldY < minimumY || worldY > maximumY) {
            return;
        }
        int mantleY = toMantleY(worldY, engine.getMinHeight());
        if (matchesAnchorFluid(engine, placement, blockX, mantleY, blockZ)
                && matchesBiome(engine, placement, blockX, mantleY, blockZ)) {
            anchors.add(worldY);
        }
    }

    static boolean matchesGeometry(
            Engine engine,
            IrisStructureAnchorMode mode,
            int blockX,
            int mantleY,
            int blockZ,
            int clearance
    ) {
        IntPredicate carved = y -> isCarved(engine, blockX, y, blockZ);
        if (mode == IrisStructureAnchorMode.CAVE_CENTER) {
            return matchesCenterGeometry(
                    carved, mantleY, Math.max(1, clearance), 0, engine.getHeight() - 1);
        }
        return matchesGeometry(carved, mode, mantleY, clearance);
    }

    static boolean matchesGeometry(
            IntPredicate carved,
            IrisStructureAnchorMode mode,
            int mantleY,
            int clearance
    ) {
        if (!carved.test(mantleY)) {
            return false;
        }
        int required = Math.max(1, clearance);
        return switch (mode) {
            case CAVE_FLOOR -> !carved.test(mantleY - 1)
                    && carvedRun(carved, mantleY, 1, required);
            case CAVE_CEILING -> !carved.test(mantleY + 1)
                    && carvedRun(carved, mantleY, -1, required);
            case CAVE_CENTER -> matchesCenterGeometry(
                    carved, mantleY, required,
                    saturatedSubtract(mantleY, MAX_GENERIC_CENTER_SEARCH),
                    saturatedAdd(mantleY, MAX_GENERIC_CENTER_SEARCH));
            case CAVE_ANY -> carvedRun(
                    carved, mantleY - Math.floorDiv(required - 1, 2), 1, required);
            default -> false;
        };
    }

    private static boolean matchesCenterGeometry(
            IntPredicate carved,
            int mantleY,
            int clearance,
            int minimumY,
            int maximumY
    ) {
        if (!carved.test(mantleY)) {
            return false;
        }
        int lower = cavernLowerBound(carved, mantleY, minimumY);
        int upper = cavernUpperBound(carved, mantleY, maximumY);
        if ((long) upper - lower + 1L < clearance) {
            return false;
        }
        int lowerCenter = lower + Math.floorDiv(upper - lower, 2);
        int upperCenter = lower + Math.ceilDiv(upper - lower, 2);
        return mantleY == lowerCenter || mantleY == upperCenter;
    }

    private static int cavernLowerBound(IntPredicate carved, int mantleY, int minimumY) {
        int lower = mantleY;
        while (lower > minimumY && carved.test(lower - 1)) {
            lower--;
        }
        return lower;
    }

    private static int cavernUpperBound(IntPredicate carved, int mantleY, int maximumY) {
        int upper = mantleY;
        while (upper < maximumY && carved.test(upper + 1)) {
            upper++;
        }
        return upper;
    }

    private static int saturatedSubtract(int value, int amount) {
        long result = (long) value - amount;
        return result < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) result;
    }

    private static int saturatedAdd(int value, int amount) {
        long result = (long) value + amount;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static boolean carvedRun(
            IntPredicate carved,
            int mantleY,
            int direction,
            int length
    ) {
        for (int offset = 0; offset < length; offset++) {
            if (!carved.test(mantleY + (direction * offset))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCarved(Engine engine, int blockX, int mantleY, int blockZ) {
        if (mantleY < 0 || mantleY >= engine.getHeight()) {
            return false;
        }
        MatterCavern cavern = cavernAt(engine, blockX, mantleY, blockZ);
        return cavern != null && cavern.isCavern();
    }

    private static boolean matchesAnchorFluid(
            Engine engine,
            IrisStructurePlacement placement,
            int blockX,
            int mantleY,
            int blockZ
    ) {
        HydrologyCaveCell hydrology = hydrologyAt(engine, blockX, mantleY, blockZ);
        MatterCavern cavern = cavernAt(engine, blockX, mantleY, blockZ);
        return acceptsAnchorFluid(
                placement.isUnderwater(),
                cavern,
                hydrology,
                mantleY,
                engine.getDimension().getCaveLavaHeight());
    }

    static boolean acceptsAnchorFluid(
            boolean underwater,
            MatterCavern cavern,
            int mantleY,
            int defaultLavaHeight
    ) {
        return acceptsAnchorFluid(underwater, cavern, null, mantleY, defaultLavaHeight);
    }

    static boolean acceptsAnchorFluid(
            boolean underwater,
            MatterCavern cavern,
            HydrologyCaveCell hydrology,
            int mantleY,
            int defaultLavaHeight
    ) {
        if (hydrology != null && hydrology.protectsPlacement()) {
            return false;
        }
        if (cavern == null || !cavern.isCavern()) {
            return false;
        }
        if (underwater) {
            return true;
        }
        if (cavern.getLiquid() == LIQUID_FORCED_AIR) {
            return true;
        }
        return cavern.isAir() && mantleY > defaultLavaHeight;
    }

    private static MatterCavern cavernAt(Engine engine, int blockX, int mantleY, int blockZ) {
        MatterCavern baseline = engine.getMantle().getMantle()
                .get(blockX, mantleY, blockZ, MatterCavern.class);
        HydrologyCaveCell hydrology = hydrologyAt(engine, blockX, mantleY, blockZ);
        return hydrology == null ? baseline : hydrology.asCavern();
    }

    private static HydrologyCaveCell hydrologyAt(Engine engine, int blockX, int mantleY, int blockZ) {
        return HydrologyCaveStorage.getIfPresent(
                engine.getMantle().getMantle(), blockX, mantleY, blockZ);
    }

    static int toMantleY(int worldY, int worldMinHeight) {
        return Math.subtractExact(worldY, worldMinHeight);
    }

    private static boolean matchesBiome(
            Engine engine,
            IrisStructurePlacement placement,
            int blockX,
            int mantleY,
            int blockZ
    ) {
        if (placement.getCaveBiomes() == null || placement.getCaveBiomes().isEmpty()) {
            return true;
        }
        IrisBiome biome = engine.getCaveOrMantleBiome(blockX, mantleY, blockZ);
        if (biome == null || biome.getLoadKey() == null) {
            return false;
        }
        String actual = biome.getLoadKey().toLowerCase(Locale.ROOT);
        for (String allowed : placement.getCaveBiomes()) {
            if (matchesBiomeKey(actual, allowed)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesBiomeKey(String actual, String allowed) {
        if (actual == null || actual.isBlank() || allowed == null || allowed.isBlank()) {
            return false;
        }
        String normalizedActual = actual.trim().toLowerCase(Locale.ROOT);
        String normalizedAllowed = allowed.trim().toLowerCase(Locale.ROOT);
        return normalizedActual.equals(normalizedAllowed)
                || normalizedActual.endsWith(":" + normalizedAllowed)
                || normalizedAllowed.endsWith(":" + normalizedActual);
    }

    public record Anchor(int x, int y, int z) {
    }
}
