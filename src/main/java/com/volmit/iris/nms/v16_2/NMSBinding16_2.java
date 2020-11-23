package com.volmit.iris.nms.v16_2;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.nms.INMSCreator;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.KMap;
import net.minecraft.server.v1_16_R2.BiomeBase;
import net.minecraft.server.v1_16_R2.IRegistry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.block.CraftBlock;

public class NMSBinding16_2 implements INMSBinding
{
	private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
	private final AtomicCache<INMSCreator> creator = new AtomicCache<>();

	@Override
	public INMSCreator getCreator()
	{
		return creator.aquire(NMSCreator16_2::new);
	}

	@Override
	public Object getBiomeBase(World world, Biome biome)
	{
		return getBiomeBase(((CraftWorld)world).getHandle().r().b(net.minecraft.server.v1_16_R2.IRegistry.ay), biome);
	}

	@Override
	public Object getBiomeBase(Object registry, Biome biome) {
		Object v = baseBiomeCache.get(biome);

		if(v != null)
		{
			return v;
		}
		v = CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, biome);
		baseBiomeCache.put(biome, v);
		return v;
	}
}
