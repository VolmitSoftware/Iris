package com.volmit.iris.generator;

import java.lang.reflect.Method;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisContext;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.KMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends CeilingChunkGenerator implements IrisContext
{
	private Method initLighting;
	private KMap<Player, IrisBiome> b = new KMap<>();

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
		super.onTick(ticks);
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

	@Override
	public void onHotloaded()
	{
		onHotload();
	}
}
