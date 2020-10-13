package com.volmit.iris.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

public class GroupedExecutor
{
	private int xc;
	private ExecutorService service;
	private KMap<String, Integer> mirror;

	public GroupedExecutor(int threadLimit, int priority, String name)
	{
		xc = 1;
		mirror = new KMap<String, Integer>();

		if(threadLimit == 1)
		{
			service = Executors.newSingleThreadExecutor((r) ->
			{
				Thread t = new Thread(r);
				t.setName(name);
				t.setPriority(priority);

				return t;
			});
		}

		else if(threadLimit > 1)
		{
			final ForkJoinWorkerThreadFactory factory = new ForkJoinWorkerThreadFactory()
			{
				@Override
				public ForkJoinWorkerThread newThread(ForkJoinPool pool)
				{
					final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
					worker.setName(name + " " + xc++);
					worker.setPriority(priority);
					return worker;
				}
			};

			service = new ForkJoinPool(threadLimit, factory, null, false);
		}

		else
		{
			service = Executors.newCachedThreadPool((r) ->
			{
				Thread t = new Thread(r);
				t.setName(name + " " + xc++);
				t.setPriority(priority);

				return t;
			});
		}
	}

	public void waitFor(String g)
	{
		if(g == null)
		{
			return;
		}

		if(!mirror.containsKey(g))
		{
			return;
		}

		while(true)
		{
			if(mirror.get(g) == 0)
			{
				break;
			}
		}
	}

	public void queue(String q, NastyRunnable r)
	{
		mirror.compute(q, (k, v) -> k == null || v == null ? 1 : v + 1);

		service.execute(() ->
		{
			try
			{
				r.run();
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}

			mirror.compute(q, (k, v) -> v - 1);
		});
	}

	public void close()
	{
		J.a(() ->
		{
			J.sleep(100);
			service.shutdown();
		});
	}

	public void closeNow()
	{
		service.shutdown();
	}
}
