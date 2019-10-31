package ninja.bytecode.iris.gen;

import java.util.Random;
import java.util.function.Function;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.iris.biome.CBI;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.iris.util.PolygonGenerator.EnumPolygonGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private CNG fractures2;
	private CNG fractures4;
	private PolygonGenerator.EnumPolygonGenerator<CBI> biomeGenerator;
	private Function<CNG, CNG> factory;
	private double closest;

	public GenLayerBiome(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		double scale = 1.25D;
		factory = (g) -> g
				.fractureWith(new CNG(g.nextRNG().nextRNG(), 1D, 32)
						.scale(0.2112)
						.fractureWith(new CNG(g.nextRNG(), 1D, 16)
								.scale(0.132),
								 333), 588);
		fractures2 = new CNG(rng.nextRNG(), 1, 32).scale(0.02);
		fractures4 = new CNG(rng.nextRNG(), 1, 16).scale(0.12);
		
		biomeGenerator = new PolygonGenerator.EnumPolygonGenerator<CBI>(rng.nextRNG(), 0.00755 * Iris.settings.gen.biomeScale, 1, 
				new CBI[] {
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

	public CBI getBiome(double x, double z)
	{
		double scram2 = fractures2.noise(x, z) * 188.35;
		double scram4 = fractures4.noise(x, z) * 47;
		double a = x - scram2 - scram4;
		double b = z + scram2 + scram4;
		a += Math.sin(b) * 12;
		b += Math.cos(a) * 12;
		return biomeGenerator.getChoice(a, b);
	}

	public double getCenterPercent(double x, double z)
	{
		double scram2 = fractures2.noise(x, z) * 188.35;
		double scram4 = fractures4.noise(x, z) * 47;
		double a = x - scram2 - scram4;
		double b = z + scram2 + scram4;
		a += Math.sin(b) * 12;
		b += Math.cos(a) * 12;
		return biomeGenerator.getClosestNeighbor(a, b);
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		CBI biome = getBiome(dx, dz);
		return ((1D + (biome.getAmp())) * noise) + (biome.getHeight() / 3D);
	}
}
