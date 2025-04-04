package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedComponent;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;

public class IrisStructurePopulator extends EngineAssignedComponent {
    private final CNG cng;
    private final long mantle;
    private final long jigsaw;

    public IrisStructurePopulator(Engine engine) {
        super(engine, "Datapack Structures");
        mantle = engine.getSeedManager().getMantle();
        jigsaw = engine.getSeedManager().getJigsaw();
        cng = NoiseStyle.STATIC.create(new RNG(jigsaw));
    }

    @ChunkCoordinates
    public void populateStructures(int x, int z, BiPredicate<String, Boolean> placer) {
        int bX = x << 4, bZ = z << 4;
        var dimension = getDimension();
        var region = getEngine().getRegion(bX + 8, bZ + 8);
        var biome = getEngine().getSurfaceBiome(bX + 8, bZ + 8);
        var loader = getData().getJigsawStructureLoader();

        long seed = cng.fit(Integer.MIN_VALUE, Integer.MAX_VALUE, x, z);
        if (dimension.getStronghold() != null) {
            var list = getDimension().getStrongholds(mantle);
            if (list != null && list.contains(new Position2(bX, bZ))) {
                place(placer, loader.load(dimension.getStronghold()), new RNG(seed), true);
                return;
            }
        }

        boolean placed = place(placer, biome.getJigsawStructures(), seed, x, z);
        if (!placed) placed = place(placer, region.getJigsawStructures(), seed, x, z);
        if (!placed) place(placer, dimension.getJigsawStructures(), seed, x, z);
    }

    private boolean place(BiPredicate<String, Boolean> placer, KList<IrisJigsawStructurePlacement> placements, long seed, int x, int z) {
        var placement = pick(placements, seed, x, z);
        if (placement == null) return false;
        return place(placer, getData().getJigsawStructureLoader().load(placement.getStructure()), new RNG(seed), false);
    }

    @Nullable
    @ChunkCoordinates
    private IrisJigsawStructurePlacement pick(List<IrisJigsawStructurePlacement> structures, long seed, int x, int z) {
        return IRare.pick(structures.stream()
                .filter(p -> p.shouldPlace(getData(), getDimension().getJigsawStructureDivisor(), jigsaw, x, z))
                .toList(), new RNG(seed).nextDouble());
    }

    @ChunkCoordinates
    private boolean place(BiPredicate<String, Boolean> placer, IrisJigsawStructure structure, RNG rng, boolean ignoreBiomes) {
        if (structure == null || structure.getDatapackStructures().isEmpty()) return false;
        var keys = structure.getDatapackStructures().shuffleCopy(rng);
        while (keys.isNotEmpty()) {
            String key = keys.removeFirst();
            if (key != null && placer.test(key, ignoreBiomes || structure.isForcePlace()))
                return true;
        }
        return false;
    }
}
