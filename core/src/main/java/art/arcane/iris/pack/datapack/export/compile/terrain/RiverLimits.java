package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPermission;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyWetness;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

final class RiverLimits {
    private static final double NEUTRAL_RAINFALL = 1.0;
    private static final Comparator<Finding> ORDER = Comparator.comparing(Finding::limit)
            .thenComparingInt(finding -> finding.owner().rank())
            .thenComparing(finding -> finding.owner().resource())
            .thenComparing(Finding::subject);

    private final IrisDimension dimension;
    private final HydrologySettings settings;
    private final Owner hydrology;
    private final Owner dimensionPolicy;
    private final String exportFluid;
    private final SortedMap<Finding, String> findings = new TreeMap<>(ORDER);

    private RiverLimits(IrisDimension dimension, HydrologySettings settings, String resource) {
        this.dimension = dimension;
        this.settings = settings;
        this.hydrology = new Owner(Owner.HYDROLOGY, resource);
        this.dimensionPolicy = new Owner(Owner.DIMENSION, "dimensions/" + dimension.getLoadKey());
        this.exportFluid = DimensionStage.fluidBlock(dimension);
    }

    static List<ExportDiagnostic> diagnostics(IrisDimension dimension, HydrologySettings settings, List<RiverCompiler.Area> areas, String resource) {
        RiverLimits limits = new RiverLimits(dimension, settings, resource);
        for (RiverCompiler.Area area : areas) {
            limits.inspect(area);
        }
        limits.inspectUnderground();
        List<ExportDiagnostic> diagnostics = new ArrayList<>(limits.findings.size());
        for (Map.Entry<Finding, String> entry : limits.findings.entrySet()) {
            Finding finding = entry.getKey();
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, finding.limit(), finding.owner().resource(), entry.getValue()));
        }
        return diagnostics;
    }

    private void inspect(RiverCompiler.Area area) {
        inspectFluid(area);
        inspectPolicy(area);
        inspectClimate(area);
        inspectShore(area);
        inspectDecorators(area);
    }

    private void inspectUnderground() {
        if (settings.underground().seaInlets().enabled()) {
            add(ExportLimit.SEA_INLET, hydrology, "", "Sea inlets are not exported: coastal cliffs have no flooded passages");
        }
        if (settings.underground().cavePools().enabled() && settings.underground().cavePools().density() > 0.0F) {
            add(ExportLimit.CAVE_POOL, hydrology, "", "Cave pools are not exported: cave floors hold no standing water of their own");
        }
    }

    private void inspectFluid(RiverCompiler.Area area) {
        Sourced<String> fluid = sourced(area, IrisHydrologyPolicy::getFluid);
        if (fluid != null) {
            settings.requireFillingFluid(fluid.value(), "the hydrology policy of " + fluid.owner().resource());
            fluid(fluid.owner(), fluid.value());
            return;
        }
        for (HydrologyTier tier : settings.tiers()) {
            fluid(hydrology, tier.fluid());
        }
    }

    private void inspectPolicy(RiverCompiler.Area area) {
        Sourced<Double> rainfall = sourced(area, IrisHydrologyPolicy::getRainfall);
        if (rainfall != null && rainfall.value() != NEUTRAL_RAINFALL) {
            add(ExportLimit.RIVER_POLICY, rainfall.owner(), "rainfall",
                    "Hydrology policy rainfall is not exported: rivers form at the dimension density here");
        }
        Sourced<IrisHydrologyPermission> streams = sourced(area, IrisHydrologyPolicy::getStreams);
        if (streams != null && streams.value() == IrisHydrologyPermission.BLOCK) {
            add(ExportLimit.RIVER_POLICY, streams.owner(), "streams",
                    "Hydrology policy streams BLOCK is not exported: rivers start here as they do elsewhere");
        }
    }

    private void inspectClimate(RiverCompiler.Area area) {
        HydrologySettings.Climate climate = settings.climate();
        Sourced<Boolean> zones = sourced(area, IrisHydrologyPolicy::getClimateZones);
        if (!climate.enabled() || zones != null && !zones.value()) {
            return;
        }
        boolean zoned = climate.dryness() > 0.0F && climate.dryRainfall() < 1.0F;
        if (zoned) {
            add(ExportLimit.RIVER_CLIMATE, hydrology, "", "Wet and dry zones are not exported: rivers start in dry zones as they do in wet ones");
        }
        Sourced<IrisHydrologyWetness> wetness = sourced(area, IrisHydrologyPolicy::getWetness);
        double driest = zoned ? climate.dryRainfall() : 1.0;
        if (wetness != null && (wetness.value().getMin() > driest || wetness.value().getMax() < 1.0)) {
            add(ExportLimit.RIVER_CLIMATE, wetness.owner(), "wetness",
                    "Hydrology policy wetness is not exported: rivers form at the dimension density here");
        }
    }

    private void inspectShore(RiverCompiler.Area area) {
        Sourced<Integer> width = sourced(area, IrisHydrologyPolicy::getShoreBiomeWidth);
        Sourced<List<String>> biomes = sourced(area, IrisHydrologyPolicy::getShoreBiomes);
        if (width == null || width.value() <= 0 || biomes == null || biomes.value().isEmpty()) {
            return;
        }
        add(ExportLimit.RIVER_SHORE_BIOME, biomes.owner(), "",
                "Hydrology shore biomes are not exported: the water's edge keeps the biome around it");
    }

    private void inspectDecorators(RiverCompiler.Area area) {
        Sourced<List<IrisDecorator>> decorators = sourced(area, IrisHydrologyPolicy::getDecorators);
        if (decorators != null) {
            if (!decorators.value().isEmpty()) {
                decorators(decorators.owner());
            }
            return;
        }
        for (HydrologyTier tier : settings.tiers()) {
            if (tier.decorators() != null && !tier.decorators().isEmpty()) {
                decorators(hydrology);
                return;
            }
        }
    }

    private void decorators(Owner owner) {
        add(ExportLimit.RIVER_DECORATOR, owner, "", "Hydrology decorators are not exported: rivers grow no water plants of their own");
    }

    private void fluid(Owner owner, String name) {
        String seam = settings.seams().get(name);
        if (!exported(settings.fluids().get(name))) {
            String subject = seam == null ? "Fluid '" + name + "' is" : "Fluid '" + name + "' and its seam '" + seam + "' are";
            add(ExportLimit.RIVER_FLUID, owner, name, subject + " not exported: rivers fill with " + exportFluid + " up to fluidHeight");
            return;
        }
        if (seam != null) {
            add(ExportLimit.RIVER_FLUID, owner, name, "Seam '" + seam + "' of fluid '" + name + "' is not exported: rivers have no seam band at their edges");
        }
    }

    private boolean exported(IrisMaterialPalette palette) {
        for (IrisBlockData block : palette.getPalette()) {
            if (!exportFluid.equals(DimensionStage.blockId(block.getBlock()))) {
                return false;
            }
        }
        return true;
    }

    private void add(ExportLimit limit, Owner owner, String subject, String message) {
        findings.putIfAbsent(new Finding(limit, owner, subject), message);
    }

    private <T> Sourced<T> sourced(RiverCompiler.Area area, Function<IrisHydrologyPolicy, T> field) {
        for (IrisBiome biome : area.biomes()) {
            T value = value(biome.getHydrologyPolicy(), field);
            if (value != null) {
                return new Sourced<>(new Owner(Owner.BIOME, "biomes/" + biome.getLoadKey()), value);
            }
        }
        T region = value(area.region().getHydrologyPolicy(), field);
        if (region != null) {
            return new Sourced<>(new Owner(Owner.REGION, "regions/" + area.region().getLoadKey()), region);
        }
        T inherited = value(dimension.getHydrologyPolicy(), field);
        return inherited == null ? null : new Sourced<>(dimensionPolicy, inherited);
    }

    private static <T> T value(IrisHydrologyPolicy policy, Function<IrisHydrologyPolicy, T> field) {
        return policy == null ? null : field.apply(policy);
    }

    private record Owner(int rank, String resource) {
        private static final int HYDROLOGY = 0;
        private static final int DIMENSION = 1;
        private static final int REGION = 2;
        private static final int BIOME = 3;
    }

    private record Finding(ExportLimit limit, Owner owner, String subject) {
    }

    private record Sourced<T>(Owner owner, T value) {
    }
}
