package com.volmit.iris.gen.provisions;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.scaffold.GeneratedChunk;
import com.volmit.iris.gen.scaffold.HeightedFakeWorld;
import com.volmit.iris.gen.scaffold.Provisioned;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainProvider;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.FastBlockData;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ProvisionBukkit extends ChunkGenerator implements Provisioned
{
	private HeightedFakeWorld fakeworld = null;
	private boolean worldSet = false;
	private final TerrainProvider provider;
	private final KMap<ChunkPosition, TerrainChunk> precache;
	private KList<ChunkPosition> regenerated = new KList<ChunkPosition>();
	private Executor e = Executors.newFixedThreadPool(Iris.getThreadCount());
	private World cachedWorld;

	public ProvisionBukkit(TerrainProvider provider)
	{
		this.provider = provider;
		provider.setProvisioner(this);
		precache = new KMap<>();
	}

	public void clearRegeneratedLists()
	{
		regenerated.clear();
		Iris.clearQueues();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void regenerate(int x, int z)
	{
		boolean exists = cachedWorld.loadChunk(x, z, false);

		if(!exists)
		{
			return;
		}

		ChunkPosition ccp = new ChunkPosition(x, z);

		if(regenerated.contains(ccp))
		{
			return;
		}

		regenerated.add(ccp);
		e.execute(() ->
		{
			((IrisTerrainProvider) getProvider()).forgetParallaxChunksNear(x, z);
			TerrainChunk snapshot = TerrainChunk.create(cachedWorld);
			snapshot.setRaw(generateChunkData(cachedWorld, getRNG(cachedWorld, x, z), x, z, snapshot));

			int cx = x * 16;
			int cz = z * 16;
			Iris.sq(() ->
			{
				for(int hh = 0; hh < 16; hh++)
				{
					int h = hh;

					for(int i = 0; i < 16; i++)
					{
						for(int j = 0; j < 16; j++)
						{
							for(int k = 0; k < 16; k++)
							{
								FastBlockData b = FastBlockData.of(snapshot.getBlockData(i, (h * 16) + j, k));

								if(!Iris.edit.get(cachedWorld, i + cx, (h * 16) + j, k + cz).equals(b))
								{
									Iris.edit.set(cachedWorld, i + cx, (h * 16) + j, k + cz, b);
								}

								if(Iris.biome3d)
								{
									Biome bo = snapshot.getBiome(i, (h * 16) + j, k);

									if(!Iris.edit.getBiome(cachedWorld, i, (h * 16) + j, k).equals(bo))
									{
										Iris.edit.setBiome(cachedWorld, i + cx, (h * 16) + j, k + cz, bo);
									}

								}

								else if(j == 0 && h == 0)
								{
									Biome bo = snapshot.getBiome(i, k);

									if(!Iris.edit.getBiome(cachedWorld, i, k).equals(bo))
									{
										Iris.edit.setBiome(cachedWorld, i + cx, k + cz, bo);
									}
								}
							}
						}
					}
				}
			});
		});
	}

	public void generate(World world, int x, int z)
	{
		cachedWorld = world;
		world.loadChunk(x, z, true);
		world.unloadChunkRequest(x, z);
	}

	public void generateAsync(World world, int x, int z, Consumer<Boolean> onDone)
	{
		cachedWorld = world;
		ChunkPosition c = new ChunkPosition(x, z);

		if(!precache.containsKey(c))
		{
			TerrainChunk snapshot = TerrainChunk.create(world);
			snapshot.setRaw(generateChunkData(world, getRNG(world, x, z), x, z, snapshot));
			precache.put(c, snapshot);
			onDone.accept(true);
		}

		else
		{
			onDone.accept(false);
		}
	}

	public void fixSpawn(World w)
	{
		cachedWorld = w;
		if(w.getSpawnLocation().getY() == 0 && w.getSpawnLocation().getZ() == 0 && w.getSpawnLocation().getX() == 0)
		{
			w.setSpawnLocation(provider.computeSpawn((b) ->
			{
				Location at = b.toLocation(w);
				Location ab = at.clone().add(0, 1, 0);
				Location bl = at.clone().add(0, -1, 0);

				if(!bl.getBlock().getType().isSolid())
				{
					return false;
				}

				if(ab.getBlock().getType().isSolid())
				{
					return false;
				}

				if(at.getBlock().getType().isSolid())
				{
					return false;
				}

				if(!ab.getBlock().getType().isAir())
				{
					return false;
				}

				if(!at.getBlock().getType().isAir())
				{
					return false;
				}

				return true;
			}).toLocation(w));
			Iris.info("Fixed " + w.getName() + " Spawn to " + w.getSpawnLocation().getBlockX() + ", " + w.getSpawnLocation().getBlockY() + ", " + w.getSpawnLocation().getBlockZ());
		}
	}

	@Override
	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		cacheWorld(world);

		if(precache.size() > 0)
		{
			ChunkPosition c = new ChunkPosition(x, z);
			if(precache.containsKey(c))
			{
				TerrainChunk snapshot = precache.remove(c);
				snapshot.inject(biome);
				return snapshot.getRaw();
			}
		}

		TerrainChunk terrain = TerrainChunk.create(world, biome);
		getProvider().generate(getRNG(world, x, z), x, z, terrain);
		return terrain.getRaw();
	}

	public GeneratedChunk generateNMSChunkData(World world, Random random, int x, int z, ChunkData nmsData, BiomeGrid biome)
	{
		cacheWorld(world);
		TerrainChunk terrain = TerrainChunk.create(nmsData, biome);
		return getProvider().generate(getRNG(world, x, z), x, z, terrain);
	}

	private void cacheWorld(World world)
	{
		cachedWorld = world;
		if(!worldSet)
		{
			worldSet = true;
			provider.getTarget().setRealWorld(world);

			if(world.getSpawnLocation().getY() == 0 && world.getSpawnLocation().getZ() == 0 && world.getSpawnLocation().getX() == 0)
			{
				Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> fixSpawn(world));
			}
		}
	}

	private Random getRNG(World world, int x, int z)
	{
		cachedWorld = world;
		return new RNG(world.getSeed()).nextParallelRNG(x).nextParallelRNG(z);
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		cachedWorld = world;
		return provider.canSpawn(x, z);
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		cachedWorld = world;
		return provider.getPopulators();
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return null;
	}

	@Override
	public boolean isParallelCapable()
	{
		return provider.isParallelCapable();
	}

	@Override
	public boolean shouldGenerateCaves()
	{
		return provider.shouldGenerateCaves();
	}

	@Override
	public boolean shouldGenerateDecorations()
	{
		return super.shouldGenerateDecorations();
	}

	@Override
	public boolean shouldGenerateMobs()
	{
		return provider.shouldGenerateMobs();
	}

	@Override
	public boolean shouldGenerateStructures()
	{
		return provider.shouldGenerateVanillaStructures();
	}
}
