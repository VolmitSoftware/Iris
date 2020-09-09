package com.volmit.iris.edit;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
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

	@Override
	public void set(int x, int y, int z, BlockData d)
	{
		last = M.ms();
		es.rawSetBlock(BlockVector3.at(x, y, z), BukkitAdapter.adapt(d));
		world.getBlockAt(x, y, z).setBlockData(d, false);
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
}
