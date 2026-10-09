package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.service.ObjectsAt;
import com.volmit.world.PackWorld;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How {@code iris:objects} tells Iris where its objects are: the objects the biome and region of a chunk would place,
 * and the biomes and regions whose surface placements name an object.
 */
public final class ObjectLocating implements ObjectsAt {
    @Override
    public Set<String> objectsAt(PackWorld world, int chunkX, int chunkZ) {
        return ObjectModule.placing(engine(world)).guess(chunkX, chunkZ);
    }

    @Override
    public PlacedBy placedBy(PackWorld world, String objectKey) {
        Engine engine = engine(world);
        Set<String> biomes = new LinkedHashSet<>();
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            if (biome != null && placesObject(biome.getSurfaceObjects(), objectKey)) {
                biomes.add(biome.getLoadKey());
            }
        }

        Set<String> regions = new LinkedHashSet<>();
        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region != null && placesObject(region.getSurfaceObjects(), objectKey)) {
                regions.add(region.getLoadKey());
            }
        }
        return new PlacedBy(biomes, regions);
    }

    private static boolean placesObject(List<IrisObjectPlacement> placements, String objectKey) {
        for (IrisObjectPlacement placement : placements) {
            if (placement != null && placement.getPlace().contains(objectKey)) {
                return true;
            }
        }
        return false;
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }
}
