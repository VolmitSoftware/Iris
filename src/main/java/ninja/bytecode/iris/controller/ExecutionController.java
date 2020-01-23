package ninja.bytecode.iris.controller;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.execution.TaskExecutor;

public class ExecutionController implements IrisController
{
	KMap<String, TaskExecutor> executors;

	@Override
	public void onStart()
	{
		executors = new KMap<>();
	}

	@Override
	public void onStop()
	{
		for(TaskExecutor i : executors.v())
		{
			i.close();
		}
	}

	public TaskExecutor getExecutor(World world, String f)
	{
		TaskExecutor x = new TaskExecutor(getTC(), Iris.settings.performance.threadPriority, "Iris " + f);
		executors.put(world.getWorldFolder().getAbsolutePath() + " (" + world + ") " + f, x);
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
