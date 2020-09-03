package com.volmit.iris.object;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.BlockVector;

import com.volmit.iris.util.B;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.IObjectPlacer;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisObject extends IrisRegistrant
{
	private static final BlockData AIR = B.getBlockData("CAVE_AIR");
	private static final BlockData[] SNOW_LAYERS = new BlockData[] {B.getBlockData("minecraft:snow[layers=1]"), B.getBlockData("minecraft:snow[layers=2]"), B.getBlockData("minecraft:snow[layers=3]"), B.getBlockData("minecraft:snow[layers=4]"), B.getBlockData("minecraft:snow[layers=5]"), B.getBlockData("minecraft:snow[layers=6]"), B.getBlockData("minecraft:snow[layers=7]"), B.getBlockData("minecraft:snow[layers=8]")};
	public static boolean shitty = false;
	private KMap<BlockVector, BlockData> blocks;
	private int w;
	private int d;
	private int h;
	private transient BlockVector center;

	public IrisObject copy()
	{
		IrisObject o = new IrisObject(w, h, d);
		o.setLoadKey(o.getLoadKey());
		o.setCenter(getCenter().clone());

		for(BlockVector i : getBlocks().k())
		{
			o.getBlocks().put(i.clone(), getBlocks().get(i).clone());
		}

		return o;
	}

	public IrisObject(int w, int h, int d)
	{
		blocks = new KMap<>();
		this.w = w;
		this.h = h;
		this.d = d;
		center = new BlockVector(w / 2, h / 2, d / 2);
	}

	public static BlockVector sampleSize(File file) throws IOException
	{
		FileInputStream in = new FileInputStream(file);
		DataInputStream din = new DataInputStream(in);
		BlockVector bv = new BlockVector(din.readInt(), din.readInt(), din.readInt());
		din.close();
		return bv;
	}

	public void read(InputStream in) throws IOException
	{
		if(shitty)
		{
			return;
		}

		DataInputStream din = new DataInputStream(in);
		this.w = din.readInt();
		this.h = din.readInt();
		this.d = din.readInt();
		center = new BlockVector(w / 2, h / 2, d / 2);
		int s = din.readInt();

		for(int i = 0; i < s; i++)
		{
			blocks.put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), B.getBlockData(din.readUTF()));
		}
	}

	public void read(File file) throws IOException
	{
		if(shitty)
		{
			return;
		}
		FileInputStream fin = new FileInputStream(file);
		read(fin);
		fin.close();
	}

	public void write(File file) throws IOException
	{
		if(shitty)
		{
			return;
		}
		file.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(file);
		write(out);
		out.close();
	}

	public void write(OutputStream o) throws IOException
	{
		if(shitty)
		{
			return;
		}
		DataOutputStream dos = new DataOutputStream(o);
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(blocks.size());
		for(BlockVector i : blocks.k())
		{
			dos.writeShort(i.getBlockX());
			dos.writeShort(i.getBlockY());
			dos.writeShort(i.getBlockZ());
			dos.writeUTF(blocks.get(i).getAsString(true));
		}
	}

	public void clean()
	{
		if(shitty)
		{
			return;
		}
		KMap<BlockVector, BlockData> d = blocks.copy();
		blocks.clear();

		for(BlockVector i : d.k())
		{
			blocks.put(new BlockVector(i.getBlockX(), i.getBlockY(), i.getBlockZ()), d.get(i));
		}
	}

	public void setUnsigned(int x, int y, int z, BlockData block)
	{
		if(shitty)
		{
			return;
		}
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " exceeds limit of " + w + " " + h + " " + d);
		}

		BlockVector v = new BlockVector(x, y, z).subtract(center).toBlockVector();

		if(block == null)
		{
			blocks.remove(v);
		}

		else
		{
			blocks.put(v, block);
		}
	}

	public void place(int x, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng)
	{
		if(shitty)
		{
			return;
		}
		place(x, -1, z, placer, config, rng);
	}

	public int place(int x, int yv, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng)
	{
		return place(x, yv, z, placer, config, rng, null);
	}

	public int place(int x, int yv, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng, Consumer<BlockPosition> listener)
	{
		boolean warped = !config.getWarp().isFlat();
		boolean stilting = (config.getMode().equals(ObjectPlaceMode.STILT) || config.getMode().equals(ObjectPlaceMode.FAST_STILT));
		KMap<ChunkPosition, Integer> lowmap = stilting ? new KMap<>() : null;
		KMap<ChunkPosition, BlockData> lowmapData = stilting ? new KMap<>() : null;
		KMap<ChunkPosition, Integer> heightmap = config.getSnow() > 0 ? new KMap<>() : null;
		int spinx = rng.imax() / 1000;
		int spiny = rng.imax() / 1000;
		int spinz = rng.imax() / 1000;
		int rty = config.getRotation().rotate(new BlockVector(0, getCenter().getBlockY(), 0), spinx, spiny, spinz).getBlockY();
		int ty = config.getTranslate().translate(new BlockVector(0, getCenter().getBlockY(), 0), config.getRotation(), spinx, spiny, spinz).getBlockY();
		int y = -1;

		if(yv < 0)
		{
			if(config.getMode().equals(ObjectPlaceMode.CENTER_HEIGHT))
			{
				y = placer.getHighest(x, z, config.isUnderwater()) + rty;
			}

			else if(config.getMode().equals(ObjectPlaceMode.MAX_HEIGHT) || config.getMode().equals(ObjectPlaceMode.STILT))
			{
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i++)
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j++)
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h > y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.FAST_MAX_HEIGHT) || config.getMode().equals(ObjectPlaceMode.FAST_STILT))
			{
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i += (rotatedDimensions.getBlockX() / 2))
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j += (rotatedDimensions.getBlockZ() / 2))
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h > y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.MIN_HEIGHT))
			{
				y = 257;
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i++)
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j++)
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h < y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.FAST_MIN_HEIGHT))
			{
				y = 257;
				BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
				BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

				for(int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i += (rotatedDimensions.getBlockX() / 2))
				{
					for(int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j += (rotatedDimensions.getBlockZ() / 2))
					{
						int h = placer.getHighest(i, j, config.isUnderwater()) + rty;

						if(h < y)
						{
							y = h;
						}
					}
				}
			}

			else if(config.getMode().equals(ObjectPlaceMode.PAINT))
			{
				y = placer.getHighest(x, z, config.isUnderwater()) + rty;
			}
		}

		else
		{
			y = yv;
		}

		if(yv >= 0 && config.isBottom())
		{
			y += Math.floorDiv(h, 2);
		}

		if(yv < 0)
		{
			if(!config.isUnderwater() && !config.isOnwater() && placer.isUnderwater(x, z))
			{
				return -1;
			}
		}

		if(config.isUnderwater() && y + rty + ty >= placer.getFluidHeight())
		{
			return -1;
		}

		if(!config.getClamp().canPlace(y + rty + ty, y - rty + ty))
		{
			return -1;
		}

		if(config.isBore())
		{
			for(int i = x - Math.floorDiv(w, 2); i <= x + Math.floorDiv(w, 2) - (w % 2 == 0 ? 1 : 0); i++)
			{
				for(int j = y - Math.floorDiv(h, 2) - config.getBoarExtendMinY(); j <= y + Math.floorDiv(h, 2) + config.getBoarExtendMaxY() - (h % 2 == 0 ? 1 : 0); j++)
				{
					for(int k = z - Math.floorDiv(d, 2); k <= z + Math.floorDiv(d, 2) - (d % 2 == 0 ? 1 : 0); k++)
					{
						placer.set(i, j, k, AIR);
					}
				}
			}
		}

		for(BlockVector g : blocks.keySet())
		{
			BlockVector i = g.clone();
			i = config.getRotation().rotate(i.clone(), spinx, spiny, spinz).clone();
			i = config.getTranslate().translate(i.clone(), config.getRotation(), spinx, spiny, spinz).clone();
			BlockData data = blocks.get(g).clone();

			if(placer.isPreventingDecay() && data instanceof Leaves && !((Leaves) data).isPersistent())
			{
				((Leaves) data).setPersistent(true);
			}

			for(IrisObjectReplace j : config.getEdit())
			{
				for(BlockData k : j.getFind())
				{
					if(j.isExact() ? k.matches(data) : k.getMaterial().equals(data.getMaterial()))
					{
						data = j.getReplace(rng, i.getX() + x, i.getY() + y, i.getZ() + z).clone();
					}
				}
			}

			data = config.getRotation().rotate(data, spinx, spiny, spinz);
			int xx = x + (int) Math.round(i.getX());
			int yy = y + (int) Math.round(i.getY());
			int zz = z + (int) Math.round(i.getZ());

			if(warped)
			{
				xx += config.warp(rng, i.getX() + x, i.getY() + y, i.getZ() + z);
				zz += config.warp(rng, i.getZ() + z, i.getY() + y, i.getX() + x);
			}

			if(yv < 0 && config.getMode().equals(ObjectPlaceMode.PAINT))
			{
				yy = (int) Math.round(i.getY()) + Math.floorDiv(h, 2) + placer.getHighest(xx, zz, config.isUnderwater());
			}

			if(heightmap != null)
			{
				ChunkPosition pos = new ChunkPosition(xx, zz);

				if(!heightmap.containsKey(pos))
				{
					heightmap.put(pos, yy);
				}

				if(heightmap.get(pos) < yy)
				{
					heightmap.put(pos, yy);
				}
			}

			if(config.isMeld() && !placer.isSolid(xx, yy, zz))
			{
				continue;
			}

			if(config.isWaterloggable() && yy <= placer.getFluidHeight() && data instanceof Waterlogged)
			{
				((Waterlogged) data).setWaterlogged(true);
			}

			if(listener != null)
			{
				listener.accept(new BlockPosition(xx, yy, zz));
			}

			if(!data.getMaterial().equals(Material.AIR))
			{
				placer.set(xx, yy, zz, data);
			}

			if(stilting)
			{
				BlockData bdata = data;
				int yyy = yy;
				ChunkPosition ck = new ChunkPosition(xx, zz);
				lowmap.compute(ck, (k, v) ->
				{
					if(v == null)
					{
						lowmapData.put(ck, bdata);
						return yyy;
					}

					if(v > yyy)
					{
						lowmapData.put(ck, bdata);
						return yyy;
					}

					return v;
				});
			}
		}

		if(stilting)
		{
			for(ChunkPosition i : lowmap.keySet())
			{
				int xf = i.getX();
				int yf = lowmap.get(i);
				int zf = i.getZ();
				int yg = Math.floorDiv(h, 2) + placer.getHighest(xf, zf, config.isUnderwater());
				BlockData d = lowmapData.get(i);

				if(d != null)
				{
					for(int j = yf; j > yg - config.getOverStilt(); j--)
					{
						if(!d.getMaterial().equals(Material.AIR))
						{
							placer.set(xf, j, zf, d);
						}
					}
				}
			}
		}

		if(heightmap != null)
		{
			RNG rngx = rng.nextParallelRNG(3468854);

			for(ChunkPosition i : heightmap.k())
			{
				int vx = i.getX();
				int vy = heightmap.get(i);
				int vz = i.getZ();

				if(config.getSnow() > 0)
				{
					int height = rngx.i(0, (int) (config.getSnow() * 7));
					placer.set(vx, vy + 1, vz, SNOW_LAYERS[Math.max(Math.min(height, 7), 0)]);
				}
			}
		}

		return y;
	}

	public void rotate(IrisObjectRotation r, int spinx, int spiny, int spinz)
	{
		if(shitty)
		{
			return;
		}

		KMap<BlockVector, BlockData> v = blocks.copy();
		blocks.clear();

		for(BlockVector i : v.keySet())
		{
			blocks.put(r.rotate(i.clone(), spinx, spiny, spinz), r.rotate(v.get(i).clone(), spinx, spiny, spinz));
		}
	}

	public void place(Location at)
	{
		if(shitty)
		{
			return;
		}

		for(BlockVector i : blocks.keySet())
		{
			at.clone().add(0, getCenter().getY(), 0).add(i).getBlock().setBlockData(blocks.get(i), false);
		}
	}
}
