package com.volmit.iris.v2.scaffold.parallel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import com.volmit.iris.util.KList;

public class BurstExecutor
{
	private ExecutorService executor;
	private KList<CompletableFuture<Void>> futures;

	public BurstExecutor(ExecutorService executor, int burstSizeEstimate)
	{
		this.executor = executor;
		futures = new KList<CompletableFuture<Void>>(burstSizeEstimate);
	}

	public CompletableFuture<Void> queue(Runnable r)
	{
		synchronized(futures)
		{
			CompletableFuture<Void> c = CompletableFuture.runAsync(r, executor);
			futures.add(c);
			return c;
		}
	}

	public void complete()
	{
		synchronized(futures)
		{
			try
			{
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get();
				futures.clear();
			}

			catch(InterruptedException | ExecutionException e)
			{
				e.printStackTrace();
			}
		}
	}
}
