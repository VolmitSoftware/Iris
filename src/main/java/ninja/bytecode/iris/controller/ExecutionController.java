package ninja.bytecode.iris.controller;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.execution.TaskExecutor;

public class ExecutionController
{
	KMap<String, TaskExecutor> executors;

	public void start()
	{
		executors = new KMap<>();
	}

	public void stop()
	{
		for(TaskExecutor i : executors.v())
		{
			i.close();
		}

		executors.clear();
	}

	public TaskExecutor getExecutor(World world, String f)
	{
		String k = world.getWorldFolder().getAbsolutePath() + " (" + world + ") " + f;

		if(executors.containsKey(k))
		{
			return executors.get(k);
		}

		TaskExecutor x = new TaskExecutor(getTC(), Iris.settings.performance.threadPriority, "Iris " + f);
		executors.put(k, x);
		return x;
	}

	public int getTC()
	{
		switch(Iris.settings.performance.performanceMode)
		{
			case HALF_CPU:
				return Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
			case MATCH_CPU:
				return Runtime.getRuntime().availableProcessors();
			case SINGLE_THREADED:
				return 1;
			case DOUBLE_CPU:
				return Runtime.getRuntime().availableProcessors() * 2;
			case UNLIMITED:
				return -1;
			case EXPLICIT:
				return Iris.settings.performance.threadCount;
			default:
				break;
		}

		return Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
	}
}
