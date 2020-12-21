package com.volmit.iris.nms.v1X;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.nms.INMSCreator;
import com.volmit.iris.scaffold.cache.AtomicCache;
import org.bukkit.World;
import org.bukkit.block.Biome;

public class NMSBinding1X implements INMSBinding
{
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator1X::new);
	}

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
}
