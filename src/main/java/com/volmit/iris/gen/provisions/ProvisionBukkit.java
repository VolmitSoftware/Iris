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

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ProvisionBukkit extends ChunkGenerator implements Provisioned
{
	private HeightedFakeWorld fakeworld = null;
	private boolean worldSet = false;
	private final TerrainProvider provider;

	public ProvisionBukkit(TerrainProvider provider)
	{
		this.provider = provider;
	}

	@Override
	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		if(!worldSet)
		{
			worldSet = true;
			provider.getTarget().setRealWorld(world);
		}

		TerrainChunk terrain = TerrainChunk.create(world, biome);
		getProvider().generate(random, x, z, terrain);
		return terrain.getRaw();
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
