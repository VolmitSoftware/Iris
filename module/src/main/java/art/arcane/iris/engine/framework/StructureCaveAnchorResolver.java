package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.carve.CaveLavaHeightSettings;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.engine.framework.structure.StructureWorld;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.mantle.CarvedVoid;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

public final class StructureCaveAnchorResolver {
    private static final int CHUNK_COLUMN_COUNT = 256;
    private static final int MAX_ATTEMPTS = 64;
    private static final int MAX_SCAN_STEP = 16;
    private static final int MAX_CLEARANCE = 64;
    private static final int MAX_GENERIC_CENTER_SEARCH = 4096;

    private StructureCaveAnchorResolver() {
    }

    public static Anchor resolve(PackWorld world, StructurePlacement placement, int chunkX, int chunkZ, RNG rng) {
        Objects.requireNonNull(world, "Cave structure anchor requires a world");
        Objects.requireNonNull(placement, "Cave structure anchor requires a placement");
        Objects.requireNonNull(rng, "Cave structure anchor requires a random source");
        StructureAnchorMode mode = placement.resolvedAnchor();
        if (!mode.isCave() || world.running().isEmpty()) {
            return null;
        }

        int worldMin = world.minWorldY() + 1;
        int worldMax = world.minWorldY() + StructureWorld.height(world) - 2;
        int minimumY = Math.max(worldMin, Math.min(placement.minHeight(), placement.maxHeight()));
        int maximumY = Math.min(worldMax, Math.max(placement.minHeight(), placement.maxHeight()));
        if (minimumY > maximumY) {
            return null;
        }

        int attempts = Math.min(MAX_ATTEMPTS, Math.max(1, placement.caveAnchorAttempts()));
        int scanStep = Math.min(MAX_SCAN_STEP, Math.max(1, placement.caveAnchorScanStep()));
        int clearance = Math.min(MAX_CLEARANCE, Math.max(1, placement.caveMinimumClearance()));
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        int[] candidateColumns = candidateColumnIndices(rng, attempts);
        for (int candidateColumn : candidateColumns) {
            int blockX = chunkMinX + (candidateColumn & 15);
            int blockZ = chunkMinZ + (candidateColumn >>> 4);
            List<Integer> anchors = anchorsInColumn(
                    world, placement, mode, blockX, blockZ,
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
            PackWorld world,
            StructurePlacement placement,
            StructureAnchorMode mode,
            int blockX,
            int blockZ,
            int minimumY,
            int maximumY,
            int scanStep,
            int clearance
    ) {
        if (mode == StructureAnchorMode.CAVE_CENTER) {
            return centerAnchorsInColumn(
                    world, placement, blockX, blockZ,
                    minimumY, maximumY, scanStep, clearance);
        }
        List<Integer> anchors = new ArrayList<>();
        int step = Math.max(1, scanStep);
        for (int worldY = minimumY; worldY <= maximumY; worldY += step) {
            int mantleY = toMantleY(worldY, world.minWorldY());
            if (!matchesGeometry(world, mode, blockX, mantleY, blockZ, clearance)) {
                continue;
            }
            if (!matchesAnchorFluid(world, placement, blockX, mantleY, blockZ)) {
                continue;
            }
            if (!matchesBiome(world, placement, blockX, mantleY, blockZ)) {
                continue;
            }
            anchors.add(worldY);
        }
        return List.copyOf(anchors);
    }

    private static List<Integer> centerAnchorsInColumn(
            PackWorld world,
            StructurePlacement placement,
            int blockX,
            int blockZ,
            int minimumY,
            int maximumY,
            int scanStep,
            int clearance
    ) {
        List<Integer> anchors = new ArrayList<>();
        int step = Math.max(1, scanStep);
        int worldOffset = world.minWorldY();
        int minimumMantleY = 0;
        int maximumMantleY = StructureWorld.height(world) - 1;
        int processedThroughWorldY = Integer.MIN_VALUE;
        IntPredicate carved = y -> isCarved(world, blockX, y, blockZ);
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
                    anchors, world, placement, blockX, blockZ,
                    lowerCenterWorldY, minimumY, maximumY);
            int upperCenterWorldY = lower + Math.ceilDiv(upper - lower, 2) + worldOffset;
            if (upperCenterWorldY != lowerCenterWorldY) {
                addCenterAnchor(
                        anchors, world, placement, blockX, blockZ,
                        upperCenterWorldY, minimumY, maximumY);
            }
        }
        return List.copyOf(anchors);
    }

