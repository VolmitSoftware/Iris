package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisRareObject;
import com.volmit.iris.object.IrisStructurePlacement;
import com.volmit.iris.object.TileResult;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.RNG;
import com.volmit.iris.v2.scaffold.parallax.ParallaxChunkMeta;

public interface EngineStructureManager extends EngineComponent
{
    default void placeStructure(IrisStructurePlacement structure, RNG rngno, int cx, int cz)
    {
        RNG rng = new RNG(getEngine().getWorld().getSeed()).nextParallelRNG(-88738456 + rngno.nextInt());
        RNG rnp = rng.nextParallelRNG(cx - (cz * cz << 3) + rngno.nextInt());
        int s = structure.gridSize(getEngine()) - (structure.getStructure(getEngine()).isMergeEdges() ? 1 : 0);
        int sh = structure.gridHeight(getEngine()) - (structure.getStructure(getEngine()).isMergeEdges() ? 1 : 0);
        KSet<ChunkPosition> m = new KSet<>();

        for(int i = cx << 4; i <= (cx << 4) + 15; i += 1)
        {
            if(Math.floorDiv(i, s) * s >> 4 < cx)
            {
                continue;
            }

            for(int j = cz << 4; j <= (cz << 4) + 15; j += 1)
            {
                if(Math.floorDiv(j, s) * s >> 4 < cz)
                {
                    continue;
                }

                ChunkPosition p = new ChunkPosition(Math.floorDiv(i, s) * s, Math.floorDiv(j, s) * s);

                if(m.contains(p))
                {
                    continue;
                }

                m.add(p);

                if(structure.getStructure(getEngine()).getMaxLayers() <= 1)
                {
                    placeLayer(structure, rng, rnp, i, 0, j, s, sh);
                    continue;
                }

                for(int k = 0; k < s * structure.getStructure(getEngine()).getMaxLayers(); k += Math.max(sh, 1))
                {
                    placeLayer(structure, rng, rnp, i, k, j, s, sh);
                }
            }
        }
    }

    default void placeLayer(IrisStructurePlacement structure,  RNG rng, RNG rnp, int i, int k, int j, int s, int sh)
    {
        if(!hasStructure(structure, rng, i, k, j))
        {
            return;
        }

        int h = (structure.getHeight() == -1 ? 0 : structure.getHeight()) + (Math.floorDiv(k, sh) * sh);
        TileResult t = structure.getStructure(getEngine()).getTile(rng, Math.floorDiv(i, s) * s, h, Math.floorDiv(j, s) * s);

        if(t != null)
        {
            IrisObject o = null;

            for(IrisRareObject l : t.getTile().getRareObjects())
            {
                if(rnp.i(1, l.getRarity()) == 1)
                {
                    o = structure.load(getEngine(), l.getObject());
                    break;
                }
            }

            o = o != null ? o : structure.load(getEngine(), t.getTile().getObjects().get(rnp.nextInt(t.getTile().getObjects().size())));
            int id = rng.i(0, Integer.MAX_VALUE);
            IrisObject oo = o;
            o.place(
                    Math.floorDiv(i, s) * s,
                    structure.getHeight() == -1 ? -1 : h,
                    Math.floorDiv(j, s) * s,
                    getEngine().getFramework().getEngineParallax(),
                    t.getPlacement(),
                    rng,
                    (b) -> {
                        getEngine().getParallax().setObject(b.getX(), b.getY(), b.getZ(), oo.getLoadKey() + "@" + id);
                        ParallaxChunkMeta meta = getEngine().getParallax().getMetaRW(b.getX() >> 4, b.getZ() >> 4);
                        meta.setObjects(true);
                        meta.setMaxObject(Math.max(b.getY(), meta.getMaxObject()));
                        meta.setMinObject(Math.min(b.getY(), Math.max(meta.getMinObject(), 0)));
                    },
                    null,
                    getData()
            );
        }
    }

    default boolean hasStructure(IrisStructurePlacement structure, RNG random, double x, double y, double z)
    {
        if(structure.getChanceGenerator(new RNG(getEngine().getWorld().getSeed())).getIndex(x / structure.getZoom(), y / structure.getZoom(), z / structure.getZoom(), structure.getRarity()) == structure.getRarity() / 2)
        {
            return structure.getRatio() > 0 ? structure.getChanceGenerator(new RNG(getEngine().getWorld().getSeed())).getDistance(x / structure.getZoom(), z / structure.getZoom()) > structure.getRatio() : structure.getChanceGenerator(new RNG(getEngine().getWorld().getSeed())).getDistance(x / structure.getZoom(), z / structure.getZoom()) < Math.abs(structure.getRatio());
        }

        return false;
    }
}
