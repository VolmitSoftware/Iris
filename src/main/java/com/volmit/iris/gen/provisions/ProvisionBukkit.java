package com.volmit.iris.gen.provisions;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.gen.scaffold.HeightedFakeWorld;
import com.volmit.iris.gen.scaffold.Provisioned;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainProvider;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.J;
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

	public ProvisionBukkit(TerrainProvider provider)
	{
		this.provider = provider;
		precache = new KMap<>();
	}

	public void generate(World world, int x, int z)
	{
		world.loadChunk(x, z, true);
		world.unloadChunkRequest(x, z);
	}

	public void generateAsync(World world, int x, int z)
	{
		ChunkPosition c = new ChunkPosition(x, z);

		if(!precache.containsKey(c))
		{
			TerrainChunk snapshot = TerrainChunk.create(world);
			snapshot.setRaw(generateChunkData(world, getRNG(world, x, z), x, z, snapshot));
			precache.put(c, snapshot);
			J.s(() -> generate(world, x, z));
		}
	}

	@Override
	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		if(!worldSet)
		{
			worldSet = true;
			provider.getTarget().setRealWorld(world);
		}

		if(precache.size() > 0)
		{
			ChunkPosition c = new ChunkPosition(x, z);
			if(precache.containsKey(c))
			{
				TerrainChunk snapshot = precache.remove(c);
				snapshot.inject(biome);
				return snapshot;
			}
		}

		TerrainChunk terrain = TerrainChunk.create(world, biome);
		getProvider().generate(getRNG(world, x, z), x, z, terrain);
		return terrain.getRaw();
	}

	private Random getRNG(World world, int x, int z)
	{
		return new RNG(world.getSeed()).nextParallelRNG(x).nextParallelRNG(z);
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return provider.canSpawn(x, z);
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		return provider.getPopulators();
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return super.getFixedSpawnLocation(world, random);
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
