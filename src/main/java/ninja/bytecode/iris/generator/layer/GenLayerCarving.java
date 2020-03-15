package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCarving extends GenLayer
{
	private CNG scram;
	private CNG cng;
	private CNG cngh;
	private CNG cngo;

	public GenLayerCarving(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		cng = new CNG(rng.nextParallelRNG(2339234), 1D, 1).scale(0.02);
		cngh = new CNG(rng.nextParallelRNG(1939234), 1D, 1).scale(0.027);
		cngo = new CNG(rng.nextParallelRNG(8939234), 1D, 1).scale(0.002);
		scram = new CNG(rng.nextParallelRNG(2639634), 1D, 1).scale(0.15);
		
		//@done
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return gnoise;
	}

	public boolean isCarved(double vwxxf, double vwzxf, int x, int z, double hl, AtomicChunkData data, ChunkPlan plan)
	{
		double a = cngh.noise(vwxxf, vwzxf);
		double hmax = 99 + (a * 30);
		double hmin = 68 + (a * 30);

		if(hl > hmax || hl < hmin)
		{
			return false;
		}
		double wxxf = (scram.noise(vwxxf, vwzxf) * 12) - vwzxf;
		double wzxf = (scram.noise(vwzxf, vwxxf) * 12) + vwxxf;

		double downrange = M.lerpInverse(hmin, hmax, hl);
		double opacity = IrisInterpolation.sinCenter(downrange);

		if(cng.noise(wxxf, wzxf, hl / 3) < (opacity / 1.4D) * cngo.noise(wxxf, wzxf))
		{
			return true;
		}

		return false;
	}
}
