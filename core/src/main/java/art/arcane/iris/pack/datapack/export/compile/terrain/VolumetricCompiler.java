package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisTerrain3D;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.compile.noise.ValueDistributionFit;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

public final class VolumetricCompiler {
    private static final String SCOPE = "volume";
    private static final String VARIANT_PREFIX = "terrain/volume/terrain3d/";
    private static final double STYLE_SCALE = 64.0;
    private static final double CRACK_VERTICAL_RATIO = 0.25;
    private static final int MARGINAL_SAMPLES = 8192;
    private static final int MARGINAL_SPAN = 1 << 20;
    private static final long MARGINAL_SEED = 0x7E33A1D5L;

    private final StyleCompiler styles;
    private final List<ExportDiagnostic> diagnostics;
    private final Map<Marginal, List<Field.Knot>> marginals = new HashMap<>();

    public VolumetricCompiler(StyleCompiler styles, List<ExportDiagnostic> diagnostics) {
        this.styles = Objects.requireNonNull(styles, "styles");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public Field compile(TerrainHeight terrain, BiomeCodes codes, IrisDimension dimension, IrisData data, NoiseRegistry registry, ExportCapabilities capabilities) {
        Field height = Objects.requireNonNull(terrain, "terrain").height();
        Map<String, IrisBiome> biomes = TerrainBiomes.resolve(codes, data);
        BandTreeCompiler bands = new BandTreeCompiler(codes, SCOPE);
        GeneratorCompiler generators = new GeneratorCompiler(styles, registry, capabilities, diagnostics);
        Field blockY = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, dimension.getMinHeight(), dimension.getMaxHeight(),
                dimension.getMinHeight(), dimension.getMaxHeight());
        double fluidHeight = dimension.getMinHeight() + dimension.getFluidHeight();
        Sampling sampling = new Sampling(data, registry, capabilities, dimension.getMinHeight(), dimension.getMaxHeight());
        Field volume = terrain3d(height, terrain.slope(), biomes, bands, generators, blockY, fluidHeight, sampling);
        List<Field> islands = new IslandCompiler(height, bands, generators, blockY, dimension, data, capabilities, diagnostics).compile(biomes);
        for (Field island : islands) {
            volume = new Field.Binary(Field.BinaryOp.MAX, volume, island);
        }
        return volume;
    }

    private Field terrain3d(Field height, Field slope, Map<String, IrisBiome> biomes, BandTreeCompiler bands, GeneratorCompiler generators,
                            Field blockY, double fluidHeight, Sampling sampling) {
        Map<IrisTerrain3D, Map<String, Double>> variants = new LinkedHashMap<>();
        for (Map.Entry<String, IrisBiome> entry : biomes.entrySet()) {
            IrisTerrain3D terrain = entry.getValue().getTerrain3D();
            if (terrain == null || !terrain.isEnabled() || terrain.getAmplitude() + terrain.getCrackDepth() <= 0.0) {
                continue;
            }
            terrain.validate();
            variants.computeIfAbsent(terrain, ignored -> new LinkedHashMap<>()).put(entry.getKey(), 1.0);
        }
        List<Field> terms = new ArrayList<>(variants.size());
        int index = 0;
        for (Map.Entry<IrisTerrain3D, Map<String, Double>> variant : variants.entrySet()) {
            IrisTerrain3D terrain = variant.getKey();
            String name = VARIANT_PREFIX + index;
            Field presence = FieldMacros.share(name + "/presence", bands.hard(variant.getValue()));
            Field mask = FieldMacros.share(name + "/mask", FieldMacros.product(presence, strength(terrain, height, slope, fluidHeight)));
            Field fade = fade(terrain, blockY, fluidHeight);
            Field shape = shape(terrain, generators, "terrain3d/" + index, sampling);
            terms.add(FieldMacros.product(mask, FieldMacros.product(fade, shape)));
            index++;
        }
        return FieldMacros.sum(terms);
    }

