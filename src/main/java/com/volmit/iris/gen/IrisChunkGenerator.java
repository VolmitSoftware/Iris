package com.volmit.iris.gen;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisContext;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.atomics.AtomicRegionData;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisEffect;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends PostBlockChunkGenerator implements IrisContext
{
	private Method initLighting;
	private IrisBiome hb = null;
	private IrisRegion hr = null;
	private KMap<Player, IrisBiome> b = new KMap<>();

	public IrisChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	public IrisChunkGenerator(String dimensionName)
	{
		super(dimensionName, 16);
	}

	public IrisChunkGenerator(int tc)
	{
		super("", tc);
	}

	public void hotload()
	{
		onHotload();
	}

	public void retry()
	{
		if(failing)
		{
			failing = false;
			hotload();
		}
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, data, grid);
	}

	public void onInit(World world, RNG rng)
	{
		try
		{
			super.onInit(world, rng);
		}

		catch(Throwable e)
		{
			fail(e);
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
	public void onTick(int ticks)
	{
		super.onTick(ticks);
		for(Player i : getWorld().getPlayers())
		{
			Location l = i.getLocation();
			IrisRegion r = sampleRegion(l.getBlockX(), l.getBlockZ());
			IrisBiome b = sampleTrueBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ()).getBiome();

			for(IrisEffect j : r.getEffects())
			{
				j.apply(i, this);
			}

			for(IrisEffect j : b.getEffects())
			{
				j.apply(i, this);
			}
		}
	}

	@Override
	protected void onClose()
	{
		super.onClose();

		try
		{
			parallaxMap.saveAll();
			parallaxMap.getLoadedChunks().clear();
			parallaxMap.getLoadedRegions().clear();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}

		setAvailableFilters(null);
		setSliverCache(null);
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
	public void onPlayerLeft(Player p)
	{
		super.onPlayerLeft(p);
	}

	@Override
	public void onHotloaded()
	{
		if(!IrisSettings.get().hotloading)
		{
			return;
		}

		if(!isHotloadable())
		{
			Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, this::onHotloaded);
			return;
		}

		CNG.creates = 0;
		getData().dump();
		getCache().drop();
		onHotload();
		Iris.proj.updateWorkspace(Iris.proj.getWorkspaceFile(getDimension().getLoadKey()));
	}

	public long guessMemoryUsage()
	{
		long bytes = 1024 * 1024 * (8 + (getThreads() / 3));

		for(AtomicRegionData i : parallaxMap.getLoadedRegions().values())
		{
			bytes += i.guessMemoryUsage();
		}

		bytes += getCache().getSize() * 65;
		bytes += parallaxMap.getLoadedChunks().size() * 256 * 4 * 460;
		bytes += getSliverBuffer() * 220;
		bytes += 823 * getData().getObjectLoader().getTotalStorage();

		return bytes;
	}

	@Override
	public boolean shouldGenerateCaves()
	{
		return false;
	}

	@Override
	public boolean shouldGenerateDecorations()
	{
		return false;
	}

	@Override
	public boolean shouldGenerateMobs()
	{
		return true;
	}

	@Override
	public boolean shouldGenerateStructures()
	{
		if(!isInitialized())
		{
			return false;
		}

		return getDimension().isVanillaStructures();
	}

	public Function2<Double, Double, Color> createRenderer()
	{
		return (x, z) -> render(x, z);
	}

	private Color render(double x, double z)
	{
		int ix = (int) x;
		int iz = (int) z;
		double height = getTerrainHeight(ix, iz);
		IrisRegion region = sampleRegion(ix, iz);
		IrisBiome biome = sampleTrueBiome(ix, iz, height).getBiome();

		float shift = (biome.hashCode() % 32) / 32f / 14f;
		float shift2 = (region.hashCode() % 9) / 9f / 14f;
		shift -= shift2;
		float sat = 0;

		if(hr.getLoadKey().equals(region.getLoadKey()))
		{
			sat += 0.2;
		}

		if(hb.getLoadKey().equals(biome.getLoadKey()))
		{
			sat += 0.3;
		}

		Color c = Color.getHSBColor((biome.isLand() ? 0.233f : 0.644f) - shift, 0.25f + shift + sat, (float) (Math.max(0, Math.min(height + getFluidHeight(), 255)) / 255));

		return c;

	}

	public String textFor(double x, double z)
	{

		int ix = (int) x;
		int iz = (int) z;
		double height = getTerrainHeight(ix, iz);
		IrisRegion region = sampleRegion(ix, iz);
		IrisBiome biome = sampleTrueBiome(ix, iz, height).getBiome();
		hb = biome;
		hr = region;
		return biome.getName() + " (" + Form.capitalizeWords(biome.getInferredType().name().toLowerCase().replaceAll("\\Q_\\E", " ") + ") in " + region.getName() + "\nY: " + (int) height);
	}
}
