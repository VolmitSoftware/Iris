package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualTerrainProvider;
import com.volmit.iris.gen.ParallaxTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CellGenerator;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListStructure;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a structure placement")
@Data
public class IrisStructurePlacement
{

	@RegistryListStructure
	@Required
	@DontObfuscate
	@Desc("The structure tileset to use")
	private String tileset = "";

	@Required
	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The structure chance zoom. Higher = bigger cells, further away")
	private double zoom = 1D;

	@MinNumber(-1)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The ratio. Lower values means cells can get closer to other cells. Negative values means make veins of structures")
	private double ratio = 0.25D;

	@Required
	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity for this structure")
	private int rarity = 4;

	@MinNumber(-1)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The height or -1 for surface")
	private int height = -1;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The chance cell shuffle (rougher edges)")
	private double shuffle = 22;

	private final transient AtomicCache<CellGenerator> chanceCell = new AtomicCache<>();
	private final transient AtomicCache<IrisStructure> structure = new AtomicCache<>();
	private final transient AtomicCache<IrisObjectPlacement> config = new AtomicCache<>();

	public void place(ParallaxTerrainProvider g, RNG rngno, int cx, int cz)
	{
		try
		{
			RNG rng = g.getMasterRandom().nextParallelRNG(-88738456 + rngno.nextInt());
			RNG rnp = rng.nextParallelRNG(cx - (cz * cz << 3) + rngno.nextInt());
			int s = gridSize(g) - (getStructure(g).isMergeEdges() ? 1 : 0);
			int sh = gridHeight(g) - (getStructure(g).isMergeEdges() ? 1 : 0);
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

	public void placeLayer(ParallaxTerrainProvider g, RNG rng, RNG rnp, int i, int k, int j, int s, int sh)
	{
		if(!hasStructure(g, rng, i, k, j))
		{
			return;
		}

		int h = (height == -1 ? 0 : height) + (Math.floorDiv(k, sh) * sh);
		TileResult t = getStructure(g).getTile(rng, Math.floorDiv(i, s) * s, h, Math.floorDiv(j, s) * s);

		if(t != null)
		{
			IrisObject o = null;

			for(IrisRareObject l : t.getTile().getRareObjects())
			{
				if(rnp.i(1, l.getRarity()) == 1)
				{
					o = load(g, l.getObject());
					break;
				}
			}

			o = o != null ? o : load(g, t.getTile().getObjects().get(rnp.nextInt(t.getTile().getObjects().size())));
			o.place(Math.floorDiv(i, s) * s, height == -1 ? -1 : h, Math.floorDiv(j, s) * s, g, t.getPlacement(), rng, (gg) -> g.getParallaxChunk(gg.getChunkX(), gg.getChunkZ()).setStructure(gg.getY(), t.getStructure(), t.getTile()));
		}
	}

	private IrisObjectPlacement getConfig()
	{
		return config.aquire(() ->
		{
			IrisObjectPlacement p = new IrisObjectPlacement();
			p.setWaterloggable(false);
			return p;
		});
	}

	public IrisObject load(ContextualTerrainProvider g, String s)
	{
		return g.getData().getObjectLoader().load(s);
	}

	public int gridSize(ContextualTerrainProvider g)
	{
		return getStructure(g).getGridSize();
	}

	public int gridHeight(ContextualTerrainProvider g)
	{
		return getStructure(g).getGridHeight();
	}

	public IrisStructure getStructure(ContextualTerrainProvider g)
	{
		return structure.aquire(() -> (g == null ? Iris.globaldata : g.getData()).getStructureLoader().load(getTileset()));
	}

	public boolean hasStructure(ParallaxTerrainProvider g, RNG random, double x, double y, double z)
	{
		if(g.getGlCarve().isCarved((int) x, (int) y, (int) z))
		{
			return false;
		}

		if(getChanceGenerator(g, random).getIndex(x / zoom, y / zoom, z / zoom, getRarity()) == getRarity() / 2)
		{
			return ratio > 0 ? getChanceGenerator(g, random).getDistance(x / zoom, z / zoom) > ratio : getChanceGenerator(g, random).getDistance(x / zoom, z / zoom) < Math.abs(ratio);
		}

		return false;
	}

	public CellGenerator getChanceGenerator(ParallaxTerrainProvider g, RNG random)
	{
		return chanceCell.aquire(() ->
		{
			CellGenerator chanceCell = new CellGenerator(g.getMasterRandom().nextParallelRNG(-72346).nextParallelRNG((height + 10000) * rarity));
			chanceCell.setCellScale(1D);
			chanceCell.setShuffle(getShuffle());
			return chanceCell;
		});
	}
}