    private static void addCenterAnchor(
            List<Integer> anchors,
            PackWorld world,
            StructurePlacement placement,
            int blockX,
            int blockZ,
            int worldY,
            int minimumY,
            int maximumY
    ) {
        if (worldY < minimumY || worldY > maximumY) {
            return;
        }
        int mantleY = toMantleY(worldY, world.minWorldY());
        if (matchesAnchorFluid(world, placement, blockX, mantleY, blockZ)
                && matchesBiome(world, placement, blockX, mantleY, blockZ)) {
            anchors.add(worldY);
        }
    }

    static boolean matchesGeometry(
            PackWorld world,
            StructureAnchorMode mode,
            int blockX,
            int mantleY,
            int blockZ,
            int clearance
    ) {
        IntPredicate carved = y -> isCarved(world, blockX, y, blockZ);
        if (mode == StructureAnchorMode.CAVE_CENTER) {
            return matchesCenterGeometry(
                    carved, mantleY, Math.max(1, clearance), 0, StructureWorld.height(world) - 1);
        }
        return matchesGeometry(carved, mode, mantleY, clearance);
    }

    static boolean matchesGeometry(
            IntPredicate carved,
            StructureAnchorMode mode,
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

    private static boolean isCarved(PackWorld world, int blockX, int mantleY, int blockZ) {
        if (mantleY < 0 || mantleY >= StructureWorld.height(world)) {
            return false;
        }
        return carvedAt(world, blockX, mantleY, blockZ).isPresent();
    }

    private static boolean matchesAnchorFluid(
            PackWorld world,
            StructurePlacement placement,
            int blockX,
            int mantleY,
            int blockZ
    ) {
        int lavaHeight = StructureWorld.running(world).settings().at(CaveLavaHeightSettings.class, blockX, blockZ)
                .dimension().map(CaveLavaHeightSettings::height).orElse(CaveLavaHeightSettings.DEFAULT_HEIGHT);
        return acceptsAnchorFluid(
                placement.underwater(),
                carvedAt(world, blockX, mantleY, blockZ).orElse(null),
                mantleY,
                lavaHeight);
    }

    static boolean acceptsAnchorFluid(
            boolean underwater,
            CarvedVoid carved,
            int mantleY,
            int defaultLavaHeight
    ) {
        if (carved == null) {
            return false;
        }
        if (underwater) {
            return true;
        }
        if (carved.fill() == CarvedVoid.Fill.AIR) {
            return true;
        }
        return carved.fill() == CarvedVoid.Fill.OPEN && mantleY > defaultLavaHeight;
    }

    private static Optional<CarvedVoid> carvedAt(PackWorld world, int blockX, int mantleY, int blockZ) {
        return StructureWorld.running(world).carvedAt(blockX, mantleY, blockZ);
    }

    static int toMantleY(int worldY, int worldMinHeight) {
        return Math.subtractExact(worldY, worldMinHeight);
    }

    private static boolean matchesBiome(
            PackWorld world,
            StructurePlacement placement,
            int blockX,
            int mantleY,
            int blockZ
    ) {
        if (placement.caveBiomes().isEmpty()) {
            return true;
        }
        Optional<ResourceId> biome = caveOrCarvedBiome(world, blockX, mantleY, blockZ);
        if (biome.isEmpty()) {
            return false;
        }
        String actual = StructureWorld.keyOf(world, biome.get()).toLowerCase(Locale.ROOT);
        for (String allowed : placement.caveBiomes()) {
            if (matchesBiomeKey(actual, allowed)) {
                return true;
            }
        }
        return false;
    }

    /** The biome the carver gave the void at the block, else the cave biome of the column at that height. */
    private static Optional<ResourceId> caveOrCarvedBiome(PackWorld world, int blockX, int mantleY, int blockZ) {
        Optional<ResourceId> carvedBiome = carvedAt(world, blockX, mantleY, blockZ).flatMap(CarvedVoid::biome);
        if (carvedBiome.isPresent()) {
            return carvedBiome;
        }
        return StructureWorld.running(world).layout().caveBiome(blockX, mantleY, blockZ);
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
