package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDepositBiomeScope;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.decoration.IrisDepositHeightDistribution;
import art.arcane.iris.generation.decoration.IrisDepositPlacementScope;
import art.arcane.iris.generation.decoration.IrisDepositShape;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class DepositCompiler {
    private static final int MAX_ORE_SIZE = 64;
    private static final int MAX_COUNT = 256;
    private static final int MIN_ANCHOR = -2032;
    private static final int MAX_ANCHOR = 2031;
    private static final String HOST_TAG = "deposit_host";
    private static final List<String> HOST_TAG_BASE = List.of("#minecraft:base_stone_overworld", "#minecraft:dirt", "#minecraft:sand");
    private static final String DEEPSLATE = "minecraft:deepslate";
    private static final String DEEPSLATE_PREFIX = "minecraft:deepslate_";
    private static final Set<String> DEEPSLATE_ORES = Set.of("coal", "emerald", "diamond", "copper", "gold", "iron", "lapis", "redstone");
    private static final Set<String> DEEPSLATE_HOSTS = Set.of(DEEPSLATE, "minecraft:deepslate_bricks", "minecraft:deepslate_brick_slab",
            "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_wall", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_slab",
            "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_wall", "minecraft:cracked_deepslate_tiles", "minecraft:deepslate_coal_ore",
            "minecraft:deepslate_iron_ore", "minecraft:deepslate_copper_ore", "minecraft:deepslate_diamond_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:deepslate_gold_ore", "minecraft:deepslate_lapis_ore", "minecraft:deepslate_redstone_ore");

    private final PackCompileContext context;
    private final ExportedBiomes biomes;
    private final BlockStateResolver blocks;
    private final FeatureCatalog catalog;
    private final WorldgenSyntax syntax;
    private String hostTag;

    public DepositCompiler(PackCompileContext context, ExportedBiomes biomes, BlockStateResolver blocks, FeatureCatalog catalog) {
        this.context = context;
        this.biomes = biomes;
        this.blocks = blocks;
        this.catalog = catalog;
        this.syntax = catalog.syntax();
    }

    public void compile() {
        List<String> everyBiome = biomes.inCodeOrder(biomes.byId().keySet());
        String dimensionResource = "dimensions/" + context.dimension().getLoadKey();
        for (IrisDepositGenerator deposit : list(context.dimension().getDeposits())) {
            place(deposit, everyBiome, dimensionResource);
        }
        TreeMap<String, List<String>> regionBiomes = new TreeMap<>();
        TreeMap<String, IrisRegion> regions = new TreeMap<>();
        for (String biomeId : everyBiome) {
            IrisRegion region = biomes.region(biomeId);
            if (region != null) {
                regions.put(region.getLoadKey(), region);
                regionBiomes.computeIfAbsent(region.getLoadKey(), ignored -> new ArrayList<>()).add(biomeId);
            }
        }
        for (Map.Entry<String, IrisRegion> region : regions.entrySet()) {
            for (IrisDepositGenerator deposit : list(region.getValue().getDeposits())) {
                place(deposit, regionBiomes.get(region.getKey()), "regions/" + region.getKey());
            }
        }
        for (Map.Entry<String, IrisBiome> biome : biomes.byId().entrySet()) {
            for (IrisDepositGenerator deposit : list(biome.getValue().getDeposits())) {
                place(deposit, List.of(biome.getKey()), biomes.resource(biome.getKey()));
            }
        }
    }

    private void place(IrisDepositGenerator deposit, List<String> candidates, String resource) {
        List<String> biomeIds = filtered(deposit, candidates);
        if (biomeIds.isEmpty() || deposit.getMaxPerChunk() <= 0 || deposit.getSpawnChance() <= 0 || deposit.getPerClumpSpawnChance() <= 0) {
            return;
        }
        List<VanillaBlockState> states = new ArrayList<>();
        for (IrisBlockData block : list(deposit.getPalette())) {
            blocks.resolve(block, resource).ifPresent(state -> {
                if (!states.contains(state)) {
                    states.add(state);
                }
            });
        }
        if (states.isEmpty()) {
            return;
        }
        String feature = feature(deposit, states, resource);
        String placed = catalog.placed("deposit/" + path(states.getFirst()), feature, placement(deposit, biomeIds, resource));
        for (String biomeId : biomeIds) {
            context.features().add(biomeId, GenerationStep.UNDERGROUND_ORES, placed);
        }
    }

    private String feature(IrisDepositGenerator deposit, List<VanillaBlockState> states, String resource) {
        String type = deposit.getShape() == IrisDepositShape.VANILLA_SCATTERED ? "minecraft:scattered_ore" : "minecraft:ore";
        int size = (int) Math.round((Math.max(0, deposit.getMinSize()) + Math.max(0, deposit.getMaxSize())) / 2.0);
        if (size > MAX_ORE_SIZE) {
            report(resource, ExportLimit.DEPOSIT_SIZE, ExportDiagnostic.Severity.WARNING,
                    "Deposit clumps of " + size + " blocks export as Minecraft ore veins of " + MAX_ORE_SIZE + " blocks");
            size = MAX_ORE_SIZE;
        }
        List<JsonObject> hosts = hosts(deposit, resource);
        List<String> ores = new ArrayList<>(states.size());
        for (VanillaBlockState state : states) {
            JsonArray targets = new JsonArray(hosts.size() + 1);
            VanillaBlockState deepslateOre = stoneVariant(state, true);
            if (hosts.size() == 1 && "minecraft:tag_match".equals(hosts.getFirst().get("predicate_type").getAsString()) && !deepslateOre.equals(state)) {
                JsonObject deepslate = new JsonObject();
                deepslate.addProperty("predicate_type", "minecraft:block_match");
                deepslate.addProperty("block", DEEPSLATE);
                targets.add(target(deepslate, deepslateOre));
            }
            for (JsonObject host : hosts) {
                boolean deepslateHost = host.has("block") && DEEPSLATE_HOSTS.contains(host.get("block").getAsString());
                targets.add(target(host, stoneVariant(state, deepslateHost)));
            }
            JsonObject config = new JsonObject();
            config.addProperty("size", Math.max(0, size));
            config.addProperty("discard_chance_on_air_exposure", (float) Math.max(0.0, Math.min(1.0, deposit.getDiscardChanceOnAirExposure())));
            config.add("targets", targets);
            ores.add(catalog.feature("deposit/" + path(state), type, config));
        }
        if (ores.size() == 1) {
            return ores.getFirst();
        }
        JsonArray features = new JsonArray(ores.size());
        for (String ore : ores) {
            features.add(catalog.inlinePlaced(ore, new JsonArray()));
        }
        JsonObject selector = new JsonObject();
        selector.add("features", features);
        return catalog.feature("deposit/" + path(states.getFirst()) + "_mix", "minecraft:simple_random_selector", selector);
    }

    private JsonObject target(JsonObject host, VanillaBlockState state) {
        JsonObject target = new JsonObject();
        target.add("target", host.deepCopy());
        target.add("state", syntax.blockState(state));
        return target;
    }

    private static VanillaBlockState stoneVariant(VanillaBlockState ore, boolean deepslate) {
        String name = ore.name();
        boolean deep = name.startsWith(DEEPSLATE_PREFIX);
        String base = deep ? name.substring(DEEPSLATE_PREFIX.length()) : name.substring(name.indexOf(':') + 1);
        if (deep == deepslate || !base.endsWith("_ore") || !DEEPSLATE_ORES.contains(base.substring(0, base.length() - "_ore".length()))) {
            return ore;
        }
        return new VanillaBlockState(deepslate ? DEEPSLATE_PREFIX + base : "minecraft:" + base, ore.properties());
    }

    private JsonArray placement(IrisDepositGenerator deposit, List<String> biomeIds, String resource) {
        int minCount = Math.max(0, Math.min(deposit.getMinPerChunk(), deposit.getMaxPerChunk()));
        int maxCount = Math.max(minCount, deposit.getMaxPerChunk());
        if (maxCount > MAX_COUNT) {
            report(resource, ExportLimit.DEPOSIT_SIZE, ExportDiagnostic.Severity.WARNING,
                    "Deposits place at most " + MAX_COUNT + " clumps per chunk");
            maxCount = MAX_COUNT;
            minCount = Math.min(minCount, maxCount);
        }
        double spawnChance = Math.min(1.0, deposit.getSpawnChance());
        double clumpChance = Math.min(1.0, deposit.getPerClumpSpawnChance());
        JsonArray placement = new JsonArray();
        if (spawnChance < 1.0) {
            placement.add(Placements.randomChance(spawnChance));
        }
        placement.add(Placements.count(Placements.intRange(minCount, maxCount)));
        placement.add(Placements.inSquare());
        boolean surfaceScope = deposit.getBiomeScope() == IrisDepositBiomeScope.SURFACE;
        if (surfaceScope) {
            placement.add(Placements.heightmap(Placements.OCEAN_FLOOR));
            placement.add(Placements.filter(Placements.matchingBiomes(biomeIds)));
        }
        placement.add(Placements.heightRange(height(deposit)));
        IrisDepositPlacementScope scope = deposit.getPlacementScope() == null ? IrisDepositPlacementScope.TERRAIN : deposit.getPlacementScope();
        if (scope == IrisDepositPlacementScope.TERRAIN) {
            placement.add(Placements.surfaceRelative(Placements.OCEAN_FLOOR, -(Math.max(0, deposit.getSurfaceClearance()) + 1)));
        } else if (scope == IrisDepositPlacementScope.ABOVE_TERRAIN) {
            report(resource, ExportLimit.DEPOSIT_SCOPE, ExportDiagnostic.Severity.INFO,
                    "Deposits limited to above-terrain placement also generate inside overhangs and floating terrain within their height band");
        }
        if (clumpChance < 1.0) {
            placement.add(Placements.randomChance(clumpChance));
        }
        if (!surfaceScope) {
            placement.add(Placements.biome());
        }
        return placement;
    }

    private JsonObject height(IrisDepositGenerator deposit) {
        int min = clampAnchor(Math.min(deposit.getMinHeight(), deposit.getMaxHeight()));
        int max = clampAnchor(Math.max(deposit.getMinHeight(), deposit.getMaxHeight()));
        return deposit.getHeightDistribution() == IrisDepositHeightDistribution.TRIANGLE
                ? Placements.trapezoidHeight(min, max)
                : Placements.uniformHeight(min, max);
    }

    private List<JsonObject> hosts(IrisDepositGenerator deposit, String resource) {
        List<JsonObject> hosts = new ArrayList<>();
        TreeSet<String> names = new TreeSet<>();
        for (String raw : list(deposit.getReplaceableBlocks())) {
            blocks.resolve(raw, resource).ifPresent(state -> names.add(state.name()));
        }
        for (String name : names) {
            JsonObject host = new JsonObject();
            host.addProperty("predicate_type", "minecraft:block_match");
            host.addProperty("block", name);
            hosts.add(host);
        }
        if (!hosts.isEmpty()) {
            return hosts;
        }
        JsonObject host = new JsonObject();
        host.addProperty("predicate_type", "minecraft:tag_match");
        host.addProperty("tag", hostTag());
        return List.of(host);
    }

    private String hostTag() {
        if (hostTag != null) {
            return hostTag;
        }
        TreeSet<String> layerBlocks = new TreeSet<>();
        for (Map.Entry<String, IrisBiome> biome : biomes.byId().entrySet()) {
            for (IrisBiomePaletteLayer layer : list(biome.getValue().getLayers())) {
                for (IrisBlockData block : list(layer.getPalette())) {
                    Optional<VanillaBlockState> state = blocks.resolve(block, biomes.resource(biome.getKey()));
                    state.ifPresent(value -> layerBlocks.add(value.name()));
                }
            }
        }
        List<String> values = new ArrayList<>(HOST_TAG_BASE);
        values.add(context.dimensionDef().defaultBlock());
        for (String block : layerBlocks) {
            if (!values.contains(block) && !block.equals("minecraft:bedrock")) {
                values.add(block);
            }
        }
        hostTag = catalog.blockTag(HOST_TAG, values);
        return hostTag;
    }

    private List<String> filtered(IrisDepositGenerator deposit, List<String> candidates) {
        List<String> included = list(deposit.getIncludedBiomes());
        List<String> excluded = list(deposit.getExcludedBiomes());
        List<String> biomeIds = new ArrayList<>(candidates.size());
        for (String biomeId : candidates) {
            String loadKey = biomes.loadKey(biomeId);
            if ((included.isEmpty() || included.contains(loadKey)) && !excluded.contains(loadKey)) {
                biomeIds.add(biomeId);
            }
        }
        return biomeIds;
    }

    private void report(String resource, ExportLimit limit, ExportDiagnostic.Severity severity, String message) {
        ExportDiagnostic diagnostic = new ExportDiagnostic(severity, limit, resource, message);
        if (!context.diagnostics().contains(diagnostic)) {
            context.diagnostics().add(diagnostic);
        }
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static int clampAnchor(int value) {
        return Math.max(MIN_ANCHOR, Math.min(MAX_ANCHOR, value));
    }

    private static String path(VanillaBlockState state) {
        return state.name().substring(state.name().indexOf(':') + 1);
    }
}
