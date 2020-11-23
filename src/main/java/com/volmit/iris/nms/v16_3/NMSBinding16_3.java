package com.volmit.iris.nms.v16_3;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.nms.INMSCreator;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.KMap;
import net.minecraft.server.v1_16_R3.BiomeBase;
import net.minecraft.server.v1_16_R3.IRegistry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;

public class NMSBinding16_3 implements INMSBinding
{
	private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator16_3::new);
	}

	@Override
	public Object getBiomeBase(World world, Biome biome)
	{
		return getBiomeBase(((CraftWorld)world).getHandle().r().b(net.minecraft.server.v1_16_R3.IRegistry.ay), biome);
	}

	@Override
	public Object getBiomeBase(Object registry, Biome biome) {
		Object v = baseBiomeCache.get(biome);

		if(v != null)
		{
			return v;
		}
		v = org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, biome);
		baseBiomeCache.put(biome, v);
		return v;
	}
}
