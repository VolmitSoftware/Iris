package com.volmit.iris.scaffold.parallel;

import com.volmit.iris.util.KList;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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

	public BurstExecutor queue(KList<Runnable> r) {
		synchronized(futures)
		{
			for(Runnable i : r)
			{
				CompletableFuture<Void> c = CompletableFuture.runAsync(i, executor);
				futures.add(c);
			}
		}

		return this;
	}

	public BurstExecutor queue(Runnable[] r)
	{
		synchronized(futures)
		{
			for(Runnable i : r)
			{
				CompletableFuture<Void> c = CompletableFuture.runAsync(i, executor);
				futures.add(c);
			}
		}

		return this;
	}

	public void complete()
	{
		synchronized(futures)
		{
			if(futures.isEmpty())
			{
				return;
			}

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
