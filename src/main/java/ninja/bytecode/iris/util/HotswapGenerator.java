package ninja.bytecode.iris.util;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.generator.IrisGenerator;

@SuppressWarnings("deprecation")
public class HotswapGenerator extends ChunkGenerator
{
	private IrisGenerator gen;

	public HotswapGenerator(IrisGenerator gen)
	{
		setGenerator(gen);
	}

	public void setGenerator(IrisGenerator gen)
	{
		this.gen = gen;
	}

	public IrisGenerator getGenerator()
	{
		return gen;
	}

	@Override
	public byte[] generate(World world, Random random, int x, int z)
	{
		return gen.generate(world, random, x, z);
	}

	@Override
	public short[][] generateExtBlockSections(World world, Random random, int x, int z, BiomeGrid biomes)
	{
		return gen.generateExtBlockSections(world, random, x, z, biomes);
	}

	@Override
	public byte[][] generateBlockSections(World world, Random random, int x, int z, BiomeGrid biomes)
	{
		return gen.generateBlockSections(world, random, x, z, biomes);
	}

	@Override
	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		return gen.generateChunkData(world, random, x, z, biome);
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return gen.canSpawn(world, x, z);
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		return gen.getDefaultPopulators(world);
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return gen.getFixedSpawnLocation(world, random);
	}
}
