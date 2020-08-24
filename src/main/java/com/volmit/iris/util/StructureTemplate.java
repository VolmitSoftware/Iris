package com.volmit.iris.util;

import java.io.File;
import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.util.BlockVector;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;
import com.volmit.iris.object.StructureTileCondition;
import com.volmit.iris.object.TileResult;

import lombok.Data;

@Data
public class StructureTemplate implements Listener, IObjectPlacer
{
	private int w;
	private int h;
	private boolean use3d;
	private IrisStructure structure;
	private RNG rng;
	private int size;
	private Location center;
	private ChronoLatch u = new ChronoLatch(50);
	private World world;
	private static final BlockData STONE = B.get("STONE");
	private static final BlockData RED = B.get("RED_STAINED_GLASS");
	private static final BlockData GREEN = B.get("LIME_STAINED_GLASS");
	private int task;
	private ChronoLatch dirtyLatch;
	private ChronoLatch gLatch;
	private Location focus;
	private Player worker;
	private KMap<Location, Runnable> updates = new KMap<>();
	private File folder;

	public StructureTemplate(String name, String dimension, Player worker, Location c, int size, int w, int h, boolean use3d)
	{
		this.worker = worker;
		rng = new RNG();
		folder = Iris.instance.getDataFolder("packs", dimension);
		gLatch = new ChronoLatch(250);
		focus = center;
		dirtyLatch = new ChronoLatch(2350);
		task = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::tick, 0, 0);
		this.world = c.getWorld();
		this.center = c.clone();
		this.size = size;
		this.use3d = use3d;
		this.w = w;
		this.h = h;
		Iris.instance.registerListener(this);
		structure = new IrisStructure();
		structure.setGridSize(w);
		structure.setGridHeight(h);
		structure.setMaxLayers(use3d ? size : 1);
		structure.setBore(true);
		structure.setLoadKey(name);
		structure.setName(Form.capitalizeWords(name.replaceAll("\\Q-\\E", " ")));
		structure.setWallChance(0.35);
		defineStructures();
		updateTiles(center, null, null);
		Iris.struct.open(this);
	}

	public void saveStructure()
	{
		try
		{
			File structureFile = new File(folder, "structures/" + structure.getLoadKey() + ".json");

			for(IrisStructureTile i : structure.getTiles())
			{
				File objectFile = new File(folder, "objects/structure/" + structure.getLoadKey() + "/" + i.getForceObject().getLoadKey() + ".iob");
				Iris.verbose("Saving " + objectFile.getPath());
				i.getForceObject().write(objectFile);
			}

			Iris.verbose("Saving " + structureFile.getPath());
			IO.writeAll(structureFile, new JSONObject(new Gson().toJson(structure)).toString(4));
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	public void setWallChance(double w)
	{
		structure.setWallChance(w);
		regenerate();
	}

	public void regenerate()
	{
		rng = new RNG();
		updateTiles(center, null, null);
	}

	public void queue(Location l, Runnable r)
	{
		if(updates.containsKey(l))
		{
			return;
		}

		updates.put(l, r);
	}

	public void tick()
	{
		try
		{
			Location m = worker.getTargetBlockExact(64).getLocation();

			if(isWithinBounds(m) && u.flip())
			{
				focus = m.clone();
				Cuboid b = getTileBounds(m);
				if(gLatch.flip())
				{
					highlightTile(b);
				}
			}
		}

		catch(Throwable ef)
		{

		}

		if(dirtyLatch.couldFlip())
		{
			int u = 3;
			while(updates.size() > 0 && u-- > 0)
			{
				runClosestTo();
			}
		}
	}

	private void runClosestTo()
	{
		if(focus == null)
		{
			focus = center;
		}

		Location g = null;
		double v = Double.MAX_VALUE;

		for(Location l : updates.keySet())
		{
			double d = l.distanceSquared(focus);
			if(d < v)
			{
				v = d;
				g = l;
			}
		}

		updates.remove(g).run();
	}

	private void mod(Location l)
	{
		if(!isWithinBounds(l))
		{
			return;
		}

		focus = l.clone();
		Cuboid cuboid = getTileBounds(l);
		Location center = cuboid.getCenter();
		TileResult r = structure.getTile(rng, center.getX(), center.getY(), center.getZ());

		if(r == null)
		{
			return;
		}

		IrisObject o = r.getTile().getForceObject();
		double yrot = r.getPlacement().getRotation().getYAxis().getMax();
		double trot = -yrot;
		r.getPlacement().getRotation().getYAxis().setMin(trot);
		r.getPlacement().getRotation().getYAxis().setMax(trot);

		Location min = cuboid.getLowerNE();
		Iterator<Block> bit = cuboid.iterator();

		while(bit.hasNext())
		{
			Block b = bit.next();
			Location loc = new Location(world, b.getX(), b.getY(), b.getZ());
			BlockVector v = loc.clone().subtract(min).subtract(o.getCenter()).toVector().toBlockVector();
			v = r.getPlacement().getRotation().rotate(v, 0, 0, 0);
			BlockData next = r.getPlacement().getRotation().rotate(b.getBlockData(), 0, 0, 0);

			o.getBlocks().put(v, next);
		}

		r.getPlacement().getRotation().getYAxis().setMin(yrot);
		r.getPlacement().getRotation().getYAxis().setMax(yrot);
		dirtyLatch.flipDown();
		updateTiles(l, r.getTile(), getTileBounds(l));
	}

	public void highlightTile(Cuboid b)
	{
		Iris.wand.draw(b, worker);
		Location center = b.getCenter();
		TileResult r = structure.getTile(rng, center.getX(), center.getY(), center.getZ());
		worker.sendTitle("", r.getTile().getForceObject().getLoadKey() + " " + r.getPlacement().getRotation().getYAxis().getMax() + "°", 0, 20, 40);
	}

	public void updateTiles(Location from, IrisStructureTile tileType, Cuboid ignore)
	{
		Cuboid bounds = getBounds();

		for(int i = bounds.getLowerX(); i < bounds.getUpperX(); i += w)
		{
			for(int j = bounds.getLowerZ(); j < bounds.getUpperZ(); j += w)
			{
				for(int hh = bounds.getLowerY(); hh < bounds.getUpperY(); hh += h)
				{
					Location l = new Location(world, i, hh, j);

					if(ignore != null && ignore.contains(l))
					{
						continue;
					}

					if(tileType != null)
					{
						Location center = getTileBounds(l).getCenter();
						TileResult r = structure.getTile(rng, center.getX(), center.getY(), center.getZ());

						if(r == null || !r.getTile().getForceObject().getLoadKey().equals(tileType.getForceObject().getLoadKey()))
						{
							continue;
						}
					}

					if(isWithinBounds(l))
					{
						queue(l, () -> updateTile(getTileBounds(l)));
					}
				}
			}
		}
	}

	public void deleteTile(Cuboid from)
	{
		Location center = from.getCenter();
		from.iterator().forEachRemaining((b) -> b.setType(Material.AIR, false));
		center.getWorld().playSound(center, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1f, 0.1f);
		center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center.getX(), center.getY(), center.getZ(), 1);
	}

	public void updateTile(Cuboid c)
	{
		Location center = c.getCenter();
		Location bottomCenter = c.getCenter();
		bottomCenter.setY(c.getLowerY());
		TileResult r = structure.getTile(rng, center.getX(), center.getY(), center.getZ());

		if(r == null)
		{
			return;
		}

		r.getTile().getForceObject().place(bottomCenter.getBlockX(), bottomCenter.getBlockY(), bottomCenter.getBlockZ(), this, r.getPlacement(), rng);
		center.getWorld().playSound(center, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1f, 0.35f);
		center.getWorld().spawnParticle(Particle.FLASH, center.getX(), center.getY(), center.getZ(), 1);
	}

	public boolean isWithinBounds(Location l)
	{
		return getBounds().contains(l);
	}

	public void close()
	{
		worker.sendMessage(Iris.instance.getTag() + "Saving Structure: " + getStructure().getName());
		Iris.instance.unregisterListener(this);
		Bukkit.getScheduler().cancelTask(task);
		saveStructure();
		Iris.struct.remove(this);
	}

	public TileResult getTile(int x, int y, int z)
	{
		return structure.getTile(rng, x, y, z);
	}

	public Cuboid getBounds()
	{
		return getBounds(center);
	}

	public Cuboid getBounds(Location center)
	{
		//@builder
		return new Cuboid(
			getTileBounds(center.clone().add(
					((size / 2) * w) + 1, 
					!use3d ? 0 : (((size / 2) * h) + 1),
					((size / 2) * w) + 1)
					).getUpperSW(), 
			getTileBounds(center.clone().subtract(
					((size / 2) * w) + 1, 
					!use3d ? 0 : (((size / 2) * h) + 1), 
					((size / 2) * w) + 1)
					).getLowerNE());
		//@done
	}

	public Cuboid getTileBounds(Location l)
	{
		//@builder
		return new Cuboid(
			new Location(l.getWorld(),
				Math.floorDiv(l.getBlockX(), w) * w, 
				Math.floorDiv(l.getBlockY(), h) * h, 
				Math.floorDiv(l.getBlockZ(), w) * w), 
			new Location(l.getWorld(), 
				((Math.floorDiv(l.getBlockX(), w)+1) * w)-1, 
				((Math.floorDiv(l.getBlockY(), h)+1) * h)-1, 
				((Math.floorDiv(l.getBlockZ(), w)+1) * w)-1));
		//@done
	}

	public IrisStructureTile tileFor(String name, StructureTileCondition f, StructureTileCondition c, StructureTileCondition n, StructureTileCondition e, StructureTileCondition w, StructureTileCondition s)
	{
		IrisObject o = new IrisObject(this.w, this.h, this.w);
		o.setLoadKey(name.toLowerCase().replaceAll("\\Q \\E", "-"));
		IrisStructureTile t = new IrisStructureTile();
		t.setForceObject(o);
		t.setObjects(new KList<>("structure/" + this.structure.getLoadKey() + "/" + o.getLoadKey()));
		t.setFloor(f);
		t.setCeiling(c);
		t.setNorth(n);
		t.setEast(e);
		t.setSouth(s);
		t.setWest(w);

		int minX = 0;
		int maxX = this.w - 1;
		int minZ = 0;
		int maxZ = this.w - 1;
		int minY = 0;
		int maxY = this.h - 1;

		if(use3d)
		{
			if(f.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					for(int j = minZ; j <= maxZ; j++)
					{
						o.setUnsigned(i, minY, j, STONE);
					}
				}
			}

			if(c.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					for(int j = minZ; j <= maxZ; j++)
					{
						o.setUnsigned(i, maxY, j, STONE);
					}
				}
			}

			if(n.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					for(int j = minY; j <= maxY; j++)
					{
						o.setUnsigned(i, j, minZ, STONE);
					}
				}
			}

			if(s.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					for(int j = minY; j <= maxY; j++)
					{
						o.setUnsigned(i, j, maxZ, STONE);
					}
				}
			}

			if(w.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minZ; i <= maxZ; i++)
				{
					for(int j = minY; j <= maxY; j++)
					{
						o.setUnsigned(minX, j, i, STONE);
					}
				}
			}

			if(e.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minZ; i <= maxZ; i++)
				{
					for(int j = minY; j <= maxY; j++)
					{
						o.setUnsigned(maxX, j, i, STONE);
					}
				}
			}
		}

		else
		{
			if(f.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					for(int j = minZ; j <= maxZ; j++)
					{
						o.setUnsigned(i, minY, j, GREEN);
					}
				}
			}

			if(c.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					for(int j = minZ; j <= maxZ; j++)
					{
						o.setUnsigned(i, maxY, j, GREEN);
					}
				}
			}

			if(n.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					o.setUnsigned(i, minY, minZ, RED);
				}
			}

			if(s.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minX; i <= maxX; i++)
				{
					o.setUnsigned(i, minY, maxZ, RED);
				}
			}

			if(w.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minZ; i <= maxZ; i++)
				{
					o.setUnsigned(minX, minY, i, RED);
				}
			}

			if(e.equals(StructureTileCondition.REQUIRED))
			{
				for(int i = minZ; i <= maxZ; i++)
				{
					o.setUnsigned(maxX, minY, i, RED);
				}
			}
		}

		return t;
	}

	@Override
	public int getHighest(int x, int z)
	{
		return 0;
	}

	@Override
	public int getHighest(int x, int z, boolean ignoreFluid)
	{
		return 0;
	}

	@Override
	public void set(int x, int y, int z, BlockData d)
	{
		if(get(x, y, z).equals(d))
		{
			return;
		}

		world.getBlockAt(x, y, z).setBlockData(d, false);
	}

	@Override
	public BlockData get(int x, int y, int z)
	{
		return world.getBlockAt(x, y, z).getBlockData();
	}

	@Override
	public boolean isPreventingDecay()
	{
		return true;
	}

	@Override
	public boolean isSolid(int x, int y, int z)
	{
		return get(x, y, z).getMaterial().isSolid();
	}

	@Override
	public boolean isUnderwater(int x, int z)
	{
		return false;
	}

	@Override
	public int getFluidHeight()
	{
		return 0;
	}

	private void defineStructures()
	{
		if(use3d)
		{
			//@builder
			structure.getTiles().add(tileFor("Cross Floor",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("T-Connect Floor",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Hall Floor",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED
					));
			structure.getTiles().add(tileFor("Corner Floor",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Room Floor",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Walled Room Floor",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED
					));
			
			structure.getTiles().add(tileFor("Cross Ceiling",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("T-Connect Ceiling",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Hall Ceiling",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED
					));
			structure.getTiles().add(tileFor("Corner Ceiling",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Room Ceiling",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Walled Room Ceiling",
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED
					));
			
			structure.getTiles().add(tileFor("Cross Opening",
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("T-Connect Opening",
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Hall Opening",
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED
					));
			structure.getTiles().add(tileFor("Corner Opening",
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Room Opening",
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Walled Room Opening",
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED
					));
			
			structure.getTiles().add(tileFor("Cross Encased",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("T-Connect Encased",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Hall Encased",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED
					));
			structure.getTiles().add(tileFor("Corner Encased",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Room Encased",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Walled Room Encased",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED
					));
			//@done
		}

		else
		{
			//@builder
			structure.getTiles().add(tileFor("Cross",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.AGNOSTIC,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("T-Connect",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.AGNOSTIC,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Hall",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.AGNOSTIC,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER,
					StructureTileCondition.REQUIRED
					));
			structure.getTiles().add(tileFor("Corner",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.AGNOSTIC,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Room",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.AGNOSTIC,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.NEVER
					));
			structure.getTiles().add(tileFor("Walled Room",
					StructureTileCondition.REQUIRED,
					StructureTileCondition.AGNOSTIC,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED,
					StructureTileCondition.REQUIRED
					));
			//@done
		}
	}

	@EventHandler
	public void on(BlockBreakEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockIgniteEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockFormEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockFromToEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockFadeEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockPhysicsEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockFertilizeEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockGrowEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockSpreadEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockBurnEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockCookEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	@EventHandler
	public void on(BlockPlaceEvent e)
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> mod(e.getBlock().getLocation()), 5);
	}

	public void more()
	{
		Location center = worker.getLocation().clone();

		if(!use3d)
		{
			center.setY(this.center.getY());
		}

		Cuboid bounds = getBounds();
		Cuboid newBounds = getBounds(center);
		Cuboid total = bounds.getBoundingCuboid(newBounds);

		for(int i = bounds.getLowerX(); i < bounds.getUpperX(); i += w)
		{
			for(int j = bounds.getLowerZ(); j < bounds.getUpperZ(); j += w)
			{
				for(int hh = bounds.getLowerY(); hh < bounds.getUpperY(); hh += h)
				{
					Location l = new Location(world, i, hh, j);
					if(!total.contains(l))
					{
						continue;
					}
					boolean o = bounds.contains(l);
					boolean n = newBounds.contains(l);

					if(o && !n)
					{
						deleteTile(getTileBounds(l));
					}
				}
			}
		}

		this.center = center;
		updateTiles(focus, null, null);
	}

	public void expand()
	{
		Location center = worker.getLocation().clone();

		if(!use3d)
		{
			center.setY(this.center.getY());
		}

		Cuboid bounds = getBounds();
		Cuboid newBounds = getBounds(center);
		Cuboid total = bounds.getBoundingCuboid(newBounds);

		for(int i = bounds.getLowerX(); i < bounds.getUpperX(); i += w)
		{
			for(int j = bounds.getLowerZ(); j < bounds.getUpperZ(); j += w)
			{
				for(int hh = bounds.getLowerY(); hh < bounds.getUpperY(); hh += h)
				{
					Location l = new Location(world, i, hh, j);
					if(!total.contains(l))
					{
						continue;
					}
					boolean o = bounds.contains(l);
					boolean n = newBounds.contains(l);

					if(o && !n)
					{
						deleteTile(getTileBounds(l));
					}
				}
			}
		}

		size += 2;
		this.center = center;
		updateTiles(focus, null, null);
	}
}
