package ninja.bytecode.iris.controller;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.TaskExecutor;

public class ExecutionController implements IrisController
{ 
	GMap<String, TaskExecutor> executors;
	
	@Override
	public void onStart()
	{
		executors = new GMap<>();
	}

	@Override
	public void onStop()
	{
		
	}
	
	public TaskExecutor getExecutor(World world)
	{
		TaskExecutor x = new TaskExecutor(getTC(), Iris.settings.performance.threadPriority, "Iris Generator (" + world.getName() + ")");
		executors.put(world.getWorldFolder().getAbsolutePath() + " (" + world + ")", x);
		return x;
	}
	
	private int getTC()
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
