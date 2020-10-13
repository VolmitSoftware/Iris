package com.volmit.iris.gen;

import java.awt.Color;
import java.io.IOException;
import java.util.Random;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockVector;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.atomics.AtomicRegionData;
import com.volmit.iris.gen.scaffold.IrisContext;
import com.volmit.iris.gen.scaffold.IrisGenConfiguration;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.gui.Renderer;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBlockDrops;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisEffect;
import com.volmit.iris.object.IrisEntityInitialSpawn;
import com.volmit.iris.object.IrisEntitySpawnOverride;
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
public class IrisTerrainProvider extends SkyTerrainProvider implements IrisContext
{
	private IrisBiome hb = null;
	private IrisRegion hr = null;
	private boolean spawnable = false;

	public IrisTerrainProvider(IrisGenConfiguration config)
	{
		super(config.getTarget(), config.getDimension(), config.getThreads());
	}

	public IrisTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
	}

	public IrisTerrainProvider(TerrainTarget t, String dimensionName)
	{
		super(t, dimensionName, 16);
	}

	public IrisTerrainProvider(TerrainTarget t, int tc)
	{
		super(t, "", tc);
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
	public void generate(Random no, int x, int z, TerrainChunk terrain)
	{
		PrecisionStopwatch s = PrecisionStopwatch.start();
		super.generate(no, x, z, terrain);
		s.end();
		getMetrics().getTotal().put(s.getMilliseconds());
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		super.onGenerate(random, x, z, terrain);
	}

	public void onInit(RNG rng)
	{
		try
		{
			super.onInit(rng);
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
		tickEffects();
	}

	protected void tickEffects()
	{
		if(!IrisSettings.get().isSystemEffects())
		{
			return;
		}

		for(Player i : getTarget().getPlayers())
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
		Iris.info("Closing Iris Dimension " + getTarget().getName());
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
		if(getDimension().getResourcePack().trim().isEmpty())
		{
			return;
		}

		p.setResourcePack(getDimension().getResourcePack());
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
		Iris.proj.updateWorkspace();
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

	public Renderer createRenderer()
	{
		return this::render;
	}

	public Color render(double x, double z)
	{
		int ix = (int) x;
		int iz = (int) z;
		double height = getTerrainHeight(ix, iz);
		IrisRegion region = sampleRegion(ix, iz);
		IrisBiome biome = sampleTrueBiome(ix, iz);

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

		return Color.getHSBColor(h, s, b);

	}

	public String textFor(double x, double z)
	{
		int ix = (int) x;
		int iz = (int) z;
		double height = getTerrainHeight(ix, iz);
		IrisRegion region = sampleRegion(ix, iz);
		IrisBiome biome = sampleTrueBiome(ix, iz);
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

		if(bio.equals(cbio))
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

		if(cbio != null)
		{
			for(IrisBlockDrops i : cbio.getBlockDrops())
			{
				if(i.shouldDropFor(data, getData()))
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
				if(i.shouldDropFor(data, getData()))
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
				if(i.shouldDropFor(data, getData()))
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
				if(i.shouldDropFor(data, getData()))
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

	public void spawnInitials(Chunk c, RNG rng)
	{
		int x = (c.getX() * 16) + rng.nextInt(15);
		int z = (c.getZ() * 16) + rng.nextInt(15);
		int y = getCarvedHeight(x, z) + 1;
		IrisDimension dim = getDimension();
		IrisRegion region = sampleRegion(x, z);
		IrisBiome above = sampleTrueBiome(x, z);

		IrisStructureResult res = getStructure(x, y, z);

		if(res != null && res.getTile() != null)
		{
			trySpawn(res.getTile().getEntityInitialSpawns(), c, rng);
		}

		if(res != null && res.getStructure() != null)
		{
			trySpawn(res.getStructure().getEntityInitialSpawns(), c, rng);

		}

		trySpawn(above.getEntityInitialSpawns(), c, rng);
		trySpawn(region.getEntityInitialSpawns(), c, rng);
		trySpawn(dim.getEntityInitialSpawns(), c, rng);
	}

	@Override
	public BlockVector computeSpawn(Function<BlockVector, Boolean> allowed)
	{
		RNG r = new RNG(489886222).nextParallelRNG(-293667771);
		int x = 0;
		int y = 0;
		int z = 0;

		for(int i = 0; i < 64; i++)
		{
			x = r.i(-64 - (i * 2), 64 + (i * 2));
			z = r.i(-64 - (i * 2), 64 + (i * 2));
			y = (int) Math.round(getTerrainHeight(x, z));
			BlockVector b = new BlockVector(x, y, z);

			if(y <= getFluidHeight() || !allowed.apply(b))
			{
				continue;
			}

			return b;
		}

		return new BlockVector(x, y, z);
	}

	@Override
	protected void onSpawn(EntitySpawnEvent e)
	{
		if(isSpawnable())
		{
			if(!IrisSettings.get().isSystemEntitySpawnOverrides())
			{
				return;
			}

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
				if(trySpawn(res.getTile().getEntitySpawnOverrides(), e))
				{
					return;
				}
			}

			if(res != null && res.getStructure() != null)
			{
				if(trySpawn(res.getStructure().getEntitySpawnOverrides(), e))
				{
					return;
				}
			}

			if(below != null)
			{
				if(trySpawn(below.getEntitySpawnOverrides(), e))
				{
					return;
				}
			}

			if(trySpawn(above.getEntitySpawnOverrides(), e))
			{
				return;
			}

			if(trySpawn(region.getEntitySpawnOverrides(), e))
			{
				return;
			}

			if(trySpawn(dim.getEntitySpawnOverrides(), e))
			{
				return;
			}
		}
	}

	private boolean trySpawn(KList<IrisEntitySpawnOverride> s, EntitySpawnEvent e)
	{
		for(IrisEntitySpawnOverride i : s)
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

	private void trySpawn(KList<IrisEntityInitialSpawn> s, Chunk c, RNG rng)
	{
		for(IrisEntityInitialSpawn i : s)
		{
			i.spawn(this, c, rng);
		}
	}

	@Override
	public boolean canSpawn(int x, int z)
	{
		return true;
	}

	@Override
	public boolean shouldGenerateCaves()
	{
		if(getDimension() == null)
		{
			return false;
		}

		return getDimension().isVanillaCaves();
	}

	@Override
	public boolean shouldGenerateVanillaStructures()
	{
		if(getDimension() == null)
		{
			return false;
		}

		return getDimension().isVanillaStructures();
	}

	@Override
	public boolean shouldGenerateMobs()
	{
		return false;
	}

	@Override
	public boolean shouldGenerateDecorations()
	{
		return true;
	}
}
