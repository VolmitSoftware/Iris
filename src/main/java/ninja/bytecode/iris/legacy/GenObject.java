package ninja.bytecode.iris.legacy;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.material.Directional;
import org.bukkit.material.Ladder;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Stairs;
import org.bukkit.material.Vine;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.IrisObject;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.VectorMath;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.logging.L;

public class GenObject
{
	public static IDLibrary lib = new IDLibrary();
	private boolean centeredHeight;
	private int w;
	private int h;
	private int d;
	private int failures;
	private int successes;
	private boolean gravity;
	private String name = "?";
	private KMap<SBlockVector, BlockData> s;
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

	public KMap<SBlockVector, BlockData> getSchematic()
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
			SBlockVector v = new SBlockVector(din.readInt(), din.readInt(), din.readInt());
			int id = din.readInt();
			byte dat = (byte) din.readInt();
			Material mat = IDLibrary.getMaterial(id);
			BlockData data = Bukkit.getUnsafe().fromLegacy(Bukkit.getUnsafe().toLegacy(mat), dat);

			if(data != null)
			{
				s.put(v, data);
			}

			else
			{
				throw new RuntimeException("Failed to convert");
			}
		}
	}

	public BlockData get(int x, int y, int z)
	{
		return s.get(new SBlockVector(x, y, z));
	}

	public boolean has(int x, int y, int z)
	{
		return s.containsKey(new SBlockVector(x, y, z));
	}

	public void put(int x, int y, int z, BlockData mb)
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

	public void fill(KMap<SBlockVector, BlockData> b)
	{
		clear();
		s.putAll(b);
	}

	public int sh(int g)
	{
		int m = (g / 2);
		return g % 2 == 0 ? m : m + 1;
	}

	public static GenObject load(InputStream in) throws IOException
	{
		GenObject s = new GenObject(1, 1, 1);
		s.read(in, true);

		return s;
	}

	public static IrisObject loadAsModern(File f) throws IOException
	{
		GenObject legacy = new GenObject(1, 1, 1);
		legacy.name = f.getName().replaceAll("\\Q.ish\\E", "");
		FileInputStream fin = new FileInputStream(f);
		legacy.read(fin, true);

		IrisObject object = new IrisObject(legacy.w, legacy.h, legacy.d);
		object.setLoadKey(legacy.name);

		for(SBlockVector i : legacy.s.k())
		{
			object.getBlocks().put(new BlockVector(i.getX(), i.getY(), i.getZ()), legacy.s.get(i));
		}

		return object;
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