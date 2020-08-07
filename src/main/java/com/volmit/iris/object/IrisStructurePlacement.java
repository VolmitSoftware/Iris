package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualChunkGenerator;
import com.volmit.iris.gen.ParallaxChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class IrisStructurePlacement
{
	@DontObfuscate
	@Desc("The structure tileset to use")
	private String tileset = "";

	@DontObfuscate
	@Desc("The structure chance zoom. Higher = bigger cells, further away")
	private double zoom = 1D;

	@DontObfuscate
	@Desc("The ratio. Lower values means cells can get closer to other cells. Negative values means make veins of structures")
	private double ratio = 0.25D;

	@DontObfuscate
	@Desc("The rarity for this structure")
	private int rarity = 4;

	@DontObfuscate
	@Desc("The height or -1 for surface")
	private int height = -1;

	@DontObfuscate
	@Desc("The chance cell shuffle (rougher edges)")
	private double shuffle = 22;

	private transient AtomicCache<CellGenerator> chanceCell = new AtomicCache<>();
	private transient AtomicCache<IrisStructure> structure = new AtomicCache<>();
	private transient AtomicCache<IrisObjectPlacement> config = new AtomicCache<>();

	public IrisStructurePlacement()
	{

	}

	public void place(ParallaxChunkGenerator g, RNG rngno, int cx, int cz)
	{
		try
		{
			RNG rng = g.getMasterRandom().nextParallelRNG(-88738456);
			RNG rnp = rng.nextParallelRNG(cx - (cz * cz));
			int s = gridSize(g) - (getStructure(g).isMergeEdges() ? 1 : 0);
			int sh = gridHeight(g) - (getStructure(g).isMergeEdges() ? 1 : 0);

			for(int i = cx << 4; i < (cx << 4) + 15; i += Math.max(s, 1))
			{
				for(int j = cz << 4; j < (cz << 4) + 15; j += Math.max(s, 1))
				{
					if(getStructure(g).getMaxLayers() <= 1)
					{
						placeLayer(g, rng, rnp, i, 0, j, s, sh);
						continue;
					}

					for(int k = 0; k < s * getStructure(g).getMaxLayers(); k += Math.max(sh, 1))
					{
						placeLayer(g, rng, rnp, i, k, j, s, sh);
					}
				}
			}
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	public void placeLayer(ParallaxChunkGenerator g, RNG rng, RNG rnp, int i, int k, int j, int s, int sh)
	{
		if(!hasStructure(rng, i, k, j))
		{
			return;
		}

		int h = (height == -1 ? 0 : height) + (Math.floorDiv(k, sh) * sh);
		TileResult t = getStructure(g).getTile(rng, i, h, j);

		if(t != null)
		{
			if(height >= 0)
			{
				t.getPlacement().setBore(true);
			}

			IrisObject o = load(g, t.getTile().getObjects().get(rnp.nextInt(t.getTile().getObjects().size())));
			o.place(Math.floorDiv(i, s) * s, height == -1 ? -1 : h, Math.floorDiv(j, s) * s, g, t.getPlacement(), rng);
		}
	}

	private IrisObjectPlacement getConfig()
	{
		return config.aquire(() -> new IrisObjectPlacement());
	}

	public IrisObject load(ContextualChunkGenerator g, String s)
	{
		return g.getData().getObjectLoader().load(s);
	}

	public int gridSize(ContextualChunkGenerator g)
	{
		return getStructure(g).getGridSize();
	}

	public int gridHeight(ContextualChunkGenerator g)
	{
		return getStructure(g).getGridHeight();
	}

	public IrisStructure getStructure(ContextualChunkGenerator g)
	{
		return structure.aquire(() -> (g == null ? Iris.globaldata : g.getData()).getStructureLoader().load(getTileset()));
	}

	public boolean hasStructure(RNG random, double x, double y, double z)
	{
		if(getChanceGenerator(random).getIndex(x / zoom, y / zoom, z / zoom, getRarity()) == getRarity() / 2)
		{
			return ratio > 0 ? getChanceGenerator(random).getDistance(x / zoom, z / zoom) > ratio : getChanceGenerator(random).getDistance(x / zoom, z / zoom) < Math.abs(ratio);
		}

		return false;
	}

	public CellGenerator getChanceGenerator(RNG random)
	{
		return chanceCell.aquire(() ->
		{
			CellGenerator chanceCell = new CellGenerator(random.nextParallelRNG(-72346));
			chanceCell.setCellScale(1D);
			chanceCell.setShuffle(getShuffle());
			return chanceCell;
		});
	}
}
