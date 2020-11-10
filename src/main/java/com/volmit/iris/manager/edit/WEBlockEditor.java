package com.volmit.iris.manager.edit;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.volmit.iris.util.M;

public class WEBlockEditor implements BlockEditor
{
	private final World world;
	private final EditSession es;
	private long last;

	public WEBlockEditor(World world)
	{
		last = M.ms();
		this.world = world;
		es = WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(world)).build();
	}

	@SuppressWarnings("deprecation")
	public void setBiome(int x, int z, Biome b)
	{
		es.setBiome(BlockVector2.at(x, z), BiomeTypes.get("minecraft:" + b.name().toLowerCase()));
	}

	public void setBiome(int x, int y, int z, Biome b)
	{
		es.setBiome(BlockVector3.at(x, y, z), BiomeTypes.get("minecraft:" + b.name().toLowerCase()));
	}

	@Override
	public void set(int x, int y, int z, BlockData d)
	{
		es.rawSetBlock(BlockVector3.at(x, y, z), BukkitAdapter.adapt(d));
	}

	@Override
	public BlockData get(int x, int y, int z)
	{
		return world.getBlockAt(x, y, z).getBlockData();
	}

	@Override
	public void close()
	{
		es.close();
		return;
	}

	@Override
	public long last()
	{
		return last;
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{
		return world.getBiome(x, y, z);
	}

	@SuppressWarnings("deprecation")
	@Override
	public Biome getBiome(int x, int z)
	{
		return world.getBiome(x, z);
	}
}
