package ninja.bytecode.iris.layer;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import ninja.bytecode.iris.generator.DimensionChunkGenerator;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.CaveResult;
import ninja.bytecode.iris.util.FastNoise;
import ninja.bytecode.iris.util.FastNoise.CellularDistanceFunction;
import ninja.bytecode.iris.util.FastNoise.CellularReturnType;
import ninja.bytecode.iris.util.FastNoise.NoiseType;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

public class GenLayerCave extends GenLayer
{
	public static final BlockData CAVE_AIR = BlockDataTools.getBlockData("CAVE_AIR");
	public static final BlockData AIR = BlockDataTools.getBlockData("AIR");
	private static final KList<CaveResult> EMPTY = new KList<>();
	private CNG gincline;
	private CNG shuffle;
	private FastNoise gg;

	public GenLayerCave(DimensionChunkGenerator iris, RNG rng)
	{
		//@builder
		super(iris, rng);
		shuffle = CNG.signature(rng.nextParallelRNG(1348566));
		gincline = new CNG(rng.nextParallelRNG(26512), 1D, 3).scale(0.00452);
		gg = new FastNoise(324895 * rng.nextParallelRNG(49678).imax());
		//@done
	}

	public KList<CaveResult> genCaves(double wxx, double wzz, int x, int z, AtomicSliver data)
	{
		if(!iris.getDimension().isCaves())
		{
			return EMPTY;
		}

		KList<CaveResult> result = new KList<>();
		shuffle.scale(0.01);
		double shuffleDistance = 72;
		gg.SetNoiseType(NoiseType.Cellular);
		gg.SetCellularReturnType(CellularReturnType.Distance2Sub);
		gg.SetCellularDistanceFunction(CellularDistanceFunction.Natural);

		for(int i = 0; i < 2; i++)
		{
			double wx = wxx + (shuffle.noise(wxx, wzz) * shuffleDistance);
			double wz = wzz + (shuffle.noise(wzz, wxx) * shuffleDistance);
			double incline = 157;
			double baseWidth = (9 * iris.getDimension().getCaveScale());
			double distanceCheck = 0.0132 * baseWidth;
			double distanceTake = 0.0032 * baseWidth;
			double drop = (-i * 7) + 44 + iris.getDimension().getCaveShift();
			double caveHeightNoise = incline * gincline.noise((wx + (10000 * i)), (wz - (10000 * i)));
			caveHeightNoise += shuffle.fitDoubleD(-1, 1, wxx - caveHeightNoise, wzz + caveHeightNoise) * 3;

			int ceiling = 0;
			int floor = 256;

			for(double tunnelHeight = 1; tunnelHeight <= baseWidth; tunnelHeight++)
			{
				double distance = (gg.GetCellular((float) wx + (10000 * i), (float) wz - (10000 * i)) + 1D) / 2D;
				if(distance < distanceCheck - (tunnelHeight * distanceTake))
				{
					int caveHeight = (int) Math.round(caveHeightNoise - drop);
					int pu = (int) (caveHeight + tunnelHeight);
					int pd = (int) (caveHeight - tunnelHeight);
					if(dig(x, pu, z, data))
					{
						ceiling = pu > ceiling ? pu : ceiling;
						floor = pu < floor ? pu : floor;
					}

					if(dig(x, pd, z, data))
					{
						ceiling = pd > ceiling ? pd : ceiling;
						floor = pd < floor ? pd : floor;
					}

					if(tunnelHeight == 1)
					{
						if(dig(x, (int) (caveHeight), z, data))
						{
							ceiling = caveHeight > ceiling ? caveHeight : ceiling;
							floor = caveHeight < floor ? caveHeight : floor;
						}
					}
				}
			}

			result.add(new CaveResult(floor, ceiling));
		}

		return result;
	}

	public boolean dig(int x, int y, int z, AtomicSliver data)
	{
		Material a = data.getType(y);
		Material c = data.getType(y + 1);
		Material f = data.getType(y - 1);

		if(can(a) && canAir(c) && canAir(f))
		{
			data.set(y, CAVE_AIR);
			return true;
		}

		return false;
	}

	public boolean canAir(Material m)
	{
		return (m.isSolid() || m.equals(Material.AIR) || m.equals(Material.CAVE_AIR)) && !m.equals(Material.BEDROCK);
	}

	public boolean can(Material m)
	{
		return m.isSolid() && !m.equals(Material.BEDROCK);
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
