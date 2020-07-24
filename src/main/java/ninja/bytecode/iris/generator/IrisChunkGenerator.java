package ninja.bytecode.iris.generator;

import java.lang.reflect.Method;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisContext;
import ninja.bytecode.iris.layer.GenLayerCave;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.RNG;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends CeilingChunkGenerator implements IrisContext
{
	private Method initLighting;
	private GenLayerCave caves;

	public IrisChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		caves = new GenLayerCave(this, new RNG(23456));
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 64; j++)
			{
				for(int k = 0; k < 16; k++)
				{
					if(caves.isCave((x * 16) + i, j, (z * 16) + k))
					{
						data.setBlock(i, j, k, Material.GREEN_STAINED_GLASS.createBlockData());
					}
				}
			}
		}
	}

	@Override
	public BiomeResult getBiome(int x, int z)
	{
		return sampleBiome(x, z);
	}

	@Override
	public IrisRegion getRegion(int x, int z)
	{
		return sampleRegion(x, z);
	}

	@Override
	public int getHeight(int x, int z)
	{
		return sampleHeight(x, z);
	}

	@Override
	protected void onTick(int ticks)
	{

	}

	@Override
	protected void onClose()
	{
		super.onClose();
		Iris.info("Closing Iris Dimension " + getWorld().getName());
	}

	@Override
	protected void onFailure(Throwable e)
	{

	}

	@Override
	protected void onChunkLoaded(Chunk c)
	{
		updateLights();
	}

	@Override
	protected void onChunkUnloaded(Chunk c)
	{

	}

	@Override
	protected void onPlayerJoin(Player p)
	{

	}

	@Override
	protected void onPlayerLeft(Player p)
	{

	}
}
