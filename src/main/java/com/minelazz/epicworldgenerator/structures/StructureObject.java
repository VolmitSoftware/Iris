package com.minelazz.epicworldgenerator.structures;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.EnumSet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.B;

public class StructureObject implements Serializable
{
	public static final String MESSAGE0 = "This was created from .ewg serialization files";
	public static final String MESSAGE1 = "This is not copied code, it is intended to ";
	public static final String MESSAGE2 = "be used so that people can convert EWG files";
	public static final String MESSAGE3 = "into .IOB files (iris objects)";

	public static IrisObject convert(File so) throws IOException, ClassNotFoundException
	{
		FileInputStream fin = new FileInputStream(so);
		ObjectInputStream in = new ObjectInputStream(fin);
		StructureObject o = (StructureObject) in.readObject();
		in.close();
		int maxX = 0;
		int maxY = 0;
		int maxZ = 0;
		int minX = 0;
		int minY = 0;
		int minZ = 0;

		for(SOBlock i : o.blocks)
		{
			maxX = maxX < i.x ? i.x : maxX;
			maxY = maxY < i.y ? i.y : maxY;
			maxZ = maxZ < i.z ? i.z : maxZ;
			minX = minX > i.x ? i.x : minX;
			minY = minY > i.y ? i.y : minY;
			minZ = minZ > i.z ? i.z : minZ;
		}

		IrisObject iob = new IrisObject(maxX - minX, maxY - minY, maxZ - minZ);

		for(SOBlock i : o.blocks)
		{
			BlockData bdx = null;

			if(i.blockData == null)
			{
				FastBlockData f = map(i.id, i.data);
				bdx = f == null ? null : f.getBlockData();
			}

			else
			{
				bdx = B.get(i.blockData).getBlockData();
			}

			if(bdx != null)
			{
				iob.getBlocks().put(new BlockVector(i.x, -i.y, i.z), FastBlockData.of(bdx));
			}
		}

		return iob;
	}

	@SuppressWarnings("deprecation")
	private static final FastBlockData map(int id, int dat)
	{
		for(Material i : EnumSet.allOf(Material.class))
		{
			if(!i.isLegacy())
			{
				continue;
			}

			if(i.getId() == id)
			{
				return FastBlockData.of(Bukkit.getUnsafe().fromLegacy(i, (byte) dat));
			}
		}

		Iris.warn("Unknown Type " + id + ":" + dat);

		return null;
	}

	private static final long serialVersionUID = -905274143366977303L;

	public SOBlock[] blocks;
	public String name;

	public final class SOBlock implements Serializable
	{
		private static final long serialVersionUID = 2610063934261982315L;
		public final int x;
		public final int y;
		public final int z;
		public final int id;
		public final int data;
		public String meta;
		public String blockData;
		final StructureObject ref;

		public SOBlock(StructureObject structureObject, int x, int y, int z, String string)
		{
			this.ref = structureObject;
			meta = null;
			this.x = x;
			this.y = y;
			this.z = z;
			id = -1;
			data = 0;
			blockData = string;
		}
	}
}
