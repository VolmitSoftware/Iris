package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.Settings.OreSettings;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerOres extends GenLayer
{
	private CNG ore;

	public GenLayerOres(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		ore = new CNG(rng.nextParallelRNG(12944), 1D, 1).scale(0.1);
		//@done
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return gnoise;
	}

	public void genOres(double xxf, double zzf, int x, int z, int h, AtomicChunkData data, ChunkPlan plan)
	{
		PrecisionStopwatch s = PrecisionStopwatch.start();
		OreSettings o = Iris.settings.ore;
		
		for(int i = 0; i < h; i++)
		{
			if(i >= o.ironMinHeight && i <= o.ironMaxHeight && 
					ore.noise(xxf + 64, i, zzf - 64) < IrisInterpolation.lerpCenterSinBezier(
					o.ironMinDispersion, 
					Iris.settings.ore.ironMaxDispersion, 
					M.lerpInverse(o.ironMinHeight, o.ironMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				
				data.setBlock(x, i, z, Material.IRON_ORE);
			}
			
			if(i >= o.coalMinHeight && i <= o.coalMaxHeight && 
					ore.noise(xxf + 128, i, zzf - 128) < IrisInterpolation.lerpCenterSinBezier(
					o.coalMinDispersion, 
					Iris.settings.ore.coalMaxDispersion, 
					M.lerpInverse(o.coalMinHeight, o.coalMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				data.setBlock(x, i, z, Material.COAL_ORE);
			}
			
			if(i >= o.goldMinHeight && i <= o.goldMaxHeight && 
					ore.noise(xxf + 64, i, zzf - 128) < IrisInterpolation.lerpCenterSinBezier(
					o.goldMinDispersion, 
					Iris.settings.ore.goldMaxDispersion, 
					M.lerpInverse(o.goldMinHeight, o.goldMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				data.setBlock(x, i, z, Material.GOLD_ORE);
			}
			
			if(i >= o.redstoneMinHeight && i <= o.redstoneMaxHeight && 
					ore.noise(xxf + 128, i, zzf - 64) < IrisInterpolation.lerpCenterSinBezier(
					o.redstoneMinDispersion, 
					Iris.settings.ore.redstoneMaxDispersion, 
					M.lerpInverse(o.redstoneMinHeight, o.redstoneMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				data.setBlock(x, i, z, Material.REDSTONE_ORE);
			}
			
			if(i >= o.lapisMinHeight && i <= o.lapisMaxHeight && 
					ore.noise(xxf + 256, i, zzf - 64) < IrisInterpolation.lerpCenterSinBezier(
					o.lapisMinDispersion, 
					Iris.settings.ore.lapisMaxDispersion, 
					M.lerpInverse(o.lapisMinHeight, o.lapisMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				data.setBlock(x, i, z, Material.LAPIS_ORE);
			}
			
			if(i >= o.diamondMinHeight && i <= o.diamondMaxHeight && 
					ore.noise(xxf + 64, i, zzf - 256) < IrisInterpolation.lerpCenterSinBezier(
					o.diamondMinDispersion, 
					Iris.settings.ore.diamondMaxDispersion, 
					M.lerpInverse(o.diamondMinHeight, o.diamondMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				data.setBlock(x, i, z, Material.DIAMOND_ORE);
			}
			
			if(i >= o.emeraldMinHeight && i <= o.emeraldMaxHeight && 
					ore.noise(xxf + 128, i, zzf - 256) < IrisInterpolation.lerpCenterSinBezier(
					o.emeraldMinDispersion, 
					Iris.settings.ore.emeraldMaxDispersion, 
					M.lerpInverse(o.emeraldMinHeight, o.emeraldMaxHeight, i)))
			{
				if(!can(data.getType(x, i, z)))
				{
					continue;
				}
				data.setBlock(x, i, z, Material.EMERALD_ORE);
			}
		}

		iris.getMetrics().stop("ores:ms:x256:/chunk:..", s);
	}
	
	public boolean can(Material m)
	{
		return m.equals(Material.STONE) || m.name().endsWith("_ORE");
	}
}
