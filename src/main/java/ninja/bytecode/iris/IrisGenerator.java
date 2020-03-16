package ninja.bytecode.iris;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.PolygonGenerator.EnumPolygonGenerator;
import ninja.bytecode.iris.util.RNG;

public class IrisGenerator extends ChunkGenerator
{
	private boolean initialized = false;
	private CNG gen;
	private EnumPolygonGenerator<BlockData> pog;
	private BlockData[] d = {Material.RED_CONCRETE.createBlockData(), Material.GREEN_CONCRETE.createBlockData(), Material.BLUE_CONCRETE.createBlockData(),
	};

	public void onInit(World world, RNG rng)
	{
		if(initialized)
		{
			return;
		}

		initialized = true;
		gen = CNG.signature(rng.nextParallelRNG(0));
		pog = new EnumPolygonGenerator<BlockData>(rng.nextParallelRNG(1), 0.1, 1, d, (c) -> c);
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return super.canSpawn(world, x, z);
	}

	@Override
	public ChunkData generateChunkData(World world, Random no, int x, int z, BiomeGrid biome)
	{
		RNG random = new RNG(world.getSeed());
		onInit(world, random.nextParallelRNG(0));
		ChunkData data = Bukkit.createChunkData(world);

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				double wx = (x * 16) + i;
				double wz = (z * 16) + j;

				data.setBlock(i, 0, j, pog.getChoice(wx, wz));
			}
		}

		return data;
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
}
