package ninja.bytecode.iris.generator;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.api.nms.NMP;
import mortar.api.sched.J;
import mortar.compute.math.M;
import mortar.lang.collection.FinalDouble;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.util.ChronoQueue;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.SMCAVector;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.NastyRunnable;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.math.RNG;

public class WorldReactor
{
	private final World world;
	private IrisGenerator gen;

	public WorldReactor(World world)
	{
		this.world = world;

		if(!(world.getGenerator() instanceof IrisGenerator))
		{
			throw new IllegalArgumentException(world.getName() + " is not an iris world.");
		}

		this.gen = ((IrisGenerator) world.getGenerator());
	}

	public void generateMultipleRegions(GMap<SMCAVector, TaskExecutor> g, boolean force, double mst, Consumer<Double> progress, Runnable done)
	{
		ChronoLatch cl = new ChronoLatch(50);
		GMap<SMCAVector, Double> p = new GMap<>();
		ReentrantLock r = new ReentrantLock();
		for(SMCAVector i : g.k())
		{
			generateRegion(i.getX(), i.getZ(), g.get(i), force, mst / (double) g.size(), (xp) ->
			{
				r.lock();
				p.put(i, xp);
				double m = 0;

				for(double j : p.v())
				{
					m += j;
				}
				r.unlock();

				double h = m / (double) p.size();

				if(h == 1D || cl.flip())
				{
					progress.accept(h);
				}
			}, () ->
			{
				p.remove(i);

				if(p.isEmpty())
				{
					done.run();
				}
			});
		}
	}

	public void generateRegion(int mx, int mz, TaskExecutor executor, boolean force, double mst, Consumer<Double> progress, Runnable done)
	{
		J.a(() ->
		{
			FinalDouble of = new FinalDouble(0D);
			FinalDouble max = new FinalDouble(0D);
			PrecisionStopwatch t = PrecisionStopwatch.start();
			GMap<SMCAVector, AtomicChunkData> data = new GMap<>();
			GMap<SMCAVector, ChunkPlan> plan = new GMap<>();
			GList<NastyRunnable> parallax = new GList<>();
			GList<NastyRunnable> noise = new GList<>();
			GList<Runnable> chunk = new GList<>();
			ReentrantLock lock = new ReentrantLock();

			for(int xx = mx << 5; xx < (mx << 5) + 32; xx++)
			{
				int x = xx;

				for(int zz = mz << 5; zz < (mz << 5) + 32; zz++)
				{
					int z = zz;
					SMCAVector w = new SMCAVector(x, z);

					if(!force && world.loadChunk(x, z, false))
					{
						// continue;
					}

					max.add(1);
					parallax.add(() ->
					{
						gen.doGenParallax(x, z);
						of.add(1);
						progress.accept(of.get() / max.get());
					});

					max.add(0.1);
					noise.add(() ->
					{
						AtomicChunkData tydata = new AtomicChunkData(world);
						ChunkPlan typlan = new ChunkPlan();

						for(int i = 0; i < 16; i++)
						{
							for(int j = 0; j < 16; j++)
							{
								gen.onGenColumn((x << 4) + i, (z << 4) + j, i, j, typlan, tydata, true);
							}
						}

						gen.getWorldData().getChunk(x, z).inject(tydata);
						gen.onPostChunk(world, x, z, new RNG(world.getSeed()), tydata, typlan);

						lock.lock();
						data.put(w, tydata);
						plan.put(w, typlan);
						lock.unlock();
						of.add(1);
						progress.accept(of.get() / max.get());
					});

					max.add(4);
					chunk.add(() ->
					{
						if(world.loadChunk(x, z, false))
						{
							world.regenerateChunk(x, z);
						}

						else
						{
							world.loadChunk(x, z, true);
						}

						Chunk cc = world.getChunkAt(x, z);
						NMP.host.relight(cc);
						cc.unload(true);
					});
				}
			}

			executor.startWork().queue(parallax).execute();
			executor.startWork().queue(noise).execute();

			gen.setSplicer((world, random, x, z, biome) ->
			{
				SMCAVector w = new SMCAVector(x, z);

				for(int i = 0; i < 16; i++)
				{
					for(int j = 0; j < 16; j++)
					{
						try
						{
							biome.setBiome((x << 4) + i, (z << 4) + j, plan.get(w).getBiome(i, j).getRealBiome());
						}

						catch(Throwable e)
						{

						}
					}
				}

				AtomicChunkData f = data.get(w);

				if(f != null)
				{

				}

				return f;
			});

			t.end();
			J.s(() ->
			{
				PrecisionStopwatch s = PrecisionStopwatch.start();
				ChronoQueue q = new ChronoQueue(mst, 1096);
				int m = 0;
				for(Runnable i : chunk)
				{
					int gg = m;

					q.queue(() ->
					{
						i.run();
						of.add(4);
						if(gg == chunk.size() - 1)
						{
							progress.accept(1D);
							s.end();
							q.dieSlowly();
							done.run();
						}

						else
						{
							progress.accept(M.clip(of.get() / max.get(), 0D, 1D));
						}
					});

					m++;
				}
			});
		});
	}

	public void generateRegionNormal(Player p, boolean force, double mst, Consumer<Double> progress, Runnable done)
	{
		ChronoQueue q = new ChronoQueue(mst, 1024);
		FinalDouble of = new FinalDouble(0D);
		FinalDouble max = new FinalDouble(0D);

		for(int xx = p.getLocation().getChunk().getX() - 32; xx < p.getLocation().getChunk().getX() + 32; xx++)
		{
			int x = xx;

			for(int zz = p.getLocation().getChunk().getX() - 32; zz < p.getLocation().getChunk().getX() + 32; zz++)
			{
				int z = zz;

				max.add(1);

				q.queue(() ->
				{
					if(world.loadChunk(x, z, false))
					{
						world.regenerateChunk(x, z);
					}

					else
					{
						world.loadChunk(x, z, true);
					}

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
		}

		q.dieSlowly();
	}
}
