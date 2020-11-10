package com.volmit.iris.scaffold.parallel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiBurst
{
	public static MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
	private ExecutorService service;

	public MultiBurst(int tc)
	{
		service = Executors.newWorkStealingPool(tc);
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
