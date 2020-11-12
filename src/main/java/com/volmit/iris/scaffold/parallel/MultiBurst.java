package com.volmit.iris.scaffold.parallel;

import com.volmit.iris.Iris;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class MultiBurst
{
	public static MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
	private ExecutorService service;
	private int tid;

	public MultiBurst(int tc)
	{
		service = Executors.newFixedThreadPool(tc, new ThreadFactory()
		{
			@Override
			public Thread newThread(Runnable r)
			{
				tid++;
				Thread t = new Thread(r);
				t.setName("Iris Generator " + tid);
				t.setPriority(Thread.MAX_PRIORITY);
				t.setUncaughtExceptionHandler((et, e) ->
				{
					Iris.info("Exception encountered in " + et.getName());
					e.printStackTrace();
				});

				return t;
			}
		});
	}

	public void burst(Runnable... r)
	{
		burst(r.length).queue(r).complete();
	}

	public BurstExecutor burst(int estimate)
	{
		return new BurstExecutor(service, estimate);
	}

	public BurstExecutor burst()
	{
		return burst(16);
	}

	public void lazy(Runnable o) {
		service.execute(o);
	}
}
