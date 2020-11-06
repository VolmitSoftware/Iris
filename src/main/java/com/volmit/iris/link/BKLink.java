package com.volmit.iris.link;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.volmit.iris.util.KList;
import com.volmit.iris.v2.lighting.LightingChunk;
import com.volmit.iris.v2.lighting.LightingService;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

public class BKLink
{
	public BKLink()
	{

	}

	public void updateBlock(Block b) {
		BlockData d = b.getBlockData();
		b.setType(Material.AIR, false);
		b.setBlockData(d, true);
	}

	public boolean supported()
	{
		return getBK() != null;
	}

	public Plugin getBK()
	{
		Plugin p = Bukkit.getPluginManager().getPlugin("BKCommonLib");

		if(p == null)
		{
			return null;
		}

		return p;
	}
}
