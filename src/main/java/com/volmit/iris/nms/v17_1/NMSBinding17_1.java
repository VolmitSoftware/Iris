package com.volmit.iris.nms.v17_1;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.util.KMap;
import net.minecraft.core.IRegistry;
import net.minecraft.world.level.biome.BiomeBase;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;

public class NMSBinding17_1 implements INMSBinding
{
	private final KMap<Biome, Object> baseBiomeCache = new KMap<>();

	@Override
	public Object getBiomeBase(World world, Biome biome)
	{
		return getBiomeBase(((CraftWorld)world).getHandle().r().b(IRegistry.ay), biome);
	}

	@Override
	public Object getBiomeBase(Object registry, Biome biome) {
		Object v = baseBiomeCache.get(biome);

		if(v != null)
		{
			return v;
		}
		v = org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, biome);
		if (v == null) {
			// Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
			// But, this does NOT exist within CraftBukkit which makes it return an error.
			// So, we will just return the ID that the plains biome returns instead.
			return org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, Biome.PLAINS);
		}
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
