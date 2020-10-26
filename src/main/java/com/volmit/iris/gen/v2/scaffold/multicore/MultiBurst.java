package com.volmit.iris.gen.v2.scaffold.multicore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiBurst
{
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
