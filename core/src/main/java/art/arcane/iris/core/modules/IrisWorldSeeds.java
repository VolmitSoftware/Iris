package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.random.WorldRandom;
import com.volmit.resource.ResourceId;

import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * How Iris answers {@link com.volmit.world.PackWorld#seed(ResourceId)}: the built-in modules' {@link IrisSeedPurposes}
 * get the seeds Iris's worlds always used for those features, every other purpose the API's rule.
 */
final class IrisWorldSeeds {
    private static final Map<ResourceId, ToLongFunction<SeedManager>> LEGACY_SEEDS = Map.ofEntries(
            Map.entry(IrisSeedPurposes.BIOME, SeedManager::getBiome),
            Map.entry(IrisSeedPurposes.CARVE, SeedManager::getCarve),
            Map.entry(IrisSeedPurposes.COMPLEX, SeedManager::getComplex),
            Map.entry(IrisSeedPurposes.COMPONENT, SeedManager::getComponent),
            Map.entry(IrisSeedPurposes.DECORATOR, SeedManager::getDecorator),
            Map.entry(IrisSeedPurposes.DEPOSIT, SeedManager::getDeposit),
            Map.entry(IrisSeedPurposes.ENTITY, SeedManager::getEntity),
            Map.entry(IrisSeedPurposes.LOOT, SeedManager::getLoot),
            Map.entry(IrisSeedPurposes.MANTLE, SeedManager::getMantle),
            Map.entry(IrisSeedPurposes.POST, SeedManager::getPost),
            Map.entry(IrisSeedPurposes.TERRAIN, SeedManager::getTerrain));

    private IrisWorldSeeds() {
    }

    static long seed(SeedManager seeds, ResourceId purpose) {
        ToLongFunction<SeedManager> legacy = LEGACY_SEEDS.get(purpose);
        return legacy != null ? legacy.applyAsLong(seeds) : WorldRandom.purposeSeed(seeds.getSeed(), purpose);
    }
}
