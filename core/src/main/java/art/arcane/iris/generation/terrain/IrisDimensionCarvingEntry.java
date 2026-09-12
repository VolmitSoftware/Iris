package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.validation.CompatPools;
import art.arcane.iris.pack.value.IrisRange;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.DependsOn;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Snippet("dimension-carving-entry")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Dimension-level cave biome override with absolute world Y bounds.")
@Data
public class IrisDimensionCarvingEntry {
    private final transient AtomicCache<IrisBiome> realBiome = new AtomicCache<>(true);
    private final transient Map<Long, CNG> childGenerators = new ConcurrentHashMap<>();

    @Description("Stable id for this carving entry")
    private String id = "";

    @Description("Enable or disable this carving entry")
    private boolean enabled = true;

    @RegistryListResource(IrisBiome.class)
    @Description("Cave biome to apply when world Y falls within worldYRange")
    private String biome = "";

    @Description("Absolute world Y bounds where this carving entry applies")
    private IrisRange worldYRange = new IrisRange(-64, 320);

    @DependsOn({"children"})
    @Description("If this carving entry has child carving entries, this controls how small those child carving patches are.")
    private double childShrinkFactor = 1.5;

    @DependsOn({"children"})
    @Description("If this carving entry has child carving entries, this controls the shape pattern used to pick them.")
    private IrisGeneratorStyle childStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

    @ArrayType(min = 1, type = String.class)
    @Description("Child carving entry ids. Child ids can point back to parent ids to create cycles; recursion is bounded by childRecursionDepth.")
    private KList<String> children = new KList<>();

    @Description("Maximum recursion depth when resolving child carving entries from this entry.")
    private int childRecursionDepth = 3;

    public IrisBiome getRealBiome(IrisData data) {
        return realBiome.aquire(() -> {
            String biomeKey = getBiome();
            if (biomeKey == null || biomeKey.isBlank()) {
                return null;
            }

            IrisBiome loaded = data.getBiomeLoader().load(biomeKey.trim());

            if (loaded != null && loaded.isCompatExcluded()) {
                CompatPools.drop(data, loaded, "carving entry", getId(), "biome " + biomeKey.trim(), null);
                return null;
            }

            return loaded;
        });
    }

    public CNG getChildrenGenerator(long seed, IrisData data) {
        String entryId = getId();
        long idHash = entryId == null ? 0L : entryId.trim().hashCode();
        long generatorSeed = seed ^ (idHash << 32) ^ 2137L;
        return childGenerators.computeIfAbsent(generatorSeed, key -> {
            double scale = Math.max(0.0001D, getChildShrinkFactor());
            RNG random = new RNG(key);
            return getChildStyle().create(random.nextParallelRNG(2137), data).bake().scale(scale).bake();
        });
    }
}
