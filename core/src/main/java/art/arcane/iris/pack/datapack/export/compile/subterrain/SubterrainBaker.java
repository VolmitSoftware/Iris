package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainBounds;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.subterrain.SubterrainSurfaceMaterials;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.object.WideObjectStructures;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SubterrainBaker {
    public static final int VARIANTS = 3;
    public static final int MAX_REACH = WideObjectStructures.MAX_DISTANCE;
    public static final VanillaBlockState NO_BLOCK = VanillaBlockState.parse("minecraft:structure_void");
    private static final VanillaBlockState CAVE_AIR = VanillaBlockState.parse("minecraft:cave_air");
    private static final VanillaBlockState WATER = VanillaBlockState.parse("minecraft:water");
    private static final VanillaBlockState LAVA = VanillaBlockState.parse("minecraft:lava");
    private static final ProceduralStream<Double> FLAT_SLOPE = ProceduralStream.of((x, z) -> 0.0, Interpolated.DOUBLE);
    private static final int SEARCH_COLUMNS = 64;
    private static final int SEARCH_ROWS = 64;

    private final long seed;
    private final IrisData data;
    private final IrisDimension dimension;
    private final BlockStateResolver blocks;
    private final List<ExportDiagnostic> diagnostics;
    private final int minY;
    private final int maxYExclusive;
    private final Map<String, IrisBiome> biomes = new HashMap<>();

    public SubterrainBaker(PackCompileContext context, long seed) {
        this.seed = seed;
        data = context.data();
        dimension = context.dimension();
        blocks = BlockStateResolver.forContext(context);
        diagnostics = context.diagnostics();
        DimensionDef exported = context.dimensionDef();
        minY = Math.max(dimension.getMinHeight(), exported.minY());
        maxYExclusive = Math.min(dimension.getMaxHeight(), exported.minY() + exported.height());
    }

    public Optional<BakedFeature> bake(IrisSubterrainFeature feature, String resource) {
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), seed, minY, maxYExclusive,
                regionResolver(feature)));
        List<SubterrainPlan> plans = search(planner, feature.getSpacing());
        IrisRange range = feature.getWorldYRange();
        if (plans.isEmpty()) {
            String reason = feature.getProbability() > 0
                    ? "does not fit its world Y range " + range.getMin() + ".." + range.getMax() + " inside the dimension"
                    : "has probability 0";
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.STRUCTURE_PLACEMENT, resource,
                    "The room never generates because it " + reason + ", so it is not exported"));
            return Optional.empty();
        }
        RNG random = new RNG(seed);
        List<BakedRoom> rooms = new ArrayList<>(plans.size());
        long clipped = 0;
        for (SubterrainPlan plan : plans) {
            Bake bake = new Bake(planner, plan, random, resource);
            rooms.add(bake.room());
            clipped += bake.clipped;
        }
        if (clipped > 0) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.OBJECT_BOUNDS, resource,
                    clipped + " room blocks lie more than " + MAX_REACH + " blocks from the room center and are left out of the templates"));
        }
        SubterrainPlan first = plans.getFirst();
        int below = first.centerY() - first.bounds().minY() + 1;
        int above = first.bounds().maxY() - first.centerY() + 1;
        int lowest = Math.max(minY, (int) Math.ceil(range.getMin())) + below;
        int highest = Math.min(maxYExclusive - 1, (int) Math.floor(range.getMax())) - above;
        return Optional.of(new BakedFeature(feature.getId(), rooms, lowest, highest));
    }

    private static SubterrainPlanner.RegionResolver regionResolver(IrisSubterrainFeature feature) {
        TreeSet<String> allowed = new TreeSet<>(feature.getAllowedRegions());
        String region = allowed.isEmpty() ? "" : allowed.first();
        return (x, z) -> region;
    }

    private static List<SubterrainPlan> search(SubterrainPlanner planner, int spacing) {
        Map<String, SubterrainPlan> found = new TreeMap<>();
        int width = spacing * SEARCH_COLUMNS;
        for (int row = 0; row < SEARCH_ROWS && found.size() < VARIANTS; row++) {
            int minZ = row * spacing;
            for (SubterrainPlan plan : planner.plansForBounds(0, minZ, width - 1, minZ + spacing - 1)) {
                found.putIfAbsent(plan.id(), plan);
            }
        }
        List<SubterrainPlan> plans = new ArrayList<>(VARIANTS);
        for (SubterrainPlan plan : found.values()) {
            if (plans.size() == VARIANTS) {
                break;
            }
            plans.add(plan);
        }
        return plans;
    }

    private NativeBlockState painted(int x, int y, int z, SubterrainCell cell, SubterrainSurfaceMaterials surfaces, RNG random) {
        String biomeKey = cell.room().biome();
        if (biomeKey.isEmpty()) {
            return null;
        }
        SubterrainSurfaceMaterials.Surface surface = surfaces.surface(x, y, z, cell);
        IrisBiome biome = biomes.computeIfAbsent(biomeKey, key -> data.getBiomeLoader().load(key, false));
        if (surface == SubterrainSurfaceMaterials.Surface.INTERIOR || biome == null) {
            return null;
        }
        int height = y - dimension.getMinHeight();
        NativeBlockState candidate = switch (surface) {
            case WALL -> biome.getWall().get(random, x, height, z, data);
            case FLOOR -> first(biome.generateLayersWithSlope(dimension, x, z, random, 1, height + 1, data, FLAT_SLOPE));
            case CEILING -> first(biome.generateCeilingLayers(dimension, x, z, random, 1, height - 1, data, null));
            case INTERIOR -> null;
        };
        return SubterrainRasterizer.isRetainedSolid(candidate) ? candidate : null;
    }

    private static NativeBlockState first(KList<NativeBlockState> layers) {
        return layers.isEmpty() ? null : layers.getFirst();
    }

    private VanillaBlockState vanilla(NativeBlockState painted, NativeBlockState solid, String resource) {
        Optional<VanillaBlockState> resolved = painted == null ? Optional.empty() : blocks.resolve(painted.key(), resource);
        return resolved.or(() -> blocks.resolve(solid.key(), resource)).orElseThrow(() ->
                new IllegalStateException("Subterrain solid block '" + solid.key() + "' of " + resource + " has no Minecraft block state"));
    }

    private final class Bake {
        private final List<SubterrainPlan> plans;
        private final SubterrainPlanner planner;
        private final SubterrainPlan plan;
        private final RNG random;
        private final String resource;
        private final SubterrainSurfaceMaterials surfaces;
        private final List<TemplateBlock> core = new ArrayList<>();
        private final List<TemplateBlock> shell = new ArrayList<>();
        private VanillaBlockState anchorState = NO_BLOCK;
        private long clipped;

        private Bake(SubterrainPlanner planner, SubterrainPlan plan, RNG random, String resource) {
            this.planner = planner;
            this.plan = plan;
            this.random = random;
            this.resource = resource;
            plans = List.of(plan);
            surfaces = new SubterrainSurfaceMaterials(planner, plans, dimension, null, data, random, dimension.getMinHeight());
        }

        private BakedRoom room() {
            SubterrainBounds bounds = plan.bounds();
            int lowX = Math.max(bounds.minX(), plan.centerX() - MAX_REACH);
            int highX = Math.min(bounds.maxX(), plan.centerX() + MAX_REACH);
            int lowZ = Math.max(bounds.minZ(), plan.centerZ() - MAX_REACH);
            int highZ = Math.min(bounds.maxZ(), plan.centerZ() + MAX_REACH);
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    boolean reachable = x >= lowX && x <= highX && z >= lowZ && z <= highZ;
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        SubterrainCell cell = planner.sample(plans, x, y, z);
                        if (!cell.owned()) {
                            continue;
                        }
                        if (reachable) {
                            add(x - lowX, y - bounds.minY(), z - lowZ, x, y, z, cell);
                        } else {
                            clipped++;
                        }
                    }
                }
            }
            int sizeX = highX - lowX + 1;
            int sizeY = bounds.maxY() - bounds.minY() + 1;
            int sizeZ = highZ - lowZ + 1;
            return new BakedRoom(lowX, bounds.minY(), lowZ, new TemplateVolume(sizeX, sizeY, sizeZ, core),
                    new TemplateVolume(sizeX, sizeY, sizeZ, shell), plan.centerX() - lowX, plan.centerY() - bounds.minY(),
                    plan.centerZ() - lowZ, anchorState);
        }

        private void add(int localX, int localY, int localZ, int x, int y, int z, SubterrainCell cell) {
            VanillaBlockState state = switch (cell.kind()) {
                case AIR -> CAVE_AIR;
                case WATER -> WATER;
                case LAVA -> LAVA;
                case SOLID -> vanilla(painted(x, y, z, cell, surfaces, random), SubterrainRasterizer.state(cell), resource);
                case OUTSIDE -> throw new IllegalStateException("Outside cells carry no block");
            };
            TemplateBlock block = new TemplateBlock(localX, localY, localZ, state, null);
            if (cell.solid() && !cell.room().reservedSolid()) {
                shell.add(block);
            } else if (x == plan.centerX() && y == plan.centerY() && z == plan.centerZ()) {
                anchorState = state;
            } else {
                core.add(block);
            }
        }
    }
}
