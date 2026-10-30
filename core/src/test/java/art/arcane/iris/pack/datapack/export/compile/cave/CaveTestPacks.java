package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveFieldModule;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class CaveTestPacks {
    static final String NAMESPACE = "iris_export";
    static final String SURFACE_BIOME = "surface";
    static final long SEED = 1337L;
    static final int MIN_Y = -64;
    static final int MAX_Y = 192;
    private static final Gson GSON = new Gson();

    private CaveTestPacks() {
    }

    static JsonObject dimension(int lavaHeight) {
        JsonObject height = new JsonObject();
        height.addProperty("min", MIN_Y);
        height.addProperty("max", MAX_Y);
        JsonArray regions = new JsonArray();
        regions.add("region");
        JsonObject dimension = new JsonObject();
        dimension.add("regions", regions);
        dimension.add("dimensionHeight", height);
        dimension.addProperty("fluidHeight", 63);
        dimension.addProperty("caveLavaHeight", lavaHeight);
        return dimension;
    }

    static JsonObject band(String id, String biome, int min, int max) {
        JsonObject range = new JsonObject();
        range.addProperty("min", min);
        range.addProperty("max", max);
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("biome", biome);
        entry.add("worldYRange", range);
        return entry;
    }

    static JsonObject region(List<String> caveBiomes, IrisCaveProfile profile) {
        JsonArray surface = new JsonArray();
        surface.add(SURFACE_BIOME);
        JsonArray caves = new JsonArray();
        for (String key : caveBiomes) {
            caves.add(key);
        }
        JsonObject region = new JsonObject();
        region.add("landBiomes", surface);
        region.add("seaBiomes", surface.deepCopy());
        region.add("caveBiomes", caves);
        if (profile != null) {
            region.add("caveProfile", GSON.toJsonTree(profile));
        }
        return region;
    }

    static File pack(File folder, JsonObject dimension, JsonObject region, Map<String, IrisCaveProfile> biomes) throws IOException {
        CompatTestPlatform.write(folder, "dimensions/" + ExportTestPacks.DIMENSION + ".json", GSON.toJson(dimension));
        CompatTestPlatform.write(folder, "regions/region.json", GSON.toJson(region));
        CompatTestPlatform.write(folder, "biomes/" + SURFACE_BIOME + ".json", "{\"name\":\"Surface\"}");
        for (Map.Entry<String, IrisCaveProfile> entry : new TreeMap<>(biomes).entrySet()) {
            JsonObject biome = new JsonObject();
            biome.addProperty("name", entry.getKey());
            biome.add("caveProfile", GSON.toJsonTree(entry.getValue()));
            CompatTestPlatform.write(folder, "biomes/" + entry.getKey() + ".json", GSON.toJson(biome));
        }
        return folder;
    }

    static JsonElement json(IrisCaveProfile profile) {
        return GSON.toJsonTree(profile);
    }

    static IrisCaveProfile profile() {
        return new IrisCaveProfile()
                .setEnabled(true)
                .setVerticalRange(new IrisRange(0, MAX_Y - MIN_Y))
                .setVerticalEdgeFade(0)
                .setBaseDensityStyle(style(NoiseStyle.SIMPLEX, 0.6))
                .setDetailDensityStyle(style(NoiseStyle.SIMPLEX, 0.3))
                .setBaseWeight(1.0)
                .setDetailWeight(0.0)
                .setDensityThreshold(new IrisStyledRange(0.0, 0.0, style(NoiseStyle.SIMPLEX, 1.0)))
                .setThresholdBias(0.0)
                .setSurfaceClearance(0)
                .setAllowSurfaceBreak(false)
                .setAllowFluid(false)
                .setAllowLava(false)
                .setModules(new KList<>());
    }

    static IrisCaveProfile constantProfile(double threshold) {
        return profile().setDensityThreshold(new IrisStyledRange(threshold, threshold, style(NoiseStyle.SIMPLEX, 1.0)));
    }

    static IrisCaveFieldModule module(NoiseStyle style, double weight, double threshold, boolean invert, IrisRange range) {
        return new IrisCaveFieldModule()
                .setStyle(style(style, 0.5))
                .setWeight(weight)
                .setThreshold(threshold)
                .setInvert(invert)
                .setVerticalRange(range);
    }

    static IrisGeneratorStyle style(NoiseStyle style, double zoom) {
        return new IrisGeneratorStyle(style).zoomed(zoom);
    }

    static PackCompileContext context(IrisData data, StyleCompiler styles) {
        PackCompileContext context = ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(styles).build();
        new DimensionStage().apply(context);
        return context;
    }

    static BiomeCodes codes(Field caveCode, List<CodeRange> ranges) {
        List<ClimateLayout.ClimateEntry> entries = new ArrayList<>();
        Map<String, String> loadKeys = new TreeMap<>();
        String surfaceId = NAMESPACE + ":" + SURFACE_BIOME;
        entries.add(new ClimateLayout.ClimateEntry(surfaceId, ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 1.0));
        loadKeys.put(surfaceId, SURFACE_BIOME);
        for (CodeRange range : ranges) {
            String id = NAMESPACE + ":" + range.loadKey();
            entries.add(new ClimateLayout.ClimateEntry(id, ClimateLayout.ClimateAxis.DEPTH, range.min(), range.max()));
            loadKeys.put(id, range.loadKey());
        }
        Field zero = new Field.Const(0.0);
        return new BiomeCodes(zero, caveCode, zero, new ClimateLayout(entries), loadKeys);
    }

    static Field xGradient() {
        return new Field.Coord(Axis.X, Field.Tiling.CLAMP, -1000, 1000, -1.0, 1.0);
    }

    static Map<String, NoiseDef> noises(NoiseRegistry registry) {
        Map<String, NoiseDef> noises = new TreeMap<>();
        for (NoiseDef definition : registry.definitions()) {
            noises.put(registry.namespace() + ":" + definition.id(), definition);
        }
        return noises;
    }

    static double sample(Field field, NoiseRegistry registry, int x, int y, int z) {
        return FieldEvaluator.sample(field, noises(registry), SEED, DataVersion.V26_3, x, y, z);
    }

    static List<Field> children(Field field) {
        List<Field> children = new ArrayList<>();
        switch (field) {
            case Field.Const ignored -> {
            }
            case Field.Coord ignored -> {
            }
            case Field.Ref ignored -> {
            }
            case Field.Noise noise -> {
                addPresent(children, noise.shiftX());
                addPresent(children, noise.shiftY());
                addPresent(children, noise.shiftZ());
            }
            case Field.Binary binary -> {
                children.add(binary.left());
                children.add(binary.right());
            }
            case Field.Unary unary -> children.add(unary.input());
            case Field.Clamp clamp -> children.add(clamp.input());
            case Field.Lerp lerp -> children.addAll(List.of(lerp.alpha(), lerp.first(), lerp.second()));
            case Field.Round round -> children.addAll(List.of(round.input(), round.multiple()));
            case Field.Curve curve -> {
                children.add(curve.input());
                for (Field.Knot knot : curve.knots()) {
                    children.add(knot.value());
                }
            }
            case Field.Partition partition -> {
                children.add(partition.input());
                children.addAll(partition.leaves());
            }
            case Field.Mux mux -> {
                children.add(mux.index());
                children.addAll(mux.cases());
            }
            case Field.Gate gate -> children.addAll(List.of(gate.condition(), gate.expr()));
            case Field.RangeIf rangeIf -> children.addAll(List.of(rangeIf.input(), rangeIf.inRange(), rangeIf.outOfRange()));
            case Field.Grid grid -> children.add(grid.expr());
            case Field.Shared shared -> children.add(shared.expr());
            case Field.Slice slice -> children.add(slice.expr());
            case Field.TopSurface topSurface -> children.add(topSurface.density());
        }
        return children;
    }

    static List<Field> eagerChildren(Field field) {
        return switch (field) {
            case Field.Partition partition -> {
                List<Field> children = new ArrayList<>();
                children.add(partition.input());
                children.addAll(partition.leaves());
                yield children;
            }
            case Field.RangeIf rangeIf -> List.of(rangeIf.input(), rangeIf.inRange(), rangeIf.outOfRange());
            case Field.Binary binary when binary.op() == Field.BinaryOp.MUL -> List.of(binary.left(), binary.right());
            case Field.Lerp lerp -> List.of(lerp.alpha(), lerp.first(), lerp.second());
            default -> List.of();
        };
    }

    static Field caveField(Field caves) {
        Field field = caves instanceof Field.Binary binary && binary.op() == Field.BinaryOp.MAX ? binary.left() : caves;
        if (field instanceof Field.Shared shared && shared.name().equals(DeepCaveCompiler.FIELD)) {
            return shared.expr();
        }
        return caves;
    }

    static List<Field> nodes(Field root) {
        Set<Field> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Field> nodes = new ArrayList<>();
        List<Field> pending = new ArrayList<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Field next = pending.removeLast();
            if (!seen.add(next)) {
                continue;
            }
            nodes.add(next);
            pending.addAll(children(next));
        }
        return nodes;
    }

    private static void addPresent(List<Field> children, Field field) {
        if (field != null) {
            children.add(field);
        }
    }

    record CodeRange(String loadKey, double min, double max) {
    }
}
