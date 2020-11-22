package com.volmit.iris.nms.v16_3;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.nms.INMSCreator;
import com.volmit.iris.scaffold.cache.AtomicCache;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;

public class NMSBinding16_3 implements INMSBinding
{
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator16_3::new);
	}

	@Override
	public Object getBiomeBase(World world, Biome biome)
	{
		return CraftBlock.biomeToBiomeBase(((CraftWorld)world).getHandle().r().b(net.minecraft.server.v1_16_R3.IRegistry.ay), biome);
	}
}
