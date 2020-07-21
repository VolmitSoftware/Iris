package ninja.bytecode.iris.generator;

import java.lang.reflect.Method;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisContext;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends ParallaxChunkGenerator implements IrisContext
{
	private Method initLighting;

	public IrisChunkGenerator(String dimensionName, int threads)
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
		// TODO: updateLights(); better
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
