package com.volmit.iris.nms.v1X;

import com.volmit.iris.nms.INMSBinding;
import org.bukkit.World;
import org.bukkit.block.Biome;

public class NMSBinding1X implements INMSBinding
{
	public Object getBiomeBase(World world, Biome biome)
	{
		return null;
	}

	@Override
	public Object getBiomeBase(Object registry, Biome biome) {
		return null;
	}

	@Override
	public boolean isBukkit() {
		return true;
	}

	@Override
	public int getBiomeId(Biome biome) {
		return biome.ordinal();
	}
}
