package com.volmit.iris.generator.atomics;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.KList;

import lombok.Getter;

@SuppressWarnings("deprecation")
public class TerrainHunk extends Hunk<TerrainNode> implements BiomeGrid, ChunkData
{
	@Getter
	private HeightHunk height;

	@Getter
	private Hunk<IrisBiome> biome;

	public TerrainHunk(int w, int h, int d)
	{
		super(w, h, d);
		this.height = new HeightHunk(w, d);
		this.biome = new Hunk<IrisBiome>(w, h, d);
	}

	public TerrainHunk(int w, int h, int d, HeightHunk hh)
	{
		super(w, h, d);
		this.height = hh;
		this.biome = new Hunk<IrisBiome>(w, h, d);
	}

	@Override
	public int getMaxHeight()
	{
		return getH();
	}

	private void set(int x, int y, int z, BlockData block)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			n = new TerrainNode(Biome.THE_VOID, block);
		}

		else
		{
			n = n.setBlockData(block);
		}

		set(x, y, z, n);
	}

	private void set(int x, int y, int z, Biome biome)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			n = new TerrainNode(biome, Material.AIR.createBlockData());
		}

		else
		{
			n = n.setBiome(biome);
		}

		set(x, y, z, n);
	}

	@Override
	public void setBlock(int x, int y, int z, Material material)
	{
		set(x, y, z, material.createBlockData());
	}

	@Override
	public void setBlock(int x, int y, int z, MaterialData material)
	{
		set(x, y, z, material.getItemType().createBlockData());
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData blockData)
	{
		set(x, y, z, blockData);
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public Material getType(int x, int y, int z)
	{
		return getBlockData(x, y, z).getMaterial();
	}

	@Override
	public MaterialData getTypeAndData(int x, int y, int z)
	{
		return new MaterialData(getBlockData(x, y, z).getMaterial());
	}

	@Override
	public BlockData getBlockData(int x, int y, int z)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			return Material.VOID_AIR.createBlockData();
		}

		return n.getBlockData();
	}

	@Override
	public byte getData(int x, int y, int z)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public Biome getBiome(int x, int z)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			return Biome.THE_VOID;
		}

		return n.getBiome();
	}

	@Override
	public void setBiome(int x, int z, Biome bio)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public void setBiome(int x, int y, int z, Biome bio)
	{
		set(x, y, z, bio);
	}

	@SuppressWarnings("unchecked")
	@SafeVarargs
	public static TerrainHunk combined(TerrainNode defaultNode, TerrainHunk... hunks)
	{
		KList<HeightHunk> hhunks = new KList<>();
		KList<Hunk<IrisBiome>> bhunks = new KList<>();

		int w = 0;
		int h = 0;
		int d = 0;

		for(TerrainHunk i : hunks)
		{
			hhunks.add(i.getHeight());
			bhunks.add(i.getBiome());
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		TerrainHunk b = new TerrainHunk(w, h, d);
		b.fill(defaultNode);

		for(TerrainHunk i : hunks)
		{
			b.insert(i);
		}

		b.height = HeightHunk.combined((byte) 0, hhunks.toArray(new HeightHunk[hhunks.size()]));
		b.biome = Hunk.combined(null, hhunks.toArray(new Hunk[hhunks.size()]));

		return b;
	}

	@SuppressWarnings("unchecked")
	@SafeVarargs
	public static TerrainHunk combined(TerrainHunk... hunks)
	{
		KList<HeightHunk> hhunks = new KList<>();
		KList<Hunk<IrisBiome>> bhunks = new KList<>();
		int w = 0;
		int h = 0;
		int d = 0;

		for(TerrainHunk i : hunks)
		{
			hhunks.add(i.getHeight());
			bhunks.add(i.getBiome());
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		TerrainHunk b = new TerrainHunk(w, h, d);

		for(TerrainHunk i : hunks)
		{
			b.insert(i);
		}

		b.height = HeightHunk.combined((byte) 0, hhunks.toArray(new HeightHunk[hhunks.size()]));
		b.biome = Hunk.combined(null, hhunks.toArray(new Hunk[hhunks.size()]));

		return b;
	}
}
