package art.arcane.iris.generation.subterrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;

public final class SubterrainPlanner {
    private static final int PLAN_CACHE_SLOTS = 4096;
    private static final int TILE_CACHE_SLOTS = 1024;
    private static final Comparator<SubterrainPlan> PLAN_ORDER =
            Comparator.comparingInt(SubterrainPlan::priority).reversed().thenComparing(SubterrainPlan::id);

    private final List<Definition> definitions;
    private final long seed;
    private final int minY;
    private final int maxYExclusive;
    private final AtomicReferenceArray<CachedPlan> planCache = new AtomicReferenceArray<>(PLAN_CACHE_SLOTS);
    private final AtomicReferenceArray<CandidateTile> tileCache = new AtomicReferenceArray<>(TILE_CACHE_SLOTS);
    private final Predicate<SubterrainBounds> allowedBounds;
    private final RegionResolver regionResolver;

    public SubterrainPlanner(Options options) {
        this(options, bounds -> true);
    }

    public SubterrainPlanner(Options options, Predicate<SubterrainBounds> allowedBounds) {
        Objects.requireNonNull(options);
        this.allowedBounds = Objects.requireNonNull(allowedBounds);
        regionResolver = Objects.requireNonNull(options.regionResolver());
        seed = options.seed();
        minY = options.minY();
        maxYExclusive = options.maxYExclusive();
        if (minY >= maxYExclusive) {
            throw new IllegalArgumentException("Subterrain world height is empty");
        }
        if (options.features().size() > 64) {
            throw new IllegalArgumentException("Subterrain supports at most 64 feature definitions");
        }
        List<Definition> loaded = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (IrisSubterrainFeature feature : options.features()) {
            if (feature == null || !feature.isEnabled()) {
                continue;
            }
            Definition definition = Definition.from(feature);
            if (!identifiers.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate subterrain id: " + definition.id());
            }
            loaded.add(definition);
        }
        definitions = List.copyOf(loaded);
    }

    private SubterrainPlanner(SubterrainPlanner source, Predicate<SubterrainBounds> allowedBounds) {
        definitions = source.definitions;
        regionResolver = source.regionResolver;
        seed = source.seed;
        minY = source.minY;
        maxYExclusive = source.maxYExclusive;
        this.allowedBounds = source.allowedBounds.and(Objects.requireNonNull(allowedBounds));
    }

