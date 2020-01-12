package ninja.bytecode.iris.util;

import org.bukkit.Bukkit;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.Queue;
import ninja.bytecode.shuriken.execution.ShurikenQueue;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.logging.L;

public class ChronoQueue
{
	private PrecisionStopwatch s;
	private Queue<Runnable> q;
	private double limit;
	private int jobLimit;
	private ChronoLatch cl;

	public ChronoQueue(double limit, int jobLimit)
	{
		this.limit = limit;
		this.jobLimit = jobLimit;
		s = new PrecisionStopwatch();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::tick, 0, 0);
		q = new ShurikenQueue<>();
		cl = new ChronoLatch(1);
	}

	public void queue(Runnable r)
	{
		q.queue(r);
	}

	private void tick()
	{
		s.reset();
		s.begin();
		int m = 0;
		while(q.hasNext() && (s.getMilliseconds() < limit || q.size() > jobLimit))
		{
			m++;
			try
			{
				q.next().run();
			}

			catch(Throwable e)
			{
				L.ex(e);
			}
		}

		if(cl.flip())
		{
			System.out.println("Q: " + F.f(q.size()) + " T: " + F.duration(s.getMilliseconds(), 2) + " DID: " + F.f(m));
		}

		s.end();
	}
}
