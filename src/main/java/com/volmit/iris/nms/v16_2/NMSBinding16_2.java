package com.volmit.iris.nms.v16_2;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.util.KMap;
import net.minecraft.server.v1_16_R2.BiomeBase;
import net.minecraft.server.v1_16_R2.IRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.block.CraftBlock;

public class NMSBinding16_2 implements INMSBinding
{
	private final KMap<Biome, Object> baseBiomeCache = new KMap<>();

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

	@Override
	public int getBiomeId(Biome biome) {
		for(World i : Bukkit.getWorlds())
		{
			if(i.getEnvironment().equals(World.Environment.NORMAL))
			{
				IRegistry<BiomeBase> registry = ((CraftWorld)i).getHandle().r().b(IRegistry.ay);
				return registry.a((BiomeBase) getBiomeBase(registry, biome));
			}
		}

		return biome.ordinal();
	}

	@Override
	public boolean isBukkit() {
		return false;
	}
}
