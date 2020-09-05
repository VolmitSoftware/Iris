package com.volmit.iris.gen;

import java.awt.Color;
import java.io.IOException;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisContext;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.atomics.AtomicRegionData;
import com.volmit.iris.gui.Renderer;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBlockDrops;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisEffect;
import com.volmit.iris.object.IrisEntitySpawn;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends PostBlockChunkGenerator implements IrisContext
{
	private IrisBiome hb = null;
	private IrisRegion hr = null;
	private boolean spawnable = false;

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
		if(isFailing())
		{
			setFailing(false);
			hotload();
		}
	}

	@Override
	public ChunkData generateChunkData(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		PrecisionStopwatch s = PrecisionStopwatch.start();
		ChunkData c = super.generateChunkData(world, no, x, z, biomeGrid);
		s.end();
		getMetrics().getTotal().put(s.getMilliseconds());
		return c;
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
	public IrisBiome getBiome(int x, int z)
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
		spawnable = true;
		super.onTick(ticks);
		for(Player i : getWorld().getPlayers())
		{
			Location l = i.getLocation();
			IrisRegion r = sampleRegion(l.getBlockX(), l.getBlockZ());
			IrisBiome b = sampleTrueBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());

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
			getParallaxMap().saveAll();
			getParallaxMap().getLoadedChunks().clear();
			getParallaxMap().getLoadedRegions().clear();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}

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
		if(!IrisSettings.get().isStudio())
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

		for(AtomicRegionData i : getParallaxMap().getLoadedRegions().values())
		{
			bytes += i.guessMemoryUsage();
		}

		bytes += getCache().getSize() * 65;
		bytes += getParallaxMap().getLoadedChunks().size() * 256 * 4 * 460;
		bytes += getSliverBuffer() * 220;
		bytes += 823 * getData().getObjectLoader().getTotalStorage();

		return bytes / 2;
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

	public Renderer createRenderer()
	{
		return (x, z) -> render(x, z);
	}

	private Color render(double x, double z)
	{
		int ix = (int) x;
		int iz = (int) z;
		double height = getTerrainHeight(ix, iz);
		IrisRegion region = sampleRegion(ix, iz);
		IrisBiome biome = sampleTrueBiome(ix, iz, height);

		if(biome.getCachedColor() != null)
		{
			return biome.getCachedColor();
		}

		float shift = (biome.hashCode() % 32) / 32f / 14f;
		float shift2 = (region.hashCode() % 9) / 9f / 14f;
		shift -= shift2;
		float sat = 0;
		float h = (biome.isLand() ? 0.233f : 0.644f) - shift;
		float s = 0.25f + shift + sat;
		float b = (float) (Math.max(0, Math.min(height + getFluidHeight(), 255)) / 255);

		Color c = Color.getHSBColor(h, s, b);

		return c;

	}

	public String textFor(double x, double z)
	{
		int ix = (int) x;
		int iz = (int) z;
		double height = getTerrainHeight(ix, iz);
		IrisRegion region = sampleRegion(ix, iz);
		IrisBiome biome = sampleTrueBiome(ix, iz, height);
		hb = biome;
		hr = region;
		return biome.getName() + " (" + Form.capitalizeWords(biome.getInferredType().name().toLowerCase().replaceAll("\\Q_\\E", " ") + ") in " + region.getName() + "\nY: " + (int) height);
	}

	public void saveAllParallax()
	{
		try
		{
			getParallaxMap().saveAll();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	protected void handleDrops(BlockDropItemEvent e)
	{
		int x = e.getBlock().getX();
		int y = e.getBlock().getY();
		int z = e.getBlock().getZ();
		IrisDimension dim = getDimension();
		IrisRegion reg = sampleRegion(x, z);
		IrisBiome bio = sampleTrueBiome(x, z);
		IrisBiome cbio = y < getFluidHeight() ? sampleTrueBiome(x, y, z) : null;

		if(cbio != null && bio.equals(cbio))
		{
			cbio = null;
		}

		if(dim.getBlockDrops().isEmpty() && reg.getBlockDrops().isEmpty() && bio.getBlockDrops().isEmpty())
		{
			return;
		}

		BlockData data = e.getBlockState().getBlockData();
		KList<ItemStack> drops = new KList<>();
		boolean skipParents = false;

		if(!skipParents && cbio != null)
		{
			for(IrisBlockDrops i : cbio.getBlockDrops())
			{
				if(i.shouldDropFor(data))
				{
					if(!skipParents && i.isSkipParents())
					{
						skipParents = true;
					}

					if(i.isReplaceVanillaDrops())
					{
						e.getItems().clear();
					}

					i.fillDrops(isDev(), drops);
				}
			}
		}

		if(!skipParents)
		{
			for(IrisBlockDrops i : bio.getBlockDrops())
			{
				if(i.shouldDropFor(data))
				{
					if(!skipParents && i.isSkipParents())
					{
						skipParents = true;
					}

					if(i.isReplaceVanillaDrops())
					{
						e.getItems().clear();
					}

					i.fillDrops(isDev(), drops);
				}
			}
		}

		if(!skipParents)
		{
			for(IrisBlockDrops i : reg.getBlockDrops())
			{
				if(i.shouldDropFor(data))
				{
					if(!skipParents && i.isSkipParents())
					{
						skipParents = true;
					}

					if(i.isReplaceVanillaDrops())
					{
						e.getItems().clear();
					}

					i.fillDrops(isDev(), drops);
				}
			}
		}

		if(!skipParents)
		{
			for(IrisBlockDrops i : dim.getBlockDrops())
			{
				if(i.shouldDropFor(data))
				{
					if(i.isReplaceVanillaDrops())
					{
						e.getItems().clear();
					}

					i.fillDrops(isDev(), drops);
				}
			}
		}

		if(drops.isNotEmpty())
		{
			Location l = e.getBlock().getLocation();

			for(ItemStack i : drops)
			{
				e.getBlock().getWorld().dropItemNaturally(l, i);
			}
		}
	}

	@Override
	protected void onSpawn(EntitySpawnEvent e)
	{
		if(isSpawnable())
		{
			int x = e.getEntity().getLocation().getBlockX();
			int y = e.getEntity().getLocation().getBlockY();
			int z = e.getEntity().getLocation().getBlockZ();
			IrisDimension dim = getDimension();
			IrisRegion region = sampleRegion(x, z);
			IrisBiome above = sampleTrueBiome(x, z);
			IrisBiome below = sampleTrueBiome(x, y, z);

			if(above.getLoadKey().equals(below.getLoadKey()))
			{
				below = null;
			}

			IrisStructureResult res = getStructure(x, y, z);

			if(res != null && res.getTile() != null)
			{
				if(trySpawn(res.getTile().getEntitySpawns(), e))
				{
					return;
				}
			}

			if(res != null && res.getStructure() != null)
			{
				if(trySpawn(res.getStructure().getEntitySpawns(), e))
				{
					return;
				}
			}

			if(below != null)
			{
				if(trySpawn(below.getEntitySpawns(), e))
				{
					return;
				}
			}

			if(trySpawn(above.getEntitySpawns(), e))
			{
				return;
			}

			if(trySpawn(region.getEntitySpawns(), e))
			{
				return;
			}

			if(trySpawn(dim.getEntitySpawns(), e))
			{
				return;
			}
		}
	}

	private boolean trySpawn(KList<IrisEntitySpawn> s, EntitySpawnEvent e)
	{
		for(IrisEntitySpawn i : s)
		{
			setSpawnable(false);

			if(i.on(this, e.getLocation(), e.getEntityType(), e) != null)
			{
				e.setCancelled(true);
				e.getEntity().remove();
				return true;
			}

			else
			{
				setSpawnable(true);
			}
		}

		return false;
	}
}
