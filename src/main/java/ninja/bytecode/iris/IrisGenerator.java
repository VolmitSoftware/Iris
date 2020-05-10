package ninja.bytecode.iris;

import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.layer.GenLayerBiome;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.ChronoLatch;
import ninja.bytecode.iris.util.GroupedExecutor;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.format.Form;
import ninja.bytecode.shuriken.logging.L;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisGenerator extends ChunkGenerator implements IrisContext, Listener
{
	private String dimensionName;
	private IrisMetrics metrics;
	private World world;
	private ChronoLatch perSecond;
	private ChronoLatch pushLatch;
	private BlockData STONE = Material.STONE.createBlockData();
	private BlockData WATER = Material.WATER.createBlockData();
	private GenLayerBiome glBiome;
	private CNG terrainNoise;
	private RNG masterRandom;
	private GroupedExecutor tx;
	private boolean failing = false;
	private boolean initialized = false;
	private int generated = 0;
	private boolean pregenDone = false;
	private int task = -1;

	public IrisGenerator(String dimensionName)
	{
		this.dimensionName = dimensionName;
		pushLatch = new ChronoLatch(3000);
		perSecond = new ChronoLatch(1000);
		CNG.creates = 0;
	}

	public IrisDimension getDimension()
	{
		IrisDimension d = Iris.data.getDimensionLoader().load(dimensionName);

		if(d == null)
		{
			Iris.error("Can't find dimension: " + dimensionName);
		}

		return d;
	}

	public void onInit(World world, RNG rng)
	{
		if(initialized)
		{
			return;
		}

		this.world = world;
		this.masterRandom = new RNG(world.getSeed());
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
		terrainNoise = CNG.signature(masterRandom.nextParallelRNG(2));
		metrics = new IrisMetrics(128);
		initialized = true;
		tx = new GroupedExecutor(16, Thread.MIN_PRIORITY, "Iris Generator");
		Iris.executors.add(tx);
		Bukkit.getServer().getPluginManager().registerEvents(this, Iris.instance);
		task = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::tick, 0, 0);
	}

	public void tick()
	{
		if(perSecond.flip())
		{
			if(generated > 770)
			{
				pregenDone = true;
			}

			if(pregenDone)
			{
				metrics.getPerSecond().put(generated);
				generated = 0;
			}
		}
	}

	@EventHandler
	public void on(PlayerTeleportEvent e)
	{
		if(e.getFrom().getWorld().equals(world) && !e.getTo().getWorld().equals(world))
		{

		}

		if(!e.getFrom().getWorld().equals(world) && e.getTo().getWorld().equals(world))
		{

		}
	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		if(world != null && e.getWorld().equals(world))
		{
			close();
		}
	}

	public void close()
	{
		HandlerList.unregisterAll(this);
		Bukkit.getScheduler().cancelTask(getTask());
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return super.canSpawn(world, x, z);
	}

	public ChunkData generateChunkDataFailure(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		ChunkData c = Bukkit.createChunkData(world);

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				int h = 0;

				if(j == i || j + i == 16)
				{
					c.setBlock(i, h, j, BlockDataTools.getBlockData("RED_TERRACOTTA"));
				}

				else
				{
					c.setBlock(i, h, j, BlockDataTools.getBlockData("BLACK_TERRACOTTA"));
				}
			}
		}

		return c;
	}

	@Override
	public ChunkData generateChunkData(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		PrecisionStopwatch sx = PrecisionStopwatch.start();

		if(failing)
		{
			return generateChunkDataFailure(world, no, x, z, biomeGrid);
		}

		try
		{
			if(pushLatch.flip())
			{
				if(this.world == null)
				{
					this.world = world;
				}

				Iris.hotloader.check();
				IrisContext.pushContext(this);
				if(metrics != null)
				{
					Iris.info("Generating " + Form.f(1000D / metrics.getTotal().getAverage(), 0) + "/s (" + Form.duration(metrics.getTotal().getAverage(), 2) + ")");
				}
			}

			String key = "c" + x + "," + z;
			PrecisionStopwatch s = PrecisionStopwatch.start();
			RNG random = new RNG(world.getSeed());
			onInit(world, random.nextParallelRNG(0));
			ChunkData c = Bukkit.createChunkData(world);
			int ii, jj;
			int fluidHeight = getDimension().getFluidHeight();
			KList<Runnable> collapse = new KList<>();
			ReentrantLock l = new ReentrantLock();

			for(ii = 0; ii < 16; ii++)
			{
				int i = ii;
				for(jj = 0; jj < 16; jj++)
				{
					int j = jj;
					tx.queue(key, () ->
					{
						BlockData block;
						int rx = (x * 16) + i;
						int rz = (z * 16) + j;
						AtomicSliver sliver = new AtomicSliver(i, j);
						double ox = (getDimension().cosRotate() * rx) + (-getDimension().sinRotate() * rz) + getDimension().getCoordFracture(random, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
						double oz = (getDimension().sinRotate() * rx) + (getDimension().cosRotate() * rz) + getDimension().getCoordFracture(random, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
						double wx = (double) (ox) / getDimension().getTerrainZoom();
						double wz = (double) (oz) / getDimension().getTerrainZoom();
						int depth = 0;
						IrisRegion region = glBiome.getRegion(wx, wz);
						BiomeResult biomeResult = glBiome.generateRegionData(wx, wz, region);
						IrisBiome biome = biomeResult.getBiome();
						double lo = interpolateHeight(ox, oz, (b) -> b.getLowHeight());
						double hi = interpolateSurface(ox, oz, (b) -> b.getHighHeight());
						double noise = lo + (terrainNoise.fitDoubleD(0, hi - lo, wx, wz));
						int height = (int) Math.round(noise) + fluidHeight;

						// Remove Land biome surfaces from underwater
						if(height < fluidHeight + 1)
						{
							if(biome.isLand())
							{
								biome = glBiome.generateShoreData(wx, wz, region).getBiome();
							}
						}

						KList<BlockData> layers = biome.generateLayers(wx, wz, random, height);

						for(int k = Math.max(height, fluidHeight); k >= 0; k--)
						{
							boolean underwater = k > height && k <= fluidHeight;
							sliver.set(k, biome.getDerivative());
							// biomeGrid.setBiome(i, k, j, biome.getDerivative());

							if(underwater)
							{
								block = WATER;
							}

							else
							{
								block = layers.hasIndex(depth) ? layers.get(depth) : STONE;
								depth++;
							}

							sliver.set(k, block);
							// c.setBlock(i, k, j, block);
						}

						l.lock();
						collapse.add(() ->
						{
							sliver.write(c);
							sliver.write(biomeGrid);
						});
						l.unlock();
					});
				}
			}

			tx.waitFor(key);
			collapse.forEach((i) -> i.run());
			metrics.getTotal().put(s.getMilliseconds());
			generated++;
			long hits = CNG.hits;
			CNG.hits = 0;
			Iris.instance.hit(hits);
			metrics.getLoss().put(sx.getMilliseconds() - s.getMilliseconds());

			return c;
		}

		catch(Throwable e)
		{
			failing = true;
			Iris.error("ERROR! Failed to generate chunk! Iris has entered a failed state!");

			for(Player i : world.getPlayers())
			{
				Iris.instance.imsg(i, ChatColor.DARK_RED + "Iris Generator has entered a failed state!");
				Iris.instance.imsg(i, ChatColor.RED + "- Check the console for the error.");
				Iris.instance.imsg(i, ChatColor.RED + "- Then simply run /iris dev");
			}

			L.ex(e);
		}

		return generateChunkDataFailure(world, no, x, z, biomeGrid);
	}

	public double interpolateHeight(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationScale(), (xx, zz) ->
		{
			BiomeResult neighborResult = glBiome.generateData(xx / getDimension().getTerrainZoom(), zz / getDimension().getTerrainZoom());
			return property.apply(neighborResult.getBiome());
		});
	}

	public double interpolateSurface(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationSurfaceFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationSurfaceScale(), (xx, zz) ->
		{
			BiomeResult neighborResult = glBiome.generateData(xx / getDimension().getTerrainZoom(), zz / getDimension().getTerrainZoom());
			return property.apply(neighborResult.getBiome());
		});
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		return super.getDefaultPopulators(world);
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return super.getFixedSpawnLocation(world, random);
	}

	@Override
	public boolean isParallelCapable()
	{
		return true;
	}

	@Override
	public IrisMetrics getMetrics()
	{
		return metrics;
	}

	@Override
	public World getWorld()
	{
		return world;
	}

	@Override
	public BiomeResult getBiome(int rx, int rz)
	{
		RNG random = new RNG(world.getSeed());
		double ox = (getDimension().cosRotate() * rx) + (-getDimension().sinRotate() * rz) + getDimension().getCoordFracture(random, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
		double oz = (getDimension().sinRotate() * rx) + (getDimension().cosRotate() * rz) + getDimension().getCoordFracture(random, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
		double wx = (double) (ox) / getDimension().getTerrainZoom();
		double wz = (double) (oz) / getDimension().getTerrainZoom();
		IrisRegion region = glBiome.getRegion(wx, wz);
		return glBiome.generateRegionData(wx, wz, region);
	}
}
