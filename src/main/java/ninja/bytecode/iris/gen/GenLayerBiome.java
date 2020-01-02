package ninja.bytecode.iris.gen;

import java.util.Random;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.iris.biome.CBI;
import ninja.bytecode.iris.util.MaxingGenerator;
import ninja.bytecode.iris.util.MaxingGenerator.EnumMaxingGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private EnumMaxingGenerator<CBI> biomeGenerator;
	private MaxingGenerator roads;
	private Function<CNG, CNG> factory;
	private CNG pathCheck;
	private CNG riverCheck;
	private CNG fracture;

	public GenLayerBiome(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		fracture = new CNG(rng.nextRNG(), 1D, 24).scale(0.0021).fractureWith(new CNG(rng.nextRNG(), 1D, 12).scale(0.01), 12250);
		factory = (g) -> g.fractureWith(new CNG(rng.nextRNG(), 1D, 4).scale(0.02), 56);
		riverCheck = new CNG(rng.nextRNG(), 1D, 2).scale(0.00096);
		pathCheck = new CNG(rng.nextRNG(), 1D, 1).scale(0.00096);
		roads = new MaxingGenerator(rng.nextRNG(), 5, 0.00055, 8, factory);
		biomeGenerator = new EnumMaxingGenerator<CBI>(rng.nextRNG(), 0.00755 * Iris.settings.gen.biomeScale, 1, 
				new CBI[] {
						CBI.HAUNTED_FOREST,
						CBI.FOREST_MOUNTAINS,
						CBI.DESERT,
						CBI.DESERT_HILLS,
						CBI.MESA,
						CBI.DESERT_COMBINED,
						CBI.SAVANNA,
						CBI.SAVANNA_HILLS,
						CBI.DESERT_RED,
						CBI.JUNGLE,
						CBI.JUNGLE_HILLS,
						CBI.SWAMP,
						CBI.OCEAN,
						CBI.PLAINS,
						CBI.DECAYING_PLAINS,
						CBI.FOREST,
						CBI.FOREST_HILLS,
						CBI.BIRCH_FOREST,
						CBI.BIRCH_FOREST_HILLS,
						CBI.ROOFED_FOREST,
						CBI.TAIGA,
						CBI.EXTREME_HILLS,
						CBI.EXTREME_HILLS_TREES,
						CBI.TAIGA_COLD,
						CBI.TAIGA_COLD_HILLS,
						CBI.ICE_FLATS,
						CBI.ICE_MOUNTAINS,
						CBI.REDWOOD_TAIGA,
						CBI.REDWOOD_TAIGA_HILLS,
				}, factory);
		//@done
	}

	public CBI getBiome(double xx, double zz)
	{
		double x = xx + (fracture.noise(zz, xx) * 1550D);
		double z = zz - (fracture.noise(xx, zz) * 1550D);
		
		if(riverCheck.noise(x, z) > 0.75)
		{
			if(biomeGenerator.hasBorder(3, 3 + Math.pow(riverCheck.noise(x, z), 1.25) * 16, x, z))
			{
				return CBI.RIVER;
			}
		}
		
		CBI cbi = biomeGenerator.getChoice(x, z);
		
		if(pathCheck.noise(x, z) > 0.33)
		{
			CBI road = CBI.ROAD_GRAVEL;
			
			if(cbi.getSurface().get(0).material.equals(Material.GRASS))
			{
				road = CBI.ROAD_GRASSY;
			}
			
			if(Math.abs(road.getHeight() - cbi.getHeight()) < 0.0001 && roads.hasBorder(4, 3, xx, zz))
			{
				return road;
			}
		}
		
		return cbi;
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
