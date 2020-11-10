package com.volmit.iris.manager.link;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