    public SubterrainPlanner restrictTo(Predicate<SubterrainBounds> allowedBounds) {
        return new SubterrainPlanner(this, allowedBounds);
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    public List<SubterrainPlan> plansForBounds(int minX, int minZ, int maxX, int maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Subterrain query bounds are inverted");
        }
        List<SubterrainPlan> result = null;
        for (int definitionIndex = 0; definitionIndex < definitions.size(); definitionIndex++) {
            Definition definition = definitions.get(definitionIndex);
            long firstX = Math.floorDiv(minX, definition.spacing()) - 1L;
            long lastX = Math.floorDiv(maxX, definition.spacing()) + 1L;
            long firstZ = Math.floorDiv(minZ, definition.spacing()) - 1L;
            long lastZ = Math.floorDiv(maxZ, definition.spacing()) + 1L;
            if ((lastX - firstX + 1L) * (lastZ - firstZ + 1L) > 65_536L) {
                throw new IllegalArgumentException("Subterrain candidate query exceeds 65536 grid cells; use bounded search tiles");
            }
            for (long gridX = firstX; gridX <= lastX; gridX++) {
                for (long gridZ = firstZ; gridZ <= lastZ; gridZ++) {
                    SubterrainPlan plan = plan(definitionIndex, definition, gridX, gridZ);
                    if (plan != null && plan.bounds().intersects(minX, minZ, maxX, maxZ)) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(plan);
                    }
                }
            }
        }
        if (result == null) {
            return List.of();
        }
        result.sort(PLAN_ORDER);
        return List.copyOf(result);
    }

    public boolean hasCandidates(int chunkX, int chunkZ) {
        return !isEmpty() && !candidates(chunkX, chunkZ).isEmpty();
    }

    public SubterrainCell sample(int x, int y, int z) {
        if (isEmpty() || y < minY || y >= maxYExclusive) {
            return SubterrainCell.OUTSIDE;
        }
        return sample(candidates(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), x, y, z);
    }

    public ChunkSampler chunkSampler(int chunkX, int chunkZ) {
        return new ChunkSampler(this, chunkX, chunkZ, isEmpty() ? List.of() : candidates(chunkX, chunkZ));
    }

    public SubterrainCell sample(List<SubterrainPlan> candidates, int x, int y, int z) {
        SubterrainCell selected = raw(candidates, x, y, z);
        if (!selected.fluid()) {
            return selected;
        }
        if (exposed(raw(candidates, x - 1, y, z), selected)
                || exposed(raw(candidates, x + 1, y, z), selected)
                || exposed(raw(candidates, x, y, z - 1), selected)
                || exposed(raw(candidates, x, y, z + 1), selected)
                || exposed(raw(candidates, x, y - 1, z), selected)) {
            return solid(selected);
        }
        return selected;
    }

    private List<SubterrainPlan> candidates(int chunkX, int chunkZ) {
        int slot = (int) mix(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL)) & (TILE_CACHE_SLOTS - 1);
        CandidateTile tile = tileCache.getAcquire(slot);
        if (tile != null && tile.chunkX() == chunkX && tile.chunkZ() == chunkZ) {
            return tile.plans();
        }
        long baseX = (long) chunkX * 16;
        long baseZ = (long) chunkZ * 16;
        List<SubterrainPlan> plans = plansForBounds(
                (int) Math.max(Integer.MIN_VALUE, baseX - 1),
                (int) Math.max(Integer.MIN_VALUE, baseZ - 1),
                (int) Math.min(Integer.MAX_VALUE, baseX + 16),
                (int) Math.min(Integer.MAX_VALUE, baseZ + 16));
        tileCache.setRelease(slot, new CandidateTile(chunkX, chunkZ, plans));
        return plans;
    }

    private SubterrainPlan plan(int definitionIndex, Definition definition, long gridX, long gridZ) {
        int slot = (int) mix(mix(gridX ^ definition.seedSalt()) ^ Long.rotateLeft(gridZ, 29)) & (PLAN_CACHE_SLOTS - 1);
        CachedPlan cached = planCache.getAcquire(slot);
        if (cached != null && cached.definition() == definitionIndex && cached.gridX() == gridX && cached.gridZ() == gridZ) {
            return cached.plan();
        }
        SubterrainPlan plan = create(definition, gridX, gridZ);
        planCache.setRelease(slot, new CachedPlan(definitionIndex, gridX, gridZ, plan));
        return plan;
    }

    private SubterrainCell raw(List<SubterrainPlan> candidates, int x, int y, int z) {
        SubterrainCell selected = SubterrainCell.OUTSIDE;
        for (int index = 0; index < candidates.size(); index++) {
            SubterrainCell cell = candidates.get(index).sample(x, y, z);
            if (cell.solid() && cell.room().reservedSolid()) {
                return cell;
            }
            if (!selected.owned() && cell.owned() || selected.solid() && cell.occupied()) {
                selected = cell;
            }
        }
        return selected;
    }

    private boolean exposed(SubterrainCell neighbor, SubterrainCell fluid) {
        return !neighbor.solid() && neighbor.kind() != fluid.kind();
    }

    private SubterrainCell solid(SubterrainCell fluid) {
        SubterrainRoom room = fluid.room();
        SubterrainRoom sealed = new SubterrainRoom(room.featureId(), room.biome(), room.family(),
                room.centerX(), room.centerY(), room.centerZ(), room.pathX(), room.pathY(), room.pathZ(),
                room.floorY(), room.ceilingY(), room.boundaryDistance(), room.fluidHeadY(), false, true,
                SubterrainCell.Kind.SOLID);
        for (Definition definition : definitions) {
            if (room.featureId().startsWith(definition.id() + ":")) {
                return new SubterrainCell(SubterrainCell.Kind.SOLID, definition.solid(), sealed);
            }
        }
        throw new IllegalStateException("Unknown subterrain fluid owner: " + room.featureId());
    }

    private SubterrainPlan create(Definition definition, long gridX, long gridZ) {
        long value = mix(seed ^ definition.seedSalt() ^ mix(gridX) ^ Long.rotateLeft(mix(gridZ), 23));
        if (unit(value) >= definition.probability()) {
            return null;
        }
        int spacing = definition.spacing();
        long x = gridX * spacing + spacing / 4L + Math.floorMod(mix(value + 1), Math.max(1, spacing / 2));
        long z = gridZ * spacing + spacing / 4L + Math.floorMod(mix(value + 2), Math.max(1, spacing / 2));
        int bottomMargin = definition.height() / 2 + 3 + definition.baseDepth();
        int topMargin = definition.height() - definition.height() / 2 + 3
                + (definition.family() == IrisSubterrainFamily.LAVA_TUBE ? definition.chimneyHeight() : 0);
        long low = (long) Math.max(minY, definition.minY()) + bottomMargin;
        long high = (long) Math.min(maxYExclusive - 1, definition.maxY()) - topMargin;
        if (low > high || x < Integer.MIN_VALUE + spacing || x > Integer.MAX_VALUE - spacing
                || z < Integer.MIN_VALUE + spacing || z > Integer.MAX_VALUE - spacing) {
            return null;
        }
        if (!definition.allowedRegions().isEmpty()
                && !definition.allowedRegions().contains(regionResolver.regionAt((int) x, (int) z))) {
            return null;
        }
        int y = (int) (low + Math.floorMod(mix(value + 3), high - low + 1));
        SubterrainPlan plan = new SubterrainPlan(new SubterrainPlan.Options(definition, (int) x, y, (int) z,
                (value & 1L) != 0, definition.id() + ":" + Long.toUnsignedString(value, 16)));
        return allowedBounds.test(plan.bounds()) ? plan : null;
    }

    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    @FunctionalInterface
    public interface RegionResolver {
        String regionAt(int x, int z);
    }

    public record Options(List<IrisSubterrainFeature> features, long seed, int minY, int maxYExclusive,
                          RegionResolver regionResolver) {
        public Options {
            features = List.copyOf(Objects.requireNonNull(features));
            Objects.requireNonNull(regionResolver);
        }
    }

    public static final class ChunkSampler {
        private final SubterrainPlanner planner;
        private final int chunkX;
        private final int chunkZ;
        private final List<SubterrainPlan> plans;
        private final int[] columnMinY;
        private final int[] columnMaxY;

        private ChunkSampler(SubterrainPlanner planner, int chunkX, int chunkZ, List<SubterrainPlan> plans) {
            this.planner = planner;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.plans = plans;
            if (plans.isEmpty()) {
                columnMinY = null;
                columnMaxY = null;
                return;
            }
            columnMinY = new int[256];
            columnMaxY = new int[256];
            Arrays.fill(columnMinY, Integer.MAX_VALUE);
            Arrays.fill(columnMaxY, Integer.MIN_VALUE);
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            for (int index = 0; index < plans.size(); index++) {
                SubterrainBounds bounds = plans.get(index).bounds();
                int lowX = Math.max(bounds.minX(), baseX) - baseX;
                int highX = Math.min(bounds.maxX(), baseX + 15) - baseX;
                int lowZ = Math.max(bounds.minZ(), baseZ) - baseZ;
                int highZ = Math.min(bounds.maxZ(), baseZ + 15) - baseZ;
                for (int localX = lowX; localX <= highX; localX++) {
                    for (int localZ = lowZ; localZ <= highZ; localZ++) {
                        int column = (localX << 4) | localZ;
                        columnMinY[column] = Math.min(columnMinY[column], bounds.minY());
                        columnMaxY[column] = Math.max(columnMaxY[column], bounds.maxY());
                    }
                }
            }
        }

        public boolean isEmpty() {
            return plans.isEmpty();
        }

        public SubterrainCell sample(int x, int y, int z) {
            if ((x >> 4) != chunkX || (z >> 4) != chunkZ) {
                return planner.sample(x, y, z);
            }
            if (plans.isEmpty() || y < planner.minY || y >= planner.maxYExclusive) {
                return SubterrainCell.OUTSIDE;
            }
            return sampleColumn(x, y, z);
        }

        public SubterrainCell sampleCandidates(int x, int y, int z) {
            if (plans.isEmpty()) {
                return SubterrainCell.OUTSIDE;
            }
            if ((x >> 4) != chunkX || (z >> 4) != chunkZ) {
                return planner.sample(plans, x, y, z);
            }
            return sampleColumn(x, y, z);
        }

        private SubterrainCell sampleColumn(int x, int y, int z) {
            int column = ((x & 15) << 4) | (z & 15);
            if (y < columnMinY[column] || y > columnMaxY[column]) {
                return SubterrainCell.OUTSIDE;
            }
            return planner.sample(plans, x, y, z);
        }
    }

    private record CandidateTile(int chunkX, int chunkZ, List<SubterrainPlan> plans) {
    }

    private record CachedPlan(int definition, long gridX, long gridZ, SubterrainPlan plan) {
    }

    record Definition(String id, IrisSubterrainFamily family, IrisSubterrainFluid fluid, String biome, int spacing, double probability,
                      int length, int radius, int height, double shapeWarp, int fluidDepth, int chimneyHeight, int terraceCount,
                      int pillarSpacing, double formationFraction, String solid, int priority,
                      int minY, int maxY, long seedSalt, Set<String> allowedRegions) {
        int baseDepth() {
            return family == IrisSubterrainFamily.TECTONIC_FAULT || fluidDepth == 0 ? 0
                    : (int) Math.ceil(Math.min(24, radius * 0.45) * shapeWarp);
        }

        static Definition from(IrisSubterrainFeature feature) {
            String id = Objects.requireNonNull(feature.getId(), "Subterrain id").trim();
            IrisSubterrainFamily family = Objects.requireNonNull(feature.getFamily(), "Subterrain family");
            IrisSubterrainFluid fluid = Objects.requireNonNullElse(feature.getFluid(),
                    family == IrisSubterrainFamily.LAVA_TUBE ? IrisSubterrainFluid.LAVA : IrisSubterrainFluid.WATER);
            int length = feature.getLength();
            int radius = feature.getRadius();
            int height = feature.getHeight();
            int chimney = feature.getChimneyHeight();
            int horizontalReach = family == IrisSubterrainFamily.CENOTE ? radius : Math.max(length / 2, radius);
            if (!id.matches("[a-zA-Z0-9_/-]+") || length < 16 || length > 2048 || radius < 6 || radius > 256
                    || height < 8 || height > 192 || chimney < 0 || chimney > 96
                    || !Double.isFinite(feature.getShapeWarp()) || feature.getShapeWarp() < 0 || feature.getShapeWarp() > 1
                    || feature.getSpacing() < Math.max(32, 2 * horizontalReach + 8) || feature.getSpacing() > 8192
                    || !Double.isFinite(feature.getProbability()) || feature.getProbability() < 0 || feature.getProbability() > 1
                    || feature.getFluidDepth() < 0 || feature.getFluidDepth() > Math.min(32, height / 3)
                    || feature.getTerraceCount() < 2 || feature.getTerraceCount() > 16
                    || feature.getPillarSpacing() < 0 || feature.getPillarSpacing() > 128
                    || !Double.isFinite(feature.getFormationFraction()) || feature.getFormationFraction() < 0
                    || feature.getFormationFraction() > 0.4 || (family == IrisSubterrainFamily.TECTONIC_FAULT && length < 200)) {
                throw new IllegalArgumentException("Invalid subterrain dimensions or placement settings: " + id);
            }
            if (feature.getWorldYRange() == null || !Double.isFinite(feature.getWorldYRange().getMin())
                    || !Double.isFinite(feature.getWorldYRange().getMax())
                    || feature.getWorldYRange().getMin() < Integer.MIN_VALUE
                    || feature.getWorldYRange().getMax() > Integer.MAX_VALUE
                    || feature.getWorldYRange().getMin() > feature.getWorldYRange().getMax()) {
                throw new IllegalArgumentException("Invalid subterrain world Y range: " + id);
            }
            String solid = Objects.requireNonNull(feature.getSolid(), "Subterrain solid").trim();
            if (!solid.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_/.-]+(?:\\[[a-z0-9_=,.-]+\\])?")
                    || solid.matches("(?:minecraft:)?(?:air|cave_air|void_air|water|lava)(?:\\[.*\\])?")) {
                throw new IllegalArgumentException("Invalid subterrain solid block state: " + id);
            }
            String biome = Objects.requireNonNullElse(feature.getBiome(), "").trim();
            if (!biome.isEmpty() && !biome.matches("[a-zA-Z0-9_/-]+")) {
                throw new IllegalArgumentException("Invalid subterrain biome key: " + id);
            }
            Set<String> allowedRegions = Set.copyOf(Objects.requireNonNull(feature.getAllowedRegions()));
            for (String region : allowedRegions) {
                if (!region.matches("[a-zA-Z0-9_/-]+")) {
                    throw new IllegalArgumentException("Invalid subterrain region key: " + id);
                }
            }
            long salt = 0xcbf29ce484222325L;
            for (int i = 0; i < id.length(); i++) {
                salt = (salt ^ id.charAt(i)) * 0x100000001b3L;
            }
            return new Definition(id, family, fluid, biome,
                    feature.getSpacing(), feature.getProbability(), length, radius, height, feature.getShapeWarp(), feature.getFluidDepth(),
                    chimney, feature.getTerraceCount(), feature.getPillarSpacing(), feature.getFormationFraction(),
                    solid, feature.getPriority(), (int) Math.ceil(feature.getWorldYRange().getMin()),
                    (int) Math.floor(feature.getWorldYRange().getMax()), salt, allowedRegions);
        }
    }
}
