package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.DependsOn;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.util.project.noise.CNG;
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
@Desc("Dimension-level cave biome override with absolute world Y bounds.")
@Data
public class IrisDimensionCarvingEntry {
    private final transient AtomicCache<IrisBiome> realBiome = new AtomicCache<>(true);
    private final transient Map<Long, CNG> childGenerators = new ConcurrentHashMap<>();

    @Desc("Stable id for this carving entry")
    private String id = "";

    @Desc("Enable or disable this carving entry")
    private boolean enabled = true;

    @RegistryListResource(IrisBiome.class)
    @Desc("Cave biome to apply when world Y falls within worldYRange")
    private String biome = "";

    @Desc("Absolute world Y bounds where this carving entry applies")
    private IrisRange worldYRange = new IrisRange(-64, 320);

    @DependsOn({"children"})
    @Desc("If this carving entry has child carving entries, this controls how small those child carving patches are.")
    private double childShrinkFactor = 1.5;

    @DependsOn({"children"})
    @Desc("If this carving entry has child carving entries, this controls the shape pattern used to pick them.")
    private IrisGeneratorStyle childStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

    @ArrayType(min = 1, type = String.class)
    @Desc("Child carving entry ids. Child ids can point back to parent ids to create cycles; recursion is bounded by childRecursionDepth.")
    private KList<String> children = new KList<>();

    @Desc("Maximum recursion depth when resolving child carving entries from this entry.")
    private int childRecursionDepth = 3;

    public IrisBiome getRealBiome(IrisData data) {
        return realBiome.aquire(() -> {
            String biomeKey = getBiome();
            if (biomeKey == null || biomeKey.isBlank()) {
                return null;
            }

            return data.getBiomeLoader().load(biomeKey.trim());
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