    private Field signed(Marginal marginal, double reach, GeneratorCompiler generators, String path, Sampling sampling) {
        Field unit = FieldMacros.lift(generators.style(marginal.style(), marginal.horizontalScale() / STYLE_SCALE, path), marginal.verticalRatio());
        if (unit instanceof Field.Const) {
            return new Field.Clamp(FieldMacros.sum(List.of(FieldMacros.scale(unit, 2.0 * reach), new Field.Const(-reach))), -reach, reach);
        }
        List<Field.Knot> knots = marginals.computeIfAbsent(marginal, key -> fit(key, unit, sampling));
        List<Field.Knot> scaled = new ArrayList<>(knots.size());
        for (Field.Knot knot : knots) {
            scaled.add(new Field.Knot(knot.location(), FieldMacros.scale(knot.value(), reach), knot.derivative() * reach));
        }
        return new Field.Clamp(new Field.Curve(unit, scaled), -reach, reach);
    }

    private static List<Field.Knot> fit(Marginal marginal, Field unit, Sampling sampling) {
        CNG iris = marginal.style().createNoCache(new RNG(MARGINAL_SEED), sampling.data());
        Map<String, NoiseDef> noises = sampling.registry().byReference();
        double frequency = STYLE_SCALE / marginal.horizontalScale();
        SplittableRandom random = new SplittableRandom(MARGINAL_SEED);
        double[] exported = new double[MARGINAL_SAMPLES];
        double[] expected = new double[MARGINAL_SAMPLES];
        for (int index = 0; index < MARGINAL_SAMPLES; index++) {
            int x = random.nextInt(-MARGINAL_SPAN, MARGINAL_SPAN);
            int y = random.nextInt(sampling.minY(), sampling.maxY());
            int z = random.nextInt(-MARGINAL_SPAN, MARGINAL_SPAN);
            exported[index] = FieldEvaluator.sample(unit, noises, MARGINAL_SEED, sampling.capabilities().target(), x, y, z);
            expected[index] = Math.clamp(iris.noiseFastSigned3D(x * frequency, y * frequency * marginal.verticalRatio(), z * frequency), -1.0, 1.0);
        }
        return ValueDistributionFit.fit(exported, expected);
    }

    private static Field strength(IrisTerrain3D terrain, Field height, Field slope, double fluidHeight) {
        double start = fluidHeight + terrain.getFluidClearance();
        Field elevation = FieldMacros.smoothstep(height, start, start + terrain.getFluidFade());
        if (terrain.getMinimumSlope() <= 0.0) {
            return elevation;
        }
        Field steepness = FieldMacros.smoothstep(slope, terrain.getMinimumSlope(), terrain.getMinimumSlope() + terrain.getSlopeFade());
        return FieldMacros.product(elevation, steepness);
    }

    private static Field fade(IrisTerrain3D terrain, Field blockY, double fluidHeight) {
        double start = fluidHeight + terrain.getFluidClearance();
        return FieldMacros.smoothstep(blockY, start, start + terrain.getFluidFade());
    }

    private Field shape(IrisTerrain3D terrain, GeneratorCompiler generators, String path, Sampling sampling) {
        List<Field> parts = new ArrayList<>(2);
        double amplitude = terrain.getAmplitude();
        if (amplitude > 0.0) {
            parts.add(signed(new Marginal(terrain.getDensityStyle(), terrain.getHorizontalScale(), terrain.getHorizontalScale() / terrain.getVerticalScale()),
                    amplitude, generators, path + "/density", sampling));
        }
        double depth = terrain.getCrackDepth();
        if (depth > 0.0) {
            Field distance = signed(new Marginal(terrain.getCrackStyle(), terrain.getCrackScale(), CRACK_VERTICAL_RATIO), terrain.getCrackScale() / 2.0,
                    generators, path + "/crack", sampling);
            Field closeness = FieldMacros.sum(List.of(new Field.Const(1.0),
                    FieldMacros.scale(new Field.Unary(Field.UnaryOp.ABS, distance), -1.0 / terrain.getCrackWidth())));
            Field ridge = new Field.Binary(Field.BinaryOp.MAX, new Field.Const(0.0), closeness);
            parts.add(FieldMacros.scale(new Field.Unary(Field.UnaryOp.SQUARE, ridge), -depth));
        }
        return FieldMacros.sum(parts);
    }

    private record Marginal(IrisGeneratorStyle style, double horizontalScale, double verticalRatio) {
    }

    private record Sampling(IrisData data, NoiseRegistry registry, ExportCapabilities capabilities, int minY, int maxY) {
    }
}
