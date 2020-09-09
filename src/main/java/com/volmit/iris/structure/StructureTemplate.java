package com.volmit.iris.structure;

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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BlockVector;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.object.StructureTileCondition;
import com.volmit.iris.object.TileResult;
import com.volmit.iris.util.B;
import com.volmit.iris.util.C;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.Cuboid;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.IObjectPlacer;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MaterialBlock;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.UIElement;
import com.volmit.iris.util.UIStaticDecorator;
import com.volmit.iris.util.UIWindow;
import com.volmit.iris.util.Window;
import com.volmit.iris.util.WindowResolution;

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
	private CNG variants;
	private boolean quiet = true;
	private KMap<Location, Integer> forceVariant = new KMap<>();

	public StructureTemplate(String name, String dimension, Player worker, Location c, int size, int w, int h, boolean use3d)
	{
		this.worker = worker;
		rng = new RNG();
		variants = NoiseStyle.STATIC.create(rng.nextParallelRNG(397878));
		folder = Iris.instance.getDataFolder("packs", dimension);
		gLatch = new ChronoLatch(250);
		focus = center;
		dirtyLatch = new ChronoLatch(250);
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
		regenerate();
		Iris.struct.open(this);
	}

	public void saveStructure()
	{
		try
		{
			File structureFile = new File(folder, "structures/" + structure.getLoadKey() + ".json");

			for(IrisStructureTile i : structure.getTiles())
			{
				for(IrisObject j : i.getForceObjects().v())
				{
					File objectFile = new File(folder, "objects/structure/" + structure.getLoadKey() + "/" + j.getLoadKey() + ".iob");
					Iris.verbose("Saving " + objectFile.getPath());
					j.write(objectFile);
				}
			}

			Iris.verbose("Saving " + structureFile.getPath());
			IO.writeAll(structureFile, new JSONObject(new Gson().toJson(structure)).toString(4));
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	@EventHandler
	public void on(PlayerMoveEvent e)
	{
		if(!e.getTo().getWorld().equals(getCenter().getWorld()))
		{
			e.getPlayer().sendMessage(Iris.instance.getTag() + " Saving open structure. Use /iris str load " + structure.getLoadKey() + " to re-open.");
			close();
			return;
		}

		if(e.getTo().distanceSquared(getCenter()) > Math.pow((size * w * 5), 2))
		{
			e.getPlayer().sendMessage(Iris.instance.getTag() + " Saving open structure. Use /iris str load " + structure.getLoadKey() + " to re-open.");
			close();
		}
	}

	public void loadStructures(IrisStructure input)
	{
		Iris.info("Loading existing structure");

		// TODO load input properties

		for(IrisStructureTile i : structure.getTiles().copy())
		{
			String realType = i.getForceObjects().get(1).getLoadKey().replaceAll("\\Q-1\\E", "");

			for(IrisStructureTile j : input.getTiles())
			{
				if(j.hashFace() == i.hashFace())
				{
					Iris.verbose("Found matching face configuration: " + j.hashFace());
					structure.getTiles().remove(i);
					IrisStructureTile hijacked = new IrisStructureTile();
					hijacked.setCeiling(j.getCeiling());
					hijacked.setFloor(j.getFloor());
					hijacked.setNorth(j.getNorth());
					hijacked.setSouth(j.getSouth());
					hijacked.setEast(j.getEast());
					hijacked.setWest(j.getWest());

					for(String k : j.getObjects())
					{
						int v = hijacked.getForceObjects().size() + 1;
						Iris.globaldata.dump();
						IrisObject o = Iris.globaldata.getObjectLoader().load(k).copy();
						String b = o.getLoadKey();
						o.setLoadKey(realType + "-" + v);

						if(b != null && !b.equals(o.getLoadKey()))
						{
							Iris.warn("Loading Object " + b + " as " + o.getLoadKey() + " (not deleting the old file)");
						}

						hijacked.getForceObjects().put(v, o);
						hijacked.getObjects().add("structure/" + this.structure.getLoadKey() + "/" + o.getLoadKey());

					}

					structure.getTiles().add(hijacked);
					break;
				}
			}
		}

		regenerate();
	}

	public void openVariants()
	{
		try
		{
			Location m = worker.getTargetBlockExact(64).getLocation();

			if(isWithinBounds(m))
			{
				focus = m.clone();
				Cuboid b = getTileBounds(m);
				Location center = b.getCenter();
				TileResult r = structure.getTile(rng, center.getX(), center.getY(), center.getZ());
				openVariants(r.getTile(), b);
				return;
			}
		}

		catch(Throwable ef)
		{
			ef.printStackTrace();
		}

		worker.sendMessage("Look at a tile to configure variants.");
	}

	public void openVariants(IrisStructureTile t, Cuboid at)
	{
		int var = getVariant(at, t);
		Window w = new UIWindow(worker);
		w.setTitle("Variants");
		w.setDecorator(new UIStaticDecorator(new UIElement("dec").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
		WindowResolution r = WindowResolution.W5_H1;
		w.setResolution(r);

		if(t.getForceObjects().size() > 4)
		{
			r = WindowResolution.W3_H3;
			w.setResolution(r);
		}

		if(t.getForceObjects().size() > 8)
		{
			r = WindowResolution.W9_H6;
			w.setResolution(r);
			w.setViewportHeight((int) Math.ceil((double) (t.getForceObjects().size() + 1) / 9D));
		}
		int m = 0;

		UIElement ea = new UIElement("add");
		ea.setEnchanted(true);
		ea.setMaterial(new MaterialBlock(Material.EMERALD));
		ea.setName("New Variant from Current Tile");

		ea.getLore().add("- Left Click to copy current variant into a new variant");
		ea.onLeftClick((ee) ->
		{
			w.close();
			createVariantCopy(t, at);
		});

		w.setElement(w.getLayoutPosition(m), w.getLayoutRow(m), ea);
		m++;

		for(Integer i : t.getForceObjects().k())
		{
			UIElement e = new UIElement("var-" + i);
			e.setEnchanted(var == i);
			e.setCount(i);
			e.setMaterial(new MaterialBlock(var == i ? Material.ENDER_EYE : Material.ENDER_PEARL));
			e.setName(t.getForceObjects().get(i).getLoadKey());

			if(var != i)
			{
				e.getLore().add("- Left Click to select this variant");
				e.onLeftClick((ee) ->
				{
					w.close();
					switchVariant(t, at, i);
				});
			}

			w.setElement(w.getLayoutPosition(m), w.getLayoutRow(m), e);
			m++;
		}

		w.open();
	}

	public void deleteVariant(IrisStructureTile t, Cuboid at)
	{

	}

	public void switchVariant(IrisStructureTile t, Cuboid at, int var)
	{
		forceVariant.put(at.getCenter(), var);
		updateTile(at);
	}

	public void createVariantCopy(IrisStructureTile t, Cuboid at)
	{
		int variant = getVariant(at, t);
		IrisObject origin = t.getForceObjects().get(variant);
		IrisObject object = new IrisObject(origin.getW(), origin.getH(), origin.getD());
		object.setCenter(origin.getCenter().clone());

		for(BlockVector i : origin.getBlocks().k())
		{
			object.getBlocks().put(i.clone(), origin.getBlocks().get(i).clone());
		}

		int nv = t.getForceObjects().size() + 1;
		object.setLoadKey(origin.getLoadKey().replaceAll("\\Q-" + variant + "\\E", "-" + nv));
		t.getObjects().add("structure/" + this.structure.getLoadKey() + "/" + object.getLoadKey());
		t.getForceObjects().put(nv, object);
		forceVariant.put(at.getCenter(), nv);
		regenerate();
	}

	public void setWallChance(double w)
	{
		structure.setWallChance(w);
		regenerate();
	}

	public void regenerate()
	{
		rng = new RNG();
		variants = NoiseStyle.STATIC.create(rng.nextParallelRNG(397878));
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
			updateAll();
		}
	}

	public void updateAll()
	{
		while(updates.size() > 0)
		{
			runClosestTo();
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
		mod(l, true);
	}

	private void mod(Location l, boolean u)
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

		IrisObject o = r.getTile().getForceObjects().get(getVariant(getTileBounds(l), r.getTile()));
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
		worker.sendTitle("", C.GRAY + r.getTile().getForceObjects().get(getVariant(b, r.getTile())).getLoadKey() + " " + C.DARK_GRAY + r.getPlacement().getRotation().getYAxis().getMax() + "°", 0, 20, 40);
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

						int v1 = getVariant(getTileBounds(l), r.getTile());
						int v2 = getVariant(getTileBounds(l), tileType);
						if(r == null || !r.getTile().getForceObjects().get(v1).getLoadKey().equals(

								tileType.getForceObjects().get(v2)

										.getLoadKey()))
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

	public void deleteTiles()
	{
		Cuboid bounds = getBounds();

		for(int i = bounds.getLowerX(); i < bounds.getUpperX(); i += w)
		{
			for(int j = bounds.getLowerZ(); j < bounds.getUpperZ(); j += w)
			{
				for(int hh = bounds.getLowerY(); hh < bounds.getUpperY(); hh += h)
				{
					Location l = new Location(world, i, hh, j);

					if(isWithinBounds(l))
					{
						Cuboid d = getTileBounds(l);
						J.s(() -> deleteTile(d), RNG.r.i(0, 100));
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

	public int getVariant(Cuboid c, IrisStructureTile t)
	{
		if(t.getForceObjects().size() == 1)
		{
			return t.getForceObjects().keys().nextElement();
		}

		if(forceVariant.containsKey(c.getCenter()))
		{
			return forceVariant.get(c.getCenter());
		}

		Location ce = c.getCenter();
		return variants.fit(t.getForceObjects().keypair(), ce.getBlockX(), ce.getBlockY(), ce.getBlockZ()).getK();
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

		r.getTile().getForceObjects().get(getVariant(c, r.getTile())).place(bottomCenter.getBlockX(), bottomCenter.getBlockY(), bottomCenter.getBlockZ(), this, r.getPlacement(), rng);
		if(!quiet)
		{
			center.getWorld().playSound(center, Sound.ENTITY_SHULKER_BULLET_HIT, 1f, 1.6f);
			center.getWorld().spawnParticle(Particle.FLASH, center.getX(), center.getY(), center.getZ(), 1);
		}
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
		deleteTiles();
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
		return tileFor(name, f, c, n, e, w, s, 1);
	}

	public IrisStructureTile tileFor(String name, StructureTileCondition f, StructureTileCondition c, StructureTileCondition n, StructureTileCondition e, StructureTileCondition w, StructureTileCondition s, int variant)
	{
		IrisObject o = new IrisObject(this.w, this.h, this.w);
		o.setLoadKey(name.toLowerCase().replaceAll("\\Q \\E", "-").trim() + "-" + variant);
		IrisStructureTile t = new IrisStructureTile();
		t.getForceObjects().put(variant, o);
		t.getObjects().add("structure/" + this.structure.getLoadKey() + "/" + o.getLoadKey());
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

		Iris.edit.set(world, x, y, z, d);
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
		quiet = false;
		mod(e.getBlock().getLocation(), false);
		updateAll();
		quiet = true;
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
		quiet = false;
		mod(e.getBlock().getLocation(), false);
		updateAll();
		quiet = true;
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

	@Override
	public boolean isDebugSmartBore()
	{
		return false;
	}
}
