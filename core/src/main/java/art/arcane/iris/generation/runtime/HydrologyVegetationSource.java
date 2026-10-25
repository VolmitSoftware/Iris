package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.HydrologyVegetation;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class HydrologyVegetationSource {
    static final HydrologyVegetationSource NONE = new HydrologyVegetationSource(null, null, Map.of(), HydrologyVegetation.Decorators.EMPTY);
    private static final String LAZY_POLICY = "Hydrology hydrologyPolicy.decorators";

    private final IrisData data;
    private final IrisDimension dimension;
    private final Map<HydrologyTier, HydrologyVegetation.Decorators> tiers;
    private final HydrologyVegetation.Decorators ponds;
    private final Object lock = new Object();
    private boolean planted;
    private volatile IdentityHashMap<List<IrisDecorator>, HydrologyVegetation.Decorators> compiled = new IdentityHashMap<>();
    private volatile IdentityHashMap<IrisBiome, IdentityHashMap<IrisRegion, Resolved>> overrides = new IdentityHashMap<>();

    private HydrologyVegetationSource(IrisData data, IrisDimension dimension, Map<HydrologyTier, HydrologyVegetation.Decorators> tiers,
                                      HydrologyVegetation.Decorators ponds) {
        this.data = data;
        this.dimension = dimension;
        this.tiers = tiers;
        this.ponds = ponds;
    }

    static HydrologyVegetationSource create(IrisDimension dimension, HydrologySettings settings, IrisData data,
                                            Collection<IrisRegion> regions, Collection<IrisBiome> biomes) {
        IdentityHashMap<HydrologyTier, HydrologyVegetation.Decorators> tiers = new IdentityHashMap<>();
        for (HydrologyTier tier : settings.tiers()) {
            tiers.put(tier, HydrologyVegetation.Decorators.compile(tier.decorators(), data, "Hydrology tier '" + tier.name() + "' decorators"));
        }
        HydrologyVegetationSource source = new HydrologyVegetationSource(data, dimension, tiers,
                HydrologyVegetation.Decorators.compile(settings.ponds().decorators(), data, "Hydrology ponds.decorators"));
        source.planted = !source.ponds.empty();
        for (HydrologyVegetation.Decorators decorators : tiers.values()) {
            source.planted |= !decorators.empty();
        }
        source.compile(dimension.getHydrologyPolicy(), "Dimension '" + dimension.getLoadKey() + "'");
        for (IrisRegion region : regions) {
            source.compile(region == null ? null : region.getHydrologyPolicy(), "Region '" + (region == null ? null : region.getLoadKey()) + "'");
        }
        for (IrisBiome biome : biomes) {
            source.compile(biome == null ? null : biome.getHydrologyPolicy(), "Biome '" + (biome == null ? null : biome.getLoadKey()) + "'");
        }
        return source;
    }

    boolean planted() {
        return planted;
    }

    HydrologyVegetation.Decorators decorators(HydrologyTier tier, IrisBiome biome, IrisRegion region) {
        if (data == null) {
            return HydrologyVegetation.Decorators.EMPTY;
        }
        Resolved override = override(biome, region);
        if (override.decorators() != null) {
            return override.decorators();
        }
        if (tier == null) {
            return ponds;
        }
        HydrologyVegetation.Decorators known = tiers.get(tier);
        if (known == null) {
            throw new IllegalStateException("Hydrology tier '" + tier.name() + "' is not one of the compiled hydrology tiers");
        }
        return known;
    }

    private Resolved override(IrisBiome biome, IrisRegion region) {
        IdentityHashMap<IrisRegion, Resolved> regions = overrides.get(biome);
        Resolved known = regions == null ? null : regions.get(region);
        return known != null ? known : remember(biome, region, resolve(biome, region));
    }

    private Resolved resolve(IrisBiome biome, IrisRegion region) {
        List<IrisDecorator> decorators = IrisHydrologyPolicy.inherited(biome, region, dimension, IrisHydrologyPolicy::getDecorators);
        return decorators == null ? Resolved.INHERIT : new Resolved(compiled(decorators, LAZY_POLICY));
    }

    private Resolved remember(IrisBiome biome, IrisRegion region, Resolved resolved) {
        synchronized (lock) {
            IdentityHashMap<IrisRegion, Resolved> current = overrides.get(biome);
            Resolved raced = current == null ? null : current.get(region);
            if (raced != null) {
                return raced;
            }
            IdentityHashMap<IrisBiome, IdentityHashMap<IrisRegion, Resolved>> next = new IdentityHashMap<>(overrides);
            IdentityHashMap<IrisRegion, Resolved> regions = current == null ? new IdentityHashMap<>() : new IdentityHashMap<>(current);
            regions.put(region, resolved);
            next.put(biome, regions);
            overrides = next;
            return resolved;
        }
    }

    private void compile(IrisHydrologyPolicy policy, String owner) {
        if (policy != null && policy.getDecorators() != null) {
            planted |= !compiled(policy.getDecorators(), owner + " hydrologyPolicy.decorators").empty();
        }
    }

    private HydrologyVegetation.Decorators compiled(List<IrisDecorator> decorators, String path) {
        HydrologyVegetation.Decorators known = compiled.get(decorators);
        if (known != null) {
            return known;
        }
        synchronized (lock) {
            HydrologyVegetation.Decorators raced = compiled.get(decorators);
            if (raced != null) {
                return raced;
            }
            HydrologyVegetation.Decorators built = HydrologyVegetation.Decorators.compile(decorators, data, path);
            IdentityHashMap<List<IrisDecorator>, HydrologyVegetation.Decorators> next = new IdentityHashMap<>(compiled);
            next.put(decorators, built);
            compiled = next;
            return built;
        }
    }

    private record Resolved(HydrologyVegetation.Decorators decorators) {
        private static final Resolved INHERIT = new Resolved(null);
    }
}
