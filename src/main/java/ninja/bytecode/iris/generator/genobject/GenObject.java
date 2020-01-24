package ninja.bytecode.iris.generator.genobject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import ninja.bytecode.iris.util.VectorMath;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.io.IO;
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
	private String name = "?";
	private KMap<SBlockVector, MB> s;
	private BlockVector mount;
	private int mountHeight;
	private BlockVector shift;

	@SuppressWarnings("deprecation")
	public void perfectRead(File folder, String name) throws IOException
	{
		File file = new File(folder, IO.hash(name) + ".ioc");
		FileInputStream fin = new FileInputStream(file);
		DataInputStream din = new DataInputStream(fin);
		centeredHeight = din.readBoolean();
		w = din.readShort();
		h = din.readShort();
		d = din.readShort();
		name = din.readUTF();
		int size = din.readInt();
		s.clear();
		for(int i = 0; i < size; i++)
		{
			s.put(new SBlockVector(din.readShort(), din.readShort(), din.readShort()), MB.of(Material.getMaterial(din.readInt()), din.readByte()));
		}

		mount = new BlockVector(din.readShort(), din.readShort(), din.readShort());
		mountHeight = din.readShort();
		shift = new BlockVector(din.readShort(), din.readShort(), din.readShort());
		din.close();
	}

	@SuppressWarnings("deprecation")
	public void perfectWrite(File folder) throws IOException
	{
		File file = new File(folder, IO.hash(name) + ".ioc");
		FileOutputStream fos = new FileOutputStream(file);
		DataOutputStream dos = new DataOutputStream(fos);
		dos.writeBoolean(centeredHeight);
		dos.writeShort(w);
		dos.writeShort(h);
		dos.writeShort(d);
		dos.writeUTF(name);
		dos.writeInt(s.size());

		for(SBlockVector i : s.keySet())
		{
			dos.writeShort((short) i.getX());
			dos.writeShort((short) i.getY());
			dos.writeShort((short) i.getZ());
			dos.writeInt(s.get(i).material.getId());
			dos.writeByte(s.get(i).data);
		}

		dos.writeShort(mount.getBlockX());
		dos.writeShort(mount.getBlockY());
		dos.writeShort(mount.getBlockZ());
		dos.writeShort(mountHeight);
		dos.writeShort(shift.getBlockX());
		dos.writeShort(shift.getBlockY());
		dos.writeShort(shift.getBlockZ());
		dos.close();
	}

	public GenObject(int w, int h, int d)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		shift = new BlockVector();
		s = new KMap<>();
		centeredHeight = false;
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

	public Location place(int wx, int wy, int wz, IPlacer placer)
	{
		Location start = new Location(placer.getWorld(), wx, wy, wz).clone().add(0, sh(h) + 1, 0);

		if(mount == null)
		{
			recalculateMountShift();
		}

		start.subtract(mount);

		int highestY = placer.getHighestY(start);

		if(start.getBlockY() + mountHeight > highestY)
		{
			start.subtract(0, start.getBlockY() + mountHeight - highestY, 0);
		}

		start.add(shift);
		KMap<Location, MB> undo = new KMap<>();

		for(SBlockVector i : s.keySet())
		{
			MB b = getSchematic().get(i);
			Location f = start.clone().add(i.toBlockVector());

			if(!Iris.settings.performance.noObjectFail)
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
							L.w(C.WHITE + "Object " + C.YELLOW + getName() + C.WHITE + " failed to place in " + C.YELLOW + m.toString().toLowerCase() + C.WHITE + " at " + C.YELLOW + F.f(f.getBlockX()) + " " + F.f(f.getBlockY()) + " " + F.f(f.getBlockZ()));
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
}
