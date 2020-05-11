package ninja.bytecode.iris.generator;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.IrisContext;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisGenerator extends ParallaxChunkGenerator implements IrisContext
{
	public IrisGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
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

	}

	@Override
	protected void onFailure(Throwable e)
	{

	}

	@Override
	protected void onChunkLoaded(Chunk c)
	{

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
