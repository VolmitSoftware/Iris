package ninja.bytecode.iris.generator.genobject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Directional;
import org.bukkit.material.Ladder;
import org.bukkit.material.Leaves;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Stairs;
import org.bukkit.material.Vine;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import mortar.compute.math.M;
import mortar.logic.format.F;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.placer.NMSPlacer;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.IPlacer;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.SBlockVector;
import ninja.bytecode.iris.util.SChunkVectorShort;
import ninja.bytecode.iris.util.VectorMath;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.RNG;

public class GenObject
{
	private boolean centeredHeight;
	private int w;
	private int h;
	private int d;
	private int failures;
	private int successes;
	private boolean gravity;
	private String name = "?";
	private KMap<SBlockVector, MB> s;
	private KMap<SChunkVectorShort, SBlockVector> slopeCache;
	private KMap<SChunkVectorShort, SBlockVector> gravityCache;
	private BlockVector mount;
	private int maxslope;
	private int baseslope;
	private boolean hydrophilic;
	private boolean submerged;
	private int mountHeight;
	private BlockVector shift;

	public GenObject(int w, int h, int d)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		shift = new BlockVector();
		s = new KMap<>();
		centeredHeight = false;
		gravity = false;
		maxslope = -1;
		baseslope = 0;
		hydrophilic = false;
		submerged = false;
	}

	public void recalculateMountShift()
	{
		int ly = Integer.MAX_VALUE;

		for(SBlockVector i : s.keySet())
		{
			if(i.getY() < ly)
			{
				ly = (int) i.getY();
			}
		}

		KList<SBlockVector> fmount = new KList<>();

		for(SBlockVector i : s.keySet())
		{
			if(i.getY() == ly)
			{
				fmount.add(i);
			}
		}

		double avx[] = new double[fmount.size()];
		double avy[] = new double[fmount.size()];
		double avz[] = new double[fmount.size()];
		int c = 0;

		for(SBlockVector i : fmount)
		{
			avx[c] = i.getX();
			avy[c] = i.getY();
			avz[c] = i.getZ();
			c++;
		}

		mountHeight = avg(avy);
		mount = new BlockVector(0, 0, 0);
	}

	private KMap<SChunkVectorShort, SBlockVector> getSlopeCache()
	{
		if(slopeCache == null)
		{
			computeSlopeCache();
		}

		return slopeCache;
	}

	private KMap<SChunkVectorShort, SBlockVector> getGravityCache()
	{
		if(gravityCache == null)
		{
			computeGravityCache();
		}

		return gravityCache;
	}

	private void computeGravityCache()
	{
		gravityCache = new KMap<>();

		for(SBlockVector i : s.keySet())
		{
			SChunkVectorShort v = new SChunkVectorShort(i.getX(), i.getZ());

			if(!gravityCache.containsKey(v) || gravityCache.get(v).getY() > i.getY())
			{
				gravityCache.put(v, i);
			}
		}
	}

	private void computeSlopeCache()
	{
		slopeCache = new KMap<>();
		int low = Integer.MAX_VALUE;

		for(SBlockVector i : s.keySet())
		{
			SChunkVectorShort v = new SChunkVectorShort(i.getX(), i.getZ());

			if(!slopeCache.containsKey(v) || slopeCache.get(v).getY() > i.getY())
			{
				slopeCache.put(v, i);
			}
		}

		for(SChunkVectorShort i : slopeCache.keySet())
		{
			int f = (int) slopeCache.get(i).getY();

			if(f < low)
			{
				low = f;
			}
		}

		for(SChunkVectorShort i : slopeCache.k())
		{
			int f = (int) slopeCache.get(i).getY();

			if(f > low - baseslope)
			{
				slopeCache.remove(i);
			}
		}
	}

	private int avg(double[] v)
	{
		double g = 0;

		for(int i = 0; i < v.length; i++)
		{
			g += v[i];
		}

		return (int) Math.round(g / (double) v.length);
	}

	public void setCenteredHeight()
	{
		this.centeredHeight = true;
	}

	public int getW()
	{
		return w;
	}

	public int getH()
	{
		return h;
	}

	public int getD()
	{
		return d;
	}

	public KMap<SBlockVector, MB> getSchematic()
	{
		return s;
	}

	public int getWidth()
	{
		return w;
	}

	public int getDepth()
	{
		return d;
	}

	public void read(InputStream in, boolean gzip) throws IOException
	{
		@SuppressWarnings("resource")
		GZIPInputStream gzi = gzip ? new GZIPInputStream(in) : null;
		DataInputStream din = new DataInputStream(gzip ? gzi : in);
		readDirect(din);
		din.close();
	}

	@SuppressWarnings("deprecation")
	public void readDirect(DataInputStream din) throws IOException
	{
		w = din.readInt();
		h = din.readInt();
		d = din.readInt();
		int l = din.readInt();
		clear();

		for(int i = 0; i < l; i++)
		{
			s.put(new SBlockVector(din.readInt(), din.readInt(), din.readInt()), new MB(Material.getMaterial((int) din.readInt()), din.readInt()));
		}
	}

	@SuppressWarnings("deprecation")
	public void writeDirect(DataOutputStream dos) throws IOException
	{
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(s.size());

		for(SBlockVector i : s.keySet())
		{
			dos.writeInt((int) i.getX());
			dos.writeInt((int) i.getY());
			dos.writeInt((int) i.getZ());
			dos.writeInt(s.get(i).material.getId());
			dos.writeInt(s.get(i).data);
		}
	}

	public void write(OutputStream out, boolean gzip) throws IOException
	{
		CustomOutputStream cos = gzip ? new CustomOutputStream(out, 9) : null;
		DataOutputStream dos = new DataOutputStream(gzip ? cos : out);
		writeDirect(dos);
		dos.close();
	}

	public MB get(int x, int y, int z)
	{
		return s.get(new SBlockVector(x, y, z));
	}

	public boolean has(int x, int y, int z)
	{
		return s.containsKey(new SBlockVector(x, y, z));
	}

	public void put(int x, int y, int z, MB mb)
	{
		s.put(new SBlockVector(x, y, z), mb);
	}

	public GenObject copy()
	{
		GenObject s = new GenObject(w, h, d);
		s.fill(this.s);
		s.centeredHeight = centeredHeight;
		s.name = name;
		return s;
	}

	public void clear()
	{
		s.clear();
	}

	public void fill(KMap<SBlockVector, MB> b)
	{
		clear();
		s.putAll(b);
	}

	public int sh(int g)
	{
		int m = (g / 2);
		return g % 2 == 0 ? m : m + 1;
	}

	public Location place(Location l)
	{
		NMSPlacer p;
		Location ll = place(l, p = new NMSPlacer(l.getWorld()));
		p.flush();

		return ll;
	}

	public Location place(Location l, IPlacer placer)
	{
		return place(l.getBlockX(), l.getBlockY(), l.getBlockZ(), placer);
	}

	@SuppressWarnings("deprecation")
	public Location place(int wx, int wy, int wz, IPlacer placer)
	{
		Location start = new Location(placer.getWorld(), wx, wy, wz).clone().add(0, sh(h) + 1, 0);

		if(mount == null)
		{
			recalculateMountShift();
		}

		start.subtract(mount);

		int highestY = submerged ? placer.getHighestYUnderwater(start) : placer.getHighestY(start);

		if(start.getBlockY() + mountHeight > highestY)
		{
			start.subtract(0, start.getBlockY() + mountHeight - highestY, 0);
		}

		start.add(shift);
		KMap<Location, MB> undo = new KMap<>();

		for(SBlockVector i : s.keySet())
		{
			MB b = getSchematic().get(i);

			if(b.material.equals(Material.LEAVES) || b.material.equals(Material.LEAVES_2))
			{
				Leaves l = new Leaves(b.material, b.data);
				l.setDecayable(false);
				l.setDecaying(false);
				b = new MB(l.getItemType(), l.getData());
			}

			Location f = start.clone().add(i.toBlockVector());

			if(gravity)
			{
				SChunkVectorShort v = new SChunkVectorShort(i.getX(), i.getZ());
				int offset = (int) i.getY() - (int) getGravityCache().get(v).getY();
				f.setY(f.getBlockY() - ((f.getBlockY() - offset) - (submerged ? placer.getHighestYUnderwater(f) : placer.getHighestY(f))));
			}

			else if(maxslope >= 0)
			{
				SChunkVectorShort v = new SChunkVectorShort(i.getX(), i.getZ());
				SBlockVector m = getSlopeCache().get(v);

				if(m == null)
				{
					continue;
				}

				int offset = (int) i.getY() - (int) m.getY();
				int shift = ((f.getBlockY() - offset) - (submerged ? placer.getHighestYUnderwater(f) : placer.getHighestY(f)));

				if(Math.abs(shift) > maxslope)
				{
					for(Location j : undo.k())
					{
						placer.set(j, undo.get(j));
					}

					if(Iris.settings.performance.verbose)
					{
						L.w(C.WHITE + "Object " + C.YELLOW + getName() + C.WHITE + " failed to place on slope " + C.YELLOW + Math.abs(shift) + C.WHITE + " at " + C.YELLOW + F.f(f.getBlockX()) + " " + F.f(f.getBlockY()) + " " + F.f(f.getBlockZ()));
					}

					failures++;
					return null;
				}
			}

			if(!hydrophilic && !Iris.settings.performance.noObjectFail)
			{
				if(f.getBlockY() == 63 && i.getY() == mountHeight)
				{
					Material m = placer.get(f.clone().subtract(0, 1, 0)).material;

					if(m.equals(Material.WATER) || m.equals(Material.STATIONARY_WATER) || m.equals(Material.LAVA) || m.equals(Material.STATIONARY_LAVA))
					{
						for(Location j : undo.k())
						{
							placer.set(j, undo.get(j));
						}

						if(Iris.settings.performance.verbose)
						{
							L.w(C.WHITE + "Object " + C.YELLOW + getName() + C.WHITE + " (hydrophobic) failed to place in " + C.YELLOW + m.toString().toLowerCase() + C.WHITE + " at " + C.YELLOW + F.f(f.getBlockX()) + " " + F.f(f.getBlockY()) + " " + F.f(f.getBlockZ()));
						}

						failures++;
						return null;
					}
				}
			}

			try
			{
				undo.put(f, placer.get(f));
				placer.set(f, b);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		successes++;
		return start;
	}

	public static GenObject load(InputStream in) throws IOException
	{
		GenObject s = new GenObject(1, 1, 1);
		s.read(in, true);

		return s;
	}

	public static GenObject load(File f) throws IOException
	{
		GenObject s = new GenObject(1, 1, 1);
		s.name = f.getName().replaceAll("\\Q.ish\\E", "");
		FileInputStream fin = new FileInputStream(f);
		s.read(fin, true);

		return s;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void rotate(Direction from, Direction to)
	{
		KMap<SBlockVector, MB> g = new KMap<>();
		g.putAll(s);
		s.clear();

		for(SBlockVector i : g.keySet())
		{
			MB mb = rotate(from, to, g.get(i));
			s.put(new SBlockVector(VectorMath.rotate(from, to, i.toBlockVector()).toBlockVector()), mb);
		}

		name = name + "-rt" + to.name();
	}

	@SuppressWarnings("deprecation")
	private MB rotate(Direction from, Direction to, MB mb)
	{
		try
		{
			Material t = mb.material;
			int i = t.getId();
			byte d = mb.data;
			MaterialData data = t.getData().getConstructor(int.class, byte.class).newInstance(i, d);

			if(data instanceof Directional)
			{
				Directional dir = (Directional) data;
				Supplier<BlockFace> get = dir::getFacing;
				Consumer<BlockFace> set = dir::setFacingDirection;

				if(dir instanceof Ladder)
				{
					get = ((Ladder) dir)::getAttachedFace;
					set = ((Ladder) dir)::setFacingDirection;
				}

				if(dir instanceof Stairs)
				{
					get = ((Stairs) dir)::getAscendingDirection;
					set = ((Stairs) dir)::setFacingDirection;
				}

				BlockFace fac = get.get();
				set.accept(rotate(from, to, fac));
				d = data.getData();
				t = data.getItemType();
				return MB.of(t, d);
			}

			else if(data instanceof Vine)
			{
				Vine vin = (Vine) data;
				Vine vif = new Vine();

				for(Direction j : Direction.news())
				{
					if(vin.isOnFace(j.getFace()))
					{
						vif.putOnFace(rotate(from, to, j.getFace()));
					}
				}

				d = vif.getData();
				t = vif.getItemType();
				return MB.of(t, d);
			}

			else if(i >= 235 && i <= 250)
			{
				BlockFace fac = getGlazedTCDir(d);
				d = toGlazedTCDir(Direction.getDirection(rotate(from, to, fac)).getFace());
				t = data.getItemType();
				return MB.of(t, d);
			}
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return mb;
	}

	private byte toGlazedTCDir(BlockFace b)
	{
		switch(b)
		{
			case NORTH:
				return 0;
			case EAST:
				return 1;
			case SOUTH:
				return 2;
			case WEST:
				return 3;
			default:
				break;
		}

		return 0;
	}

	private BlockFace getGlazedTCDir(byte d2)
	{
		switch(d2)
		{
			case 0:
				return BlockFace.NORTH;
			case 1:
				return BlockFace.EAST;
			case 2:
				return BlockFace.SOUTH;
			case 3:
				return BlockFace.WEST;
		}

		return BlockFace.NORTH;
	}

	private BlockFace rotate(Direction from, Direction to, BlockFace face)
	{
		return Direction.getDirection(from.angle(Direction.getDirection(face).toVector(), to)).getFace();
	}

	public void computeFlag(String j)
	{
		try
		{
			if(j.equals("gravity"))
			{
				gravity = true;
			}

			if(j.equals("hydrophilic"))
			{
				hydrophilic = true;
			}

			if(j.equals("submerged"))
			{
				submerged = true;
				hydrophilic = true;
			}

			if(j.startsWith("maxslope "))
			{
				maxslope = Integer.valueOf(j.split("\\Q \\E")[1]);
			}

			if(j.startsWith("baseslope "))
			{
				baseslope = Integer.valueOf(j.split("\\Q \\E")[1]);
			}

			if(j.startsWith("replace "))
			{
				String[] g = j.split("\\Q \\E");
				MB a = MB.of(g[1]);
				boolean specific = g[1].contains(":");
				MB b = MB.of(g[2]);

				for(SBlockVector i : s.k())
				{
					MB c = s.get(i);

					if((specific && c.equals(a)) || c.material.equals(a.material))
					{
						s.put(i, b);
					}
				}
			}

			if(j.startsWith("sink "))
			{
				int downshift = Integer.valueOf(j.split("\\Q \\E")[1]);
				shift.subtract(new Vector(0, downshift, 0));
			}

			if(j.startsWith("raise "))
			{
				int downshift = Integer.valueOf(j.split("\\Q \\E")[1]);
				shift.add(new Vector(0, downshift, 0));
			}
		}

		catch(Throwable e)
		{
			L.f("Failed to compute flag '" + j + "'");
			L.ex(e);
		}
	}

	public void applySnowFilter(int factor)
	{
		int minX = 0;
		int maxX = 0;
		int minY = 0;
		int maxY = 0;
		int minZ = 0;
		int maxZ = 0;
		boolean added = false;

		for(SBlockVector i : getSchematic().keySet())
		{
			if(i.getX() > maxX)
			{
				maxX = (int) i.getX();
			}

			if(i.getY() > maxY)
			{
				maxY = (int) i.getY();
			}

			if(i.getZ() > maxZ)
			{
				maxZ = (int) i.getZ();
			}

			if(i.getX() < minX)
			{
				minX = (int) i.getX();
			}

			if(i.getY() < minY)
			{
				minY = (int) i.getY();
			}

			if(i.getZ() < minZ)
			{
				minZ = (int) i.getZ();
			}
		}

		for(int i = minX; i <= maxX; i++)
		{
			for(int j = minZ; j <= maxZ; j++)
			{
				SBlockVector highest = null;

				for(SBlockVector k : getSchematic().keySet())
				{
					if(k.getX() == i && k.getZ() == j)
					{
						if(highest == null)
						{
							highest = k;
						}

						else if(highest.getY() < k.getY())
						{
							highest = k;
						}
					}
				}

				if(highest != null)
				{
					BlockVector mbv = highest.toBlockVector().add(new Vector(0, 1, 0)).toBlockVector();
					added = true;
					getSchematic().put(new SBlockVector(mbv), MB.of(Material.SNOW, RNG.r.nextInt((int) M.clip(factor, 1, 8))));
				}
			}
		}

		if(added)
		{
			h++;
			recalculateMountShift();
		}
	}

	@SuppressWarnings("deprecation")
	public void applyLushFilter(double factor)
	{
		int minX = 0;
		int maxX = 0;
		int minY = 0;
		int maxY = 0;
		int minZ = 0;
		int maxZ = 0;
		boolean added = false;

		for(SBlockVector i : getSchematic().keySet())
		{
			if(i.getX() > maxX)
			{
				maxX = (int) i.getX();
			}

			if(i.getY() > maxY)
			{
				maxY = (int) i.getY();
			}

			if(i.getZ() > maxZ)
			{
				maxZ = (int) i.getZ();
			}

			if(i.getX() < minX)
			{
				minX = (int) i.getX();
			}

			if(i.getY() < minY)
			{
				minY = (int) i.getY();
			}

			if(i.getZ() < minZ)
			{
				minZ = (int) i.getZ();
			}
		}

		for(int i = minX; i <= maxX; i++)
		{
			for(int j = minY; j <= maxY; j++)
			{
				for(int k = minZ; k <= maxZ; k++)
				{
					SBlockVector at = new SBlockVector(i, j, k);

					if(M.r(factor / 25D) && getSchematic().containsKey(at) && !getSchematic().get(at).material.equals(Material.VINE))
					{
						SBlockVector a = new SBlockVector(i + 1, j, k);
						SBlockVector b = new SBlockVector(i - 1, j, k);
						SBlockVector c = new SBlockVector(i, j, k + 1);
						SBlockVector d = new SBlockVector(i, j, k - 1);
						Vine v = null;
						KMap<SBlockVector, MB> e = new KMap<>();

						if(!getSchematic().containsKey(a))
						{
							v = new Vine(BlockFace.WEST);
							SBlockVector ma = new SBlockVector(a.getX(), a.getY(), a.getZ() + 1);
							SBlockVector mb = new SBlockVector(a.getX(), a.getY(), a.getZ() - 1);

							if(getSchematic().containsKey(ma) && !getSchematic().get(ma).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.SOUTH, BlockFace.WEST);
							}

							else if(getSchematic().containsKey(mb) && !getSchematic().get(mb).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.NORTH, BlockFace.WEST);
							}

							e.put(a, MB.of(Material.VINE, v.getData()));
						}

						if(!getSchematic().containsKey(b))
						{
							v = new Vine(BlockFace.EAST);
							SBlockVector ma = new SBlockVector(b.getX(), b.getY(), b.getZ() + 1);
							SBlockVector mb = new SBlockVector(b.getX(), b.getY(), b.getZ() - 1);

							if(getSchematic().containsKey(ma) && !getSchematic().get(ma).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.SOUTH, BlockFace.EAST);
							}

							else if(getSchematic().containsKey(mb) && !getSchematic().get(mb).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.NORTH, BlockFace.EAST);
							}

							e.put(b, MB.of(Material.VINE, v.getData()));
						}

						if(!getSchematic().containsKey(c))
						{
							v = new Vine(BlockFace.NORTH);
							SBlockVector ma = new SBlockVector(c.getX() + 1, c.getY(), c.getZ());
							SBlockVector mb = new SBlockVector(c.getX() - 1, c.getY(), c.getZ());

							if(getSchematic().containsKey(ma) && !getSchematic().get(ma).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.NORTH, BlockFace.EAST);
							}

							else if(getSchematic().containsKey(mb) && !getSchematic().get(mb).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.NORTH, BlockFace.WEST);
							}

							e.put(c, MB.of(Material.VINE, v.getData()));
						}

						if(!getSchematic().containsKey(d))
						{
							v = new Vine(BlockFace.SOUTH);
							SBlockVector ma = new SBlockVector(d.getX() + 1, d.getY(), d.getZ());
							SBlockVector mb = new SBlockVector(d.getX() - 1, d.getY(), d.getZ());

							if(getSchematic().containsKey(ma) && !getSchematic().get(ma).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.SOUTH, BlockFace.EAST);
							}

							else if(getSchematic().containsKey(mb) && !getSchematic().get(mb).material.equals(Material.VINE))
							{
								v = new Vine(BlockFace.SOUTH, BlockFace.WEST);
							}

							e.put(d, MB.of(Material.VINE, v.getData()));
						}

						if(!e.isEmpty())
						{
							added = true;
						}

						for(SBlockVector n : e.k())
						{
							for(int g = 0; g < (factor * 2) * RNG.r.nextDouble(); g++)
							{
								if(n.getY() - (g + 1) < minY)
								{
									break;
								}

								SBlockVector p = new SBlockVector(n.getX(), n.getY() - g, n.getZ());

								if(e.containsKey(p) || getSchematic().containsKey(p))
								{
									break;
								}

								e.put(p, e.get(n));
							}
						}

						getSchematic().putAll(e);
					}
				}
			}
		}

		if(added)
		{
			w++;
			d++;
			recalculateMountShift();
		}
	}

	public double getSuccess()
	{
		return (double) successes / ((double) successes + (double) failures);
	}

	public int getFailures()
	{
		return failures;
	}

	public int getSuccesses()
	{
		return successes;
	}

	public int getPlaces()
	{
		return successes + failures;
	}

	public void dispose()
	{
		s.clear();
	}

	public void setGravity(boolean gravity)
	{
		this.gravity = gravity;
	}

	public void setShift(int x, int y, int z)
	{
		shift = new BlockVector(x, y, z);
	}

	public boolean isCenteredHeight()
	{
		return centeredHeight;
	}

	public boolean isGravity()
	{
		return gravity;
	}

	public KMap<SBlockVector, MB> getS()
	{
		return s;
	}

	public BlockVector getMount()
	{
		return mount;
	}

	public int getMaxslope()
	{
		return maxslope;
	}

	public int getBaseslope()
	{
		return baseslope;
	}

	public boolean isHydrophilic()
	{
		return hydrophilic;
	}

	public boolean isSubmerged()
	{
		return submerged;
	}

	public int getMountHeight()
	{
		return mountHeight;
	}

	public BlockVector getShift()
	{
		return shift;
	}

	public void setCenteredHeight(boolean centeredHeight)
	{
		this.centeredHeight = centeredHeight;
	}

	public void setW(int w)
	{
		this.w = w;
	}

	public void setH(int h)
	{
		this.h = h;
	}

	public void setD(int d)
	{
		this.d = d;
	}

	public void setFailures(int failures)
	{
		this.failures = failures;
	}

	public void setSuccesses(int successes)
	{
		this.successes = successes;
	}

	public void setS(KMap<SBlockVector, MB> s)
	{
		this.s = s;
	}

	public void setSlopeCache(KMap<SChunkVectorShort, SBlockVector> slopeCache)
	{
		this.slopeCache = slopeCache;
	}

	public void setGravityCache(KMap<SChunkVectorShort, SBlockVector> gravityCache)
	{
		this.gravityCache = gravityCache;
	}

	public void setMount(BlockVector mount)
	{
		this.mount = mount;
	}

	public void setMaxslope(int maxslope)
	{
		this.maxslope = maxslope;
	}

	public void setBaseslope(int baseslope)
	{
		this.baseslope = baseslope;
	}

	public void setHydrophilic(boolean hydrophilic)
	{
		this.hydrophilic = hydrophilic;
	}

	public void setSubmerged(boolean submerged)
	{
		this.submerged = submerged;
	}

	public void setMountHeight(int mountHeight)
	{
		this.mountHeight = mountHeight;
	}

	public void setShift(BlockVector shift)
	{
		this.shift = shift;
	}
}
