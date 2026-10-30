package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeDecision;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeIdSanitizer;
import art.arcane.iris.pack.datapack.export.compile.decoration.BiomeFeatureLists;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.validation.ContentGate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PackCompileContext {
    private static final Field NEUTRAL_VOLUME = new Field.Const(0.0);
    private static final Field NO_CAVES = new Field.Const(1.0e6);
    private static final Field ZERO_CODE = new Field.Const(0.0);
    private static final double CLIMATE_SPAN = 1.0;
    private static final List<String> LEGACY_BIOME_KEYS = List.of("surface_builder", "depth", "scale", "precipitation", "category", "starts");
    private static final StyleCompiler NO_STYLE_COMPILER = (style, seedSalt, registry, capabilities) -> {
        throw new IllegalStateException("No style compiler installed");
    };

    private final IrisData data;
    private final IrisDimension dimension;
    private final ExportCapabilities capabilities;
    private final String namespace;
    private final WorldgenExportMode mode;
    private final boolean objects;
    private final StyleCompiler styles;
    private final NoiseRegistry noises;
    private final List<ExportDiagnostic> diagnostics;
    private final BiomeFeatureLists features = new BiomeFeatureLists();
    private final List<JsonResource> jsonResources = new ArrayList<>();
    private final List<BinaryResource> binaryResources = new ArrayList<>();
    private DimensionDef dimensionDef;
    private BiomeCodes biomeCodes;
    private BiomeDecision biomeDecision;
    private BiomeCodes boundBiomeCodes;
    private Field boundHeight;
    private List<BiomeDef> biomes;
    private Field height;
    private Field carvedHeight;
    private Field climateHeight;
    private Field surfaceEstimate;
    private Field volumetric;
    private Field caves;
    private Field.Shared caveField;
    private double wallShellBand;
    private Field.Shared deepCaves;
    private AquiferDef aquifer;
    private MaterialProgram material;
    private DefaultBiome defaultBiome;

    private PackCompileContext(Builder builder) {
        data = Objects.requireNonNull(builder.data, "Pack compile context requires pack data");
        dimension = Objects.requireNonNull(builder.dimension, "Pack compile context requires a dimension");
        capabilities = Objects.requireNonNull(builder.capabilities, "Pack compile context requires export capabilities");
        namespace = VanillaResourceIdentifier.requireNamespace(builder.namespace, "export namespace");
        mode = Objects.requireNonNull(builder.mode, "Pack compile context requires an export mode");
        objects = builder.objects;
        styles = builder.styles;
        diagnostics = builder.diagnostics;
        noises = new NoiseRegistry(namespace);
    }

    public static Builder builder() {
        return new Builder();
    }

    public IrisData data() {
        return data;
    }

    public IrisDimension dimension() {
        return dimension;
    }

    public ExportCapabilities capabilities() {
        return capabilities;
    }

    public String namespace() {
        return namespace;
    }

    public WorldgenExportMode mode() {
        return mode;
    }

    public boolean objects() {
        return objects;
    }

    public StyleCompiler styles() {
        return styles;
    }

    public NoiseRegistry noises() {
        return noises;
    }

    public List<ExportDiagnostic> diagnostics() {
        return diagnostics;
    }

    public BiomeFeatureLists features() {
        return features;
    }

    public List<JsonResource> jsonResources() {
        return jsonResources;
    }

    public List<BinaryResource> binaryResources() {
        return binaryResources;
    }

    public DimensionDef dimensionDef() {
        if (dimensionDef == null) {
            throw new IllegalStateException("Dimension definition for '" + dimension.getLoadKey() + "' is read before the dimension stage ran");
        }
        return dimensionDef;
    }

    public void setDimensionDef(DimensionDef value) {
        dimensionDef = Objects.requireNonNull(value);
    }

    public BiomeCodes biomeCodes() {
        if (biomeDecision != null) {
            return height == null ? biomeDecision.base() : heightBoundBiomeCodes();
        }
        return biomeCodes == null ? defaultBiome().codes() : biomeCodes;
    }

    public BiomeCodes baseBiomeCodes() {
        if (biomeDecision != null) {
            return biomeDecision.base();
        }
        return biomeCodes == null ? defaultBiome().codes() : biomeCodes;
    }

    public void setBiomeCodes(BiomeCodes value) {
        biomeCodes = Objects.requireNonNull(value);
        biomeDecision = null;
    }

    public void setBiomeDecision(BiomeDecision value) {
        biomeDecision = Objects.requireNonNull(value);
        biomeCodes = null;
        boundBiomeCodes = null;
        boundHeight = null;
    }

    public List<BiomeDef> biomes() {
        return biomes == null ? defaultBiome().biomes() : biomes;
    }

    public void setBiomes(List<BiomeDef> value) {
        biomes = List.copyOf(value);
    }

    public Field height() {
        return carvedHeight == null ? naturalHeight() : carvedHeight;
    }

    public Field naturalHeight() {
        return height == null ? new Field.Const(dimensionDef().fluidHeight()) : height;
    }

    public void setHeight(Field value) {
        FieldValidator.requireTwoDimensional(Objects.requireNonNull(value), "height");
        height = value;
        carvedHeight = null;
        climateHeight = null;
        surfaceEstimate = null;
    }

    public Field climateHeight() {
        return climateHeight == null ? naturalHeight() : climateHeight;
    }

    public void setClimateHeight(Field value) {
        FieldValidator.requireTwoDimensional(Objects.requireNonNull(value), "climate height");
        climateHeight = value;
    }

    public void setCarvedHeight(Field value) {
        FieldValidator.requireTwoDimensional(Objects.requireNonNull(value), "carved height");
        carvedHeight = value;
    }

    public Field surfaceEstimate() {
        return surfaceEstimate == null ? climateHeight() : surfaceEstimate;
    }

    public void setSurfaceEstimate(Field value) {
        FieldValidator.requireTwoDimensional(Objects.requireNonNull(value), "surface estimate");
        surfaceEstimate = value;
    }

    public Field volumetric() {
        return volumetric == null ? NEUTRAL_VOLUME : volumetric;
    }

    public void setVolumetric(Field value) {
        volumetric = Objects.requireNonNull(value);
    }

    public Field caves() {
        return caves == null ? NO_CAVES : caves;
    }

    public void setCaves(Field value) {
        caves = Objects.requireNonNull(value);
    }

    public Optional<Field.Shared> caveField() {
        return Optional.ofNullable(caveField);
    }

    public void setCaveField(Field.Shared value, double wallBand) {
        if (!(wallBand >= 0.0) || Double.isInfinite(wallBand)) {
            throw new IllegalArgumentException("Wall shell band must be finite and non-negative, got " + wallBand);
        }
        caveField = Objects.requireNonNull(value);
        wallShellBand = wallBand;
    }

    public double wallShellBand() {
        return wallShellBand;
    }

    public Optional<Field.Shared> deepCaves() {
        return Optional.ofNullable(deepCaves);
    }

    public void setDeepCaves(Field.Shared value) {
        deepCaves = Objects.requireNonNull(value);
    }

    public AquiferDef aquifer() {
        return aquifer == null ? AquiferDef.dry(surfaceEstimate()) : aquifer;
    }

    public void setAquifer(AquiferDef value) {
        aquifer = Objects.requireNonNull(value);
    }

    public MaterialProgram material() {
        return material == null ? MaterialProgram.rock(dimensionDef().defaultBlock()) : material;
    }

    public void setMaterial(MaterialProgram value) {
        material = Objects.requireNonNull(value);
    }

    private BiomeCodes heightBoundBiomeCodes() {
        Field bindingHeight = climateHeight();
        if (boundHeight != bindingHeight) {
            boundBiomeCodes = biomeDecision.bind(bindingHeight);
            boundHeight = bindingHeight;
        }
        return boundBiomeCodes;
    }

    private DefaultBiome defaultBiome() {
        if (defaultBiome == null) {
            defaultBiome = createDefaultBiome();
        }
        return defaultBiome;
    }

    private DefaultBiome createDefaultBiome() {
        String loadKey = firstLandBiome();
        String id = BiomeIdSanitizer.sanitize(namespace, loadKey, new HashSet<>());
        String body = new IrisBiomeCustom().generateJson(capabilities.target().get(), new ContentGate(null, Map.of(), null));
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        for (String key : LEGACY_BIOME_KEYS) {
            json.remove(key);
        }
        BiomeDef biome = new BiomeDef(id.substring(namespace.length() + 1), json);
        ClimateLayout layout = new ClimateLayout(List.of(
                new ClimateLayout.ClimateEntry(id, ClimateLayout.ClimateAxis.TEMPERATURE, -CLIMATE_SPAN, CLIMATE_SPAN)));
        BiomeCodes codes = new BiomeCodes(ZERO_CODE, ZERO_CODE, ZERO_CODE, layout, Map.of(id, loadKey));
        return new DefaultBiome(List.of(biome), codes);
    }

    private String firstLandBiome() {
        for (String regionKey : dimension.getRegions()) {
            IrisRegion region = data.getRegionLoader().load(regionKey, false);
            if (region == null || region.isCompatExcluded()) {
                continue;
            }
            for (String biomeKey : region.getLandBiomes()) {
                IrisBiome biome = biomeKey == null ? null : data.getBiomeLoader().load(biomeKey, false);
                if (biome != null && !biome.isCompatExcluded()) {
                    return biome.getLoadKey();
                }
            }
        }
        throw new IllegalStateException("Dimension '" + dimension.getLoadKey() + "' has no exportable land biome in any of its regions");
    }

    private record DefaultBiome(List<BiomeDef> biomes, BiomeCodes codes) {
    }

    public static final class Builder {
        private IrisData data;
        private IrisDimension dimension;
        private ExportCapabilities capabilities;
        private String namespace;
        private WorldgenExportMode mode = WorldgenExportMode.OVERWORLD;
        private boolean objects = true;
        private StyleCompiler styles = NO_STYLE_COMPILER;
        private List<ExportDiagnostic> diagnostics = new ArrayList<>();

        private Builder() {
        }

        public Builder data(IrisData value) {
            data = Objects.requireNonNull(value);
            return this;
        }

        public Builder dimension(IrisDimension value) {
            dimension = Objects.requireNonNull(value);
            return this;
        }

        public Builder capabilities(ExportCapabilities value) {
            capabilities = Objects.requireNonNull(value);
            return this;
        }

        public Builder namespace(String value) {
            namespace = Objects.requireNonNull(value);
            return this;
        }

        public Builder mode(WorldgenExportMode value) {
            mode = Objects.requireNonNull(value);
            return this;
        }

        public Builder objects(boolean value) {
            objects = value;
            return this;
        }

        public Builder styles(StyleCompiler value) {
            styles = Objects.requireNonNull(value);
            return this;
        }

        public Builder diagnostics(List<ExportDiagnostic> value) {
            diagnostics = Objects.requireNonNull(value);
            return this;
        }

        public PackCompileContext build() {
            return new PackCompileContext(this);
        }
    }
}
