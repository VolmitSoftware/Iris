package com.volmit.iris.gen.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.DimensionalTerrainProvider;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.object.IrisStructureTile;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;

import lombok.Data;

@Data
public class AtomicSliverMap
{
	private final AtomicSliver[] slivers;
	private KMap<Integer, String> structures;
	private boolean parallaxGenerated;
	private boolean worldGenerated;

	public AtomicSliverMap()
	{
		structures = new KMap<>();
		parallaxGenerated = false;
		worldGenerated = false;
		slivers = new AtomicSliver[256];

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				slivers[i * 16 + j] = new AtomicSliver(i, j);
			}
		}
	}

	public void insert(AtomicSliverMap map)
	{
		for(int i = 0; i < 256; i++)
		{
			slivers[i].insert(map.slivers[i]);
		}
	}

	public void setStructure(int y, IrisStructure s, IrisStructureTile t)
	{
		structures.put(y, s.getLoadKey() + "." + s.getTiles().indexOf(t));
	}

	public IrisStructureResult getStructure(DimensionalTerrainProvider g, int y)
	{
		String v = structures.get(y);

		if(v == null)
		{
			return null;
		}

		String[] a = v.split("\\Q.\\E");

		IrisStructure s = g.getData().getStructureLoader().load(a[0]);

		if(s == null)
		{
			return null;
		}

		return new IrisStructureResult(s.getTiles().get(Integer.valueOf(a[1])), s);
	}

	public void write(OutputStream out) throws IOException
	{
		DataOutputStream dos = new DataOutputStream(out);
		dos.writeBoolean(isParallaxGenerated());
		dos.writeBoolean(isWorldGenerated());

		for(int i = 0; i < 256; i++)
		{
			try
			{
				slivers[i].write(dos);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		KList<String> structurePalette = new KList<>();

		for(Integer i : structures.k())
		{
			String struct = structures.get(i);

			if(!structurePalette.contains(struct))
			{
				structurePalette.add(struct);
			}
		}

		dos.writeByte(structurePalette.size() + Byte.MIN_VALUE);

		for(String i : structurePalette)
		{
			dos.writeUTF(i);
		}

		dos.writeByte(structures.size() + Byte.MIN_VALUE);

		for(Integer i : structures.k())
		{
			dos.writeByte(i + Byte.MIN_VALUE);
			dos.writeByte(structurePalette.indexOf(structures.get(i)) + Byte.MIN_VALUE);
		}

		dos.flush();
	}

	public void read(InputStream in) throws IOException
	{
		DataInputStream din = new DataInputStream(in);
		parallaxGenerated = din.readBoolean();
		worldGenerated = din.readBoolean();

		for(int i = 0; i < 256; i++)
		{
			try
			{
				slivers[i].read(din);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		int spc = din.readByte() - Byte.MIN_VALUE;
		KList<String> spal = new KList<>();
		for(int i = 0; i < spc; i++)
		{
			spal.add(din.readUTF());
		}

		int smc = din.readByte() - Byte.MIN_VALUE;
		structures.clear();

		for(int i = 0; i < smc; i++)
		{
			structures.put(din.readByte() - Byte.MIN_VALUE, spal.get(din.readByte() - Byte.MIN_VALUE));
		}
	}

	public AtomicSliver getSliver(int x, int z)
	{
		return slivers[x * 16 + z];
	}

	public void write(ChunkData data, BiomeGrid grid, HeightMap height, boolean skipNull)
	{
		for(AtomicSliver i : slivers)
		{
			if(i != null)
			{
				i.write(data, skipNull);
				i.write(grid);
				i.write(height);
			}
		}
	}

	public void inject(ChunkData currentData)
	{
		for(AtomicSliver i : slivers)
		{
			i.inject(currentData);
		}
	}

	public boolean isModified()
	{
		for(AtomicSliver i : slivers)
		{
			if(i.isModified())
			{
				return true;
			}
		}

		return false;
	}

	public void injectUpdates(AtomicSliverMap map)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				getSliver(i, j).inject(map.getSliver(i, j).getUpdatables());
			}
		}
	}

	public void reset()
	{
		setParallaxGenerated(false);
		setWorldGenerated(false);
		getStructures().clear();

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				slivers[i * 16 + j] = new AtomicSliver(i, j);
			}
		}
	}
}
