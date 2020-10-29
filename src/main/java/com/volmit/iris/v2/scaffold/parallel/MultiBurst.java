package com.volmit.iris.v2.scaffold.parallel;

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

	public BurstExecutor burst(int estimate)
	{
		return new BurstExecutor(service, estimate);
	}

	public BurstExecutor burst()
	{
		return burst(16);
	}
}
