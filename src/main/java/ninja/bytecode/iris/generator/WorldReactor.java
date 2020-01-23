package ninja.bytecode.iris.generator;

import java.util.Collections;
import java.util.function.Consumer;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.api.nms.NMP;
import mortar.api.sched.J;
import mortar.compute.math.M;
import mortar.lang.collection.FinalDouble;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.ChronoQueue;
import ninja.bytecode.iris.util.ObjectMode;
import ninja.bytecode.iris.util.SMCAVector;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

public class WorldReactor
{
	private static KList<ChronoQueue> q = new KList<>();
	private final World world;

	public WorldReactor(World world)
	{
		this.world = world;
	}

	public void generateRegionNormal(Player p, boolean force, double mst, Consumer<Double> progress, Runnable done)
	{
		for(ChronoQueue i : WorldReactor.q)
		{
			i.close();
		}

		WorldReactor.q.clear();

		ChronoQueue q = new ChronoQueue(mst, 10240);
		WorldReactor.q.add(q);
		FinalDouble of = new FinalDouble(0D);
		FinalDouble max = new FinalDouble(0D);
		KMap<SMCAVector, Double> d = new KMap<>();
		int mx = p.getLocation().getChunk().getX();
		int mz = p.getLocation().getChunk().getZ();
		for(int xx = p.getLocation().getChunk().getX() - 32; xx < p.getLocation().getChunk().getX() + 32; xx++)
		{
			int x = xx;

			for(int zz = p.getLocation().getChunk().getZ() - 32; zz < p.getLocation().getChunk().getZ() + 32; zz++)
			{
				int z = zz;

				if(world.isChunkLoaded(x, z) || world.loadChunk(x, z, false))
				{
					d.put(new SMCAVector(x, z), Math.sqrt(Math.pow(x - mx, 2) + Math.pow(z - mz, 2)));
				}
			}
		}

		KList<SMCAVector> v = d.k();
		Collections.sort(v, (a, b) -> (int) (10000 * (d.get(a) - d.get(b))));

		for(SMCAVector i : v)
		{
			int x = i.getX();
			int z = i.getZ();

			if(Iris.settings.performance.objectMode.equals(ObjectMode.PARALLAX) && world.getGenerator() instanceof IrisGenerator)
			{
				IrisGenerator gg = ((IrisGenerator) world.getGenerator());
				gg.getWorldData().deleteChunk(x, z);
			}

			max.add(1);
			q.queue(() ->
			{
				world.regenerateChunk(x, z);

				Chunk cc = world.getChunkAt(x, z);
				NMP.host.relight(cc);
				of.add(1);

				if(of.get() == max.get())
				{
					progress.accept(1D);
					q.dieSlowly();
					done.run();
				}

				else
				{
					progress.accept(M.clip(of.get() / max.get(), 0D, 1D));
				}

			});
		}

		J.s(() ->
		{
			q.dieSlowly();
		}, 20);
	}
}
