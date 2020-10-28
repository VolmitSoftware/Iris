package com.volmit.iris.gen;

import java.io.IOException;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.atomics.AtomicWorldData;
import com.volmit.iris.gen.atomics.MasterLock;
import com.volmit.iris.gen.layer.GenLayerText;
import com.volmit.iris.gen.layer.GenLayerUpdate;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeMutation;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructurePlacement;
import com.volmit.iris.object.IrisTextPlacement;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.CarveResult;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IObjectPlacer;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Spiraler;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallaxTerrainProvider extends TopographicTerrainProvider implements IObjectPlacer
{
	private short cacheID = 0;
	private KMap<ChunkPosition, AtomicSliver> sliverCache;
	private AtomicWorldData parallaxMap;
	private MasterLock masterLock;
	private IrisLock flock = new IrisLock("ParallaxLock");
	private IrisLock lock = new IrisLock("ParallaxLock");
	private GenLayerUpdate glUpdate;
	private GenLayerText glText;
	private int sliverBuffer;

	public ParallaxTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
		setSliverCache(new KMap<>());
		setSliverBuffer(sliverBuffer);
		setMasterLock(new MasterLock());
		getMasterLock().disable();
	}

	public void onInit(RNG rng)
	{
		super.onInit(rng);
		setParallaxMap(new AtomicWorldData(getTarget()));
		setGlText(new GenLayerText(this, rng.nextParallelRNG(32485)));
		setGlUpdate(null);
		J.a(() -> getDimension().getParallaxSize(this));
	}

	protected void onClose()
	{
		super.onClose();

		try
		{
			getParallaxMap().unloadAll(true);
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public int getHighest(int x, int z)
	{
		return getHighest(x, z, false);
	}

	@Override
	public void onHotload()
	{
		getData().preferFolder(getDimension().getLoadFile().getParentFile().getParentFile().getName());
		super.onHotload();
		setCacheID(RNG.r.simax());

		if(IrisSettings.get().isRegenerateLoadedChunksOnHotload())
		{
			World w = getTarget().getRealWorld();

			if(w != null)
			{
				for(Player i : w.getPlayers())
				{
					new Spiraler(10, 10, (a, b) -> getProvisioner().regenerate(i.getLocation().getChunk().getX() + a, i.getLocation().getChunk().getZ() + b)).drain();
				}

				for(Chunk i : w.getLoadedChunks())
				{
					getProvisioner().regenerate(i.getX(), i.getZ());
				}
			}
		}
	}

	@Override
	public int getHighest(int x, int z, boolean ignoreFluid)
	{
		return getCarvedHeight(x, z, ignoreFluid);
	}

	@Override
	public void set(int x, int y, int z, FastBlockData d)
	{
		getParallaxSliver(x, z).set(y, d);
	}

	@Override
	public FastBlockData get(int x, int y, int z)
	{
		FastBlockData b = sampleSliver(x, z).getBlock()[y];
		return b == null ? AIR : b;
	}

	@Override
	public boolean isSolid(int x, int y, int z)
	{
		return get(x, y, z).getMaterial().isSolid();
	}

	public AtomicSliver getParallaxSliver(int wx, int wz)
	{
		getMasterLock().lock("gpc");
		getMasterLock().lock((wx >> 4) + "." + (wz >> 4));
		AtomicSliverMap map = getParallaxChunk(wx >> 4, wz >> 4);
		getMasterLock().unlock("gpc");
		AtomicSliver sliver = map.getSliver(wx & 15, wz & 15);
		getMasterLock().unlock((wx >> 4) + "." + (wz >> 4));

		return sliver;
	}

	public boolean isParallaxGenerated(int x, int z)
	{
		return getParallaxChunk(x, z).isParallaxGenerated();
	}

	public boolean isWorldGenerated(int x, int z)
	{
		return getParallaxChunk(x, z).isWorldGenerated();
	}

	public AtomicSliverMap getParallaxChunk(int x, int z)
	{
		try
		{
			if(getParallaxMap() == null)
			{
				if(getTarget() == null)
				{
					Iris.warn("Parallax map was accessed before init! Serving a dummy chunk!");
					return new AtomicSliverMap();
				}

				else
				{
					setParallaxMap(new AtomicWorldData(getTarget()));
				}
			}

			return getParallaxMap().loadChunk(x, z);
		}

		catch(IOException e)
		{
			fail(e);
		}

		return new AtomicSliverMap();
	}

	public void initGLUpdate()
	{
		if(getGlUpdate() == null)
		{
			setGlUpdate(new GenLayerUpdate(this));
		}
	}

	@Override
	public List<BlockPopulator> getPopulators()
	{
		List<BlockPopulator> g = new KList<>();
		initGLUpdate();
		g.add(getGlUpdate());
		return g;
	}

	public void forgetThisParallaxChunk(int x, int z)
	{
		getParallaxChunk(x, z).reset();
		getSliverCache().clear();
		getCache().drop();
	}

	public void forgetParallaxChunksNear(int x, int z)
	{
		getSliverCache().clear();
		getCache().drop();
		ChunkPosition rad = getDimension().getParallaxSize(this);
		for(int ii = x - (rad.getX() / 2); ii <= x + (rad.getX() / 2); ii++)
		{
			for(int jj = z - (rad.getZ() / 2); jj <= z + (rad.getZ() / 2); jj++)
			{
				getParallaxChunk(ii, jj).reset();
			}
		}
	}

	@Override
	protected void onPostGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		if(getSliverCache().size() > 20000)
		{
			getSliverCache().clear();
		}

		super.onPostGenerate(random, x, z, terrain, height, biomeMap, map);
		PrecisionStopwatch p = PrecisionStopwatch.start();

		if(getDimension().isPlaceObjects())
		{
			onGenerateParallax(random, x, z);
			getParallaxChunk(x, z).inject(terrain);
			getParallaxChunk(x, z).injectUpdates(map);
			getParallaxChunk(x, z).setWorldGenerated(true);
		}

		setSliverBuffer(getSliverCache().size());
		getMasterLock().clear();
		p.end();
		getMetrics().getParallax().put(p.getMilliseconds());
		super.onPostParallaxPostGenerate(random, x, z, terrain, height, biomeMap, map);
		getParallaxMap().clean();
		getData().getObjectLoader().clean();
	}

	public IrisStructureResult getStructure(int x, int y, int z)
	{
		return getParallaxChunk(x >> 4, z >> 4).getStructure(this, y);
	}

	protected void onGenerateParallax(RNG randomx, int x, int z)
	{
		String key = "par." + x + "." + z;
		ChunkPosition rad = getDimension().getParallaxSize(this);

		for(int ii = x - (rad.getX() / 2); ii <= x + (rad.getX() / 2); ii++)
		{
			int i = ii;

			for(int jj = z - (rad.getZ() / 2); jj <= z + (rad.getZ() / 2); jj++)
			{
				int j = jj;
				RNG salt = new RNG(2922 + i + j).nextParallelRNG(i - 293938).nextParallelRNG(j + 294416);
				RNG random = getMasterRandom().nextParallelRNG(i + salt.imax()).nextParallelRNG(j + salt.imax());

				if(isParallaxGenerated(ii, jj))
				{
					continue;
				}

				if(isWorldGenerated(ii, jj))
				{
					continue;
				}

				getAccelerant().queue(key, () ->
				{
					IrisBiome b = sampleTrueBiome((i * 16) + 7, (j * 16) + 7);
					IrisRegion r = sampleRegion((i * 16) + 7, (j * 16) + 7);
					RNG ro = getMasterRandom().nextParallelRNG(196888 + i + j + 2225).nextParallelRNG(salt.i(-i, i)).nextParallelRNG(salt.i(-j, j));
					int g = 1;
					g = placeMutations(ro, random, i, j, g);
					g = placeText(random, r, b, i, j, g);
					g = placeObjects(random, r, b, i, j, g);
					g = placeCarveObjects(random, r, b, i, j, g);
					g = placeCaveObjects(ro, random, i, j, g);
					g = placeStructures(randomx, r, b, i, j, g);
				});

				getParallaxChunk(ii, jj).setParallaxGenerated(true);
			}
		}

		getAccelerant().waitFor(key);
	}

	private int placeMutations(RNG ro, RNG random, int i, int j, int g)
	{
		searching: for(IrisBiomeMutation k : getDimension().getMutations())
		{
			for(int l = 0; l < k.getChecks(); l++)
			{
				IrisBiome sa = sampleTrueBiome(((i * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius()), ((j * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius()));
				IrisBiome sb = sampleTrueBiome(((i * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius()), ((j * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius()));

				if(sa.getLoadKey().equals(sb.getLoadKey()))
				{
					continue;
				}

				if(k.getRealSideA(this).contains(sa.getLoadKey()) && k.getRealSideB(this).contains(sb.getLoadKey()))
				{
					for(IrisObjectPlacement m : k.getObjects())
					{
						placeObject(m, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 1569962));
					}

					continue searching;
				}
			}
		}

		return g;
	}

	private int placeText(RNG random, IrisRegion r, IrisBiome b, int i, int j, int g)
	{
		for(IrisTextPlacement k : getDimension().getText())
		{
			k.place(this, random.nextParallelRNG(g++ + -7228 + (34 * ((i * 30) + (j * 30)) * i * j) + i - j + 1569962), i, j);
		}

		for(IrisTextPlacement k : r.getText())
		{
			k.place(this, random.nextParallelRNG(g++ + -4228 + -7228 + (34 * ((i * 30) + (j * 30)) * i * j) + i - j + 1569962), i, j);
		}

		for(IrisTextPlacement k : b.getText())
		{
			k.place(this, random.nextParallelRNG(g++ + -22228 + -4228 + -7228 + (34 * ((i * 30) + (j * 30)) * i * j) + i - j + 1569962), i, j);
		}

		return g;
	}

	private int placeObjects(RNG random, IrisRegion r, IrisBiome b, int i, int j, int g)
	{
		for(IrisObjectPlacement k : b.getSurfaceObjects())
		{
			placeObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 3566522));
		}

		if(r == null)
		{
			Iris.error("R Null");
		}

		if(r.getSurfaceObjects() == null)
		{
			Iris.error("Surface objects null");
		}

		if(r != null)
		{
			for(IrisObjectPlacement k : r.getSurfaceObjects())
			{
				placeObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 3569222));
			}
		}

		return g;
	}

	private int placeCarveObjects(RNG random, IrisRegion r, IrisBiome b, int i, int j, int g)
	{
		if(!getGlCarve().isCouldCarve())
		{
			return g;
		}

		KList<CarveResult> layers = getGlCarve().getCarveLayers((i * 16) + random.nextInt(16), (j * 16) + random.nextInt(16));

		if(layers.isEmpty())
		{
			return g + 2;
		}

		for(CarveResult c : layers)
		{
			g = placeCarveObjects(random, r, b, i, j, g, c);
		}

		return g;
	}

	private int placeCarveObjects(RNG random, IrisRegion r, IrisBiome b, int i, int j, int g, CarveResult c)
	{
		for(IrisObjectPlacement k : b.getCarvingObjects())
		{
			placeCarveObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 3569221 + g), c);
		}

		for(IrisObjectPlacement k : r.getCarvingObjects())
		{
			placeCarveObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 3561222 + g), c);
		}

		return g;
	}

	private int placeCaveObjects(RNG ro, RNG random, int i, int j, int g)
	{
		if(!getDimension().isCaves())
		{
			return g;
		}

		int bx = (i * 16) + ro.nextInt(16);
		int bz = (j * 16) + ro.nextInt(16);

		IrisBiome biome = sampleCaveBiome(bx, bz);

		if(biome == null)
		{
			return g;
		}

		if(biome.getObjects().isEmpty())
		{
			return g;
		}

		for(IrisObjectPlacement k : biome.getObjects())
		{
			int gg = g++;
			placeCaveObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + gg) * i * j) + i - j + 1869322));
		}

		return g;
	}

	private int placeStructures(RNG random, IrisRegion r, IrisBiome b, int i, int j, int g)
	{
		for(IrisStructurePlacement k : r.getStructures())
		{
			if(k == null)
			{
				continue;
			}

			k.place(this, random.nextParallelRNG(2228 * 2 * g++), i, j);
		}

		for(IrisStructurePlacement k : b.getStructures())
		{
			if(k == null)
			{
				continue;
			}

			k.place(this, random.nextParallelRNG(-22228 * 4 * g++), i, j);
		}

		return g;
	}

	public void placeObject(IrisObjectPlacement o, int x, int z, RNG rng)
	{
		for(int i = 0; i < o.getTriesForChunk(rng); i++)
		{
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			o.getSchematic(this, rng).place((x * 16) + rng.nextInt(16), (z * 16) + rng.nextInt(16), this, o, rng, getData());
		}
	}

	public void placeCarveObject(IrisObjectPlacement o, int x, int z, RNG rng, CarveResult c)
	{
		for(int i = 0; i < o.getTriesForChunk(rng); i++)
		{
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			o.getSchematic(this, rng).place((x * 16) + rng.nextInt(16), (z * 16) + rng.nextInt(16), this, o, rng, c, getData());
		}
	}

	public void placeCaveObject(IrisObjectPlacement o, int x, int z, RNG rng)
	{
		for(int i = 0; i < o.getTriesForChunk(rng); i++)
		{
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			int xx = (x * 16) + rng.nextInt(16);
			int zz = (z * 16) + rng.nextInt(16);
			KList<CaveResult> res = getCaves(xx, zz);

			if(res.isEmpty())
			{
				continue;
			}

			o.getSchematic(this, rng).place(xx, res.get(rng.nextParallelRNG(29345 * (i + 234)).nextInt(res.size())).getFloor() + 2, zz, this, o, rng, getData());
		}
	}

	public AtomicSliver sampleSliver(int x, int z)
	{
		ChunkPosition key = new ChunkPosition(x, z);

		if(getSliverCache().containsKey(key))
		{
			return getSliverCache().get(key);
		}

		AtomicSliver s = new AtomicSliver(x & 15, z & 15);
		onGenerateColumn(x >> 4, z >> 4, x, z, x & 15, z & 15, s, null, true);
		getSliverCache().put(key, s);

		return s;
	}

	@Override
	public boolean isDebugSmartBore()
	{
		return getDimension().isDebugSmartBore();
	}

	@Override
	public boolean isPreventingDecay()
	{
		return getDimension().isPreventLeafDecay();
	}
}
