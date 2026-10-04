package art.arcane.iris.generation.subterrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class SubterrainPlanner {
    private final List<Definition> definitions;
    private final long seed;
    private final int minY;
    private final int maxYExclusive;
    private final ThreadLocal<CandidateTile> sampleTile = new ThreadLocal<>();
    private final Predicate<SubterrainBounds> allowedBounds;

    public SubterrainPlanner(Options options) {
        this(options, bounds -> true);
    }

    public SubterrainPlanner(Options options, Predicate<SubterrainBounds> allowedBounds) {
        Objects.requireNonNull(options);
        this.allowedBounds = Objects.requireNonNull(allowedBounds);
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
        List<SubterrainPlan> result = new ArrayList<>();
        for (Definition definition : definitions) {
            long firstX = Math.floorDiv(minX, definition.spacing()) - 1L;
            long lastX = Math.floorDiv(maxX, definition.spacing()) + 1L;
            long firstZ = Math.floorDiv(minZ, definition.spacing()) - 1L;
            long lastZ = Math.floorDiv(maxZ, definition.spacing()) + 1L;
            if ((lastX - firstX + 1L) * (lastZ - firstZ + 1L) > 65_536L) {
                throw new IllegalArgumentException("Subterrain candidate query exceeds 65536 grid cells; use bounded search tiles");
            }
            for (long gridX = firstX; gridX <= lastX; gridX++) {
                for (long gridZ = firstZ; gridZ <= lastZ; gridZ++) {
                    SubterrainPlan plan = create(definition, gridX, gridZ);
                    if (plan != null && plan.bounds().intersects(minX, minZ, maxX, maxZ)) {
                        result.add(plan);
                    }
                }
            }
        }
        result.sort(Comparator.comparingInt(SubterrainPlan::priority).reversed().thenComparing(SubterrainPlan::id));
        return List.copyOf(result);
    }

    public SubterrainCell sample(int x, int y, int z) {
        if (isEmpty() || y < minY || y >= maxYExclusive) {
            return SubterrainCell.OUTSIDE;
        }
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        CandidateTile tile = sampleTile.get();
        if (tile == null || tile.chunkX() != chunkX || tile.chunkZ() != chunkZ) {
            long baseX = (long) chunkX * 16;
            long baseZ = (long) chunkZ * 16;
            tile = new CandidateTile(chunkX, chunkZ, plansForBounds(
                    (int) Math.max(Integer.MIN_VALUE, baseX - 1),
                    (int) Math.max(Integer.MIN_VALUE, baseZ - 1),
                    (int) Math.min(Integer.MAX_VALUE, baseX + 16),
                    (int) Math.min(Integer.MAX_VALUE, baseZ + 16)));
            sampleTile.set(tile);
        }
        return sample(tile.plans(), x, y, z);
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

    private SubterrainCell raw(List<SubterrainPlan> candidates, int x, int y, int z) {
        SubterrainCell selected = SubterrainCell.OUTSIDE;
        for (SubterrainPlan plan : candidates) {
            SubterrainCell cell = plan.sample(x, y, z);
            if (cell.solid()) {
                return cell;
            }
            if (!selected.owned() && cell.owned()) {
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
        int bottomMargin = definition.height() / 2 + 3;
        int topMargin = definition.height() - definition.height() / 2 + 3
                + (definition.family() == IrisSubterrainFamily.LAVA_TUBE ? definition.chimneyHeight() : 0);
        long low = (long) Math.max(minY, definition.minY()) + bottomMargin;
        long high = (long) Math.min(maxYExclusive - 1, definition.maxY()) - topMargin;
        if (low > high || x < Integer.MIN_VALUE + spacing || x > Integer.MAX_VALUE - spacing
                || z < Integer.MIN_VALUE + spacing || z > Integer.MAX_VALUE - spacing) {
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

    public record Options(List<IrisSubterrainFeature> features, long seed, int minY, int maxYExclusive) {
        public Options {
            features = List.copyOf(Objects.requireNonNull(features));
        }
    }

    private record CandidateTile(int chunkX, int chunkZ, List<SubterrainPlan> plans) {
    }

    record Definition(String id, IrisSubterrainFamily family, IrisSubterrainFluid fluid, String biome, int spacing, double probability,
                      int length, int radius, int height, int fluidDepth, int chimneyHeight, int terraceCount,
                      int pillarSpacing, double formationFraction, String solid, int priority,
                      int minY, int maxY, long seedSalt) {
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
            long salt = 0xcbf29ce484222325L;
            for (int i = 0; i < id.length(); i++) {
                salt = (salt ^ id.charAt(i)) * 0x100000001b3L;
            }
            return new Definition(id, family, fluid, biome,
                    feature.getSpacing(), feature.getProbability(), length, radius, height, feature.getFluidDepth(),
                    chimney, feature.getTerraceCount(), feature.getPillarSpacing(), feature.getFormationFraction(),
                    solid, feature.getPriority(), (int) Math.ceil(feature.getWorldYRange().getMin()),
                    (int) Math.floor(feature.getWorldYRange().getMax()), salt);
        }
    }
}
