package ninja.bytecode.iris.generator.genobject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import mortar.compute.math.M;
import ninja.bytecode.iris.generator.placer.NMSPlacer;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.IPlacer;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.VectorMath;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.RNG;

public class GenObject
{
	private boolean centeredHeight;
	private int w;
	private int h;
	private int d;
	private String name = "?";
	private final GMap<BlockVector, MB> s;
	private BlockVector mount;
	private int mountHeight;
	private BlockVector shift;

	public GenObject(int w, int h, int d)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		shift = new BlockVector();
		s = new GMap<>();
		centeredHeight = false;
	}

	public void recalculateMountShift()
	{
		int ly = Integer.MAX_VALUE;

		for(BlockVector i : s.k())
		{
			if(i.getBlockY() < ly)
			{
				ly = i.getBlockY();
			}
		}

		GList<BlockVector> fmount = new GList<>();

		for(BlockVector i : s.k())
		{
			if(i.getBlockY() == ly)
			{
				fmount.add(i);
			}
		}

		double avx[] = new double[fmount.size()];
		double avy[] = new double[fmount.size()];
		double avz[] = new double[fmount.size()];
		int c = 0;

		for(BlockVector i : fmount)
		{
			avx[c] = i.getBlockX();
			avy[c] = i.getBlockY();
			avz[c] = i.getBlockZ();
			c++;
		}

		mountHeight = avg(avy);
		mount = new BlockVector(avg(avx), 0, avg(avz));
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

	public GMap<BlockVector, MB> getSchematic()
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
			s.put(new BlockVector(din.readInt(), din.readInt(), din.readInt()), new MB(Material.getMaterial((int) din.readInt()), din.readInt()));
		}
	}

	@SuppressWarnings("deprecation")
	public void writeDirect(DataOutputStream dos) throws IOException
	{
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(s.size());

		for(BlockVector i : s.keySet())
		{
			dos.writeInt(i.getBlockX());
			dos.writeInt(i.getBlockY());
			dos.writeInt(i.getBlockZ());
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
		return s.get(new BlockVector(x, y, z));
	}

	public boolean has(int x, int y, int z)
	{
		return s.contains(new BlockVector(x, y, z));
	}

	public void put(int x, int y, int z, MB mb)
	{
		s.put(new BlockVector(x, y, z), mb);
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

	public void fill(GMap<BlockVector, MB> b)
	{
		clear();
		s.put(b);
	}

	public int sh(int g)
	{
		int m = (g / 2);
		return g % 2 == 0 ? m : m + 1;
	}

	public void place(Location l)
	{
		place(l, new NMSPlacer(l.getWorld()));
	}

	public void place(Location l, IPlacer placer)
	{
		place(l.getBlockX(), l.getBlockY(), l.getBlockZ(), placer);
	}

	public void place(int wx, int wy, int wz, IPlacer placer)
	{
		Location start = new Location(placer.getWorld(), wx, wy, wz).clone().add(sh(w), sh(h) + 1, sh(d));

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
		GMap<Location, MB> undo = new GMap<>();

		for(BlockVector i : getSchematic().k())
		{
			MB b = getSchematic().get(i);

			if(b.material.equals(Material.CONCRETE_POWDER))
			{
				continue;
			}

			Location f = start.clone().add(i);

			Material m = placer.get(f.clone().subtract(0, 1, 0)).material;
			if(i.getBlockY() == mountHeight && (m.equals(Material.WATER) || m.equals(Material.STATIONARY_WATER) || m.equals(Material.LAVA) || m.equals(Material.STATIONARY_LAVA)))
			{
				for(Location j : undo.k())
				{
					placer.set(j, undo.get(j));
				}

				return;
			}

			if(b.material.equals(Material.SKULL))
			{
				continue;
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
		GMap<BlockVector, MB> g = s.copy();
		s.clear();

		for(BlockVector i : g.k())
		{
			MB mb = g.get(i);
			s.put(VectorMath.rotate(from, to, i).toBlockVector(), mb);
		}

		name = name + "-rt" + to.name();
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

				for(BlockVector i : s.k())
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

		for(BlockVector i : getSchematic().k())
		{
			if(i.getBlockX() > maxX)
			{
				maxX = i.getBlockX();
			}

			if(i.getBlockY() > maxY)
			{
				maxY = i.getBlockY();
			}

			if(i.getBlockZ() > maxZ)
			{
				maxZ = i.getBlockZ();
			}

			if(i.getBlockX() < minX)
			{
				minX = i.getBlockX();
			}

			if(i.getBlockY() < minY)
			{
				minY = i.getBlockY();
			}

			if(i.getBlockZ() < minZ)
			{
				minZ = i.getBlockZ();
			}
		}

		for(int i = minX; i <= maxX; i++)
		{
			for(int j = minZ; j <= maxZ; j++)
			{
				BlockVector highest = null;

				for(BlockVector k : getSchematic().k())
				{
					if(k.getBlockX() == i && k.getBlockZ() == j)
					{
						if(highest == null)
						{
							highest = k;
						}

						else if(highest.getBlockY() < k.getBlockY())
						{
							highest = k;
						}
					}
				}

				if(highest != null)
				{
					BlockVector mbv = highest.clone().add(new Vector(0, 1, 0)).toBlockVector();
					added = true;
					getSchematic().put(mbv, MB.of(Material.SNOW, RNG.r.nextInt((int) M.clip(factor, 0, 8))));
				}
			}
		}

		if(added)
		{
			h++;
			recalculateMountShift();
		}
	}
}
